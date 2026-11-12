-- =======================================================================================
-- MIGRATION: V20261111__add_customer_ward_district_codes.sql
-- Target DBMS: Microsoft SQL Server 2022 (PawnMasterDB)
-- Author: PawnMaster Database Engineering
-- Purpose:
--   Complete 3-tier Vietnamese GSO administrative unit persistence on dbo.customers
--   (province_code, district_code, ward_code) with foreign keys to dbo.provinces,
--   dbo.districts, and dbo.wards, composite location index idx_customers_location,
--   canonical joined-address view, and optional batched backfill for permanent_address.
--
-- Migration Safety Notes (Expand-Contract / Zero-Downtime):
--   1. All added columns are NULLABLE VARCHAR(20) — metadata-only DDL in SQL Server
--      with zero table rewrite and no data-page locks.
--   2. DDL batches are separated via GO so SQL Server resolves newly added columns
--      cleanly before adding FOREIGN KEY constraints and non-clustered indexes.
--   3. Non-clustered index creation attempts ONLINE = ON first (Enterprise/Developer)
--      and falls back gracefully to standard index creation (Express/Standard/Web).
--   4. Optional DML backfill is isolated in its own batch using small TOP (1000) chunks
--      to avoid lock escalation.
-- =======================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- =======================================================================================
-- UP MIGRATION — STEP 1: EXPAND NULLABLE ADMINISTRATIVE COLUMNS ON dbo.customers
-- =======================================================================================
IF OBJECT_ID(N'dbo.customers', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.customers', 'province_code') IS NULL
    BEGIN
        ALTER TABLE dbo.customers ADD province_code VARCHAR(20) NULL;
    END

    IF COL_LENGTH('dbo.customers', 'district_code') IS NULL
    BEGIN
        ALTER TABLE dbo.customers ADD district_code VARCHAR(20) NULL;
    END

    IF COL_LENGTH('dbo.customers', 'ward_code') IS NULL
    BEGIN
        ALTER TABLE dbo.customers ADD ward_code VARCHAR(20) NULL;
    END
END
GO

-- =======================================================================================
-- UP MIGRATION — STEP 2: ADD FOREIGN KEY CONSTRAINTS & COMPOSITE LOCATION INDEX
-- =======================================================================================
IF OBJECT_ID(N'dbo.customers', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'dbo.provinces', N'U') IS NOT NULL
       AND OBJECT_ID(N'dbo.fk_customers_province', N'F') IS NULL
    BEGIN
        ALTER TABLE dbo.customers
        ADD CONSTRAINT fk_customers_province
            FOREIGN KEY (province_code) REFERENCES dbo.provinces(code);
    END

    IF OBJECT_ID(N'dbo.districts', N'U') IS NOT NULL
       AND OBJECT_ID(N'dbo.fk_customers_district', N'F') IS NULL
    BEGIN
        ALTER TABLE dbo.customers
        ADD CONSTRAINT fk_customers_district
            FOREIGN KEY (district_code) REFERENCES dbo.districts(code);
    END

    IF OBJECT_ID(N'dbo.wards', N'U') IS NOT NULL
       AND OBJECT_ID(N'dbo.fk_customers_ward', N'F') IS NULL
    BEGIN
        ALTER TABLE dbo.customers
        ADD CONSTRAINT fk_customers_ward
            FOREIGN KEY (ward_code) REFERENCES dbo.wards(code);
    END

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'idx_customers_location'
          AND object_id = OBJECT_ID(N'dbo.customers')
    )
    BEGIN
        BEGIN TRY
            EXEC sp_executesql N'CREATE NONCLUSTERED INDEX idx_customers_location ON dbo.customers(province_code, district_code, ward_code) WITH (ONLINE = ON);';
        END TRY
        BEGIN CATCH
            CREATE NONCLUSTERED INDEX idx_customers_location
                ON dbo.customers(province_code, district_code, ward_code);
        END CATCH
    END
END
GO

-- =======================================================================================
-- UP MIGRATION — STEP 3: HELPER VIEW FOR CANONICAL JOINED VIETNAMESE ADDRESS
-- Joins street detail (address) + ward.full_name + district.full_name + province.full_name
-- =======================================================================================
CREATE OR ALTER VIEW dbo.vw_customer_full_addresses
AS
SELECT
    c.id AS customer_id,
    c.customer_code,
    c.full_name,
    c.identity_number,
    c.province_code,
    COALESCE(NULLIF(p.full_name, N''), p.[name]) AS province_full_name,
    c.district_code,
    COALESCE(NULLIF(d.full_name, N''), d.[name]) AS district_full_name,
    c.ward_code,
    COALESCE(NULLIF(w.full_name, N''), w.[name]) AS ward_full_name,
    c.address,
    c.permanent_address,
    -- Canonical joined address expression:
    -- If c.address holds street/house detail only (e.g. N'88 Nguyễn Huệ'), joins cleanly
    -- with [w.full_name, d.full_name, p.full_name] -> N'88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, Thành phố Hồ Chí Minh'.
    -- If c.address already contains the ward/province suffix, avoids duplicating administrative names.
    CASE
        WHEN c.address IS NOT NULL
             AND w.full_name IS NOT NULL
             AND CHARINDEX(w.full_name, c.address) > 0
             AND p.full_name IS NOT NULL
             AND CHARINDEX(p.full_name, c.address) > 0
            THEN c.address
        ELSE CONCAT_WS(
            N', ',
            NULLIF(LTRIM(RTRIM(
                CASE
                    WHEN CHARINDEX(N',', ISNULL(c.address, N'')) > 0
                         AND (
                             (w.full_name IS NOT NULL AND CHARINDEX(w.full_name, c.address) > 0)
                             OR (d.full_name IS NOT NULL AND CHARINDEX(d.full_name, c.address) > 0)
                         )
                        THEN LEFT(c.address, CHARINDEX(N',', c.address) - 1)
                    ELSE c.address
                END
            )), N''),
            COALESCE(NULLIF(w.full_name, N''), w.[name]),
            COALESCE(NULLIF(d.full_name, N''), d.[name]),
            COALESCE(NULLIF(p.full_name, N''), p.[name])
        )
    END AS composed_permanent_address
FROM dbo.customers c
LEFT JOIN dbo.wards w     ON c.ward_code = w.code
LEFT JOIN dbo.districts d ON c.district_code = d.code
LEFT JOIN dbo.provinces p ON c.province_code = p.code;
GO

-- =======================================================================================
-- UP MIGRATION — STEP 4: OPTIONAL SAFE BATCHED BACKFILL FOR permanent_address
-- Populates/standardizes permanent_address from [street detail + w.full_name + d.full_name + p.full_name]
-- for rows that already have valid administrative codes and missing/unstandardized permanent_address.
-- =======================================================================================
DECLARE @batchSize INT = 1000;
DECLARE @rowsAffected INT = 1;

WHILE @rowsAffected > 0
BEGIN
    ;WITH NextBatch AS (
        SELECT TOP (@batchSize)
            c.id,
            v.composed_permanent_address
        FROM dbo.customers c
        INNER JOIN dbo.vw_customer_full_addresses v ON v.customer_id = c.id
        WHERE c.province_code IS NOT NULL
          AND c.district_code IS NOT NULL
          AND c.ward_code IS NOT NULL
          AND v.composed_permanent_address IS NOT NULL
          AND LEN(v.composed_permanent_address) <= 255
          AND (
              c.permanent_address IS NULL
              OR LTRIM(RTRIM(c.permanent_address)) = N''
              OR (v.ward_full_name IS NOT NULL AND CHARINDEX(v.ward_full_name, c.permanent_address) = 0)
          )
        ORDER BY c.id
    )
    UPDATE c
    SET c.permanent_address = nb.composed_permanent_address,
        c.updated_at = SYSDATETIME()
    FROM dbo.customers c
    INNER JOIN NextBatch nb ON nb.id = c.id;

    SET @rowsAffected = @@ROWCOUNT;
END
GO

-- =======================================================================================
-- DOWN / ROLLBACK PLAN (COMMENTED — RUN ONLY AFTER APPLICATION STOPS REFERENCING ward_code)
-- =======================================================================================
/*
IF OBJECT_ID(N'dbo.vw_customer_full_addresses', N'V') IS NOT NULL
    DROP VIEW dbo.vw_customer_full_addresses;
GO

IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'idx_customers_location'
      AND object_id = OBJECT_ID(N'dbo.customers')
)
    DROP INDEX idx_customers_location ON dbo.customers;
GO

IF OBJECT_ID(N'dbo.fk_customers_ward', N'F') IS NOT NULL
    ALTER TABLE dbo.customers DROP CONSTRAINT fk_customers_ward;
GO

IF COL_LENGTH('dbo.customers', 'ward_code') IS NOT NULL
    ALTER TABLE dbo.customers DROP COLUMN ward_code;
GO
*/
