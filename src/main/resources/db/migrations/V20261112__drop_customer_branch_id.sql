-- =======================================================================================
-- MIGRATION: V20261112__drop_customer_branch_id.sql
-- Target DBMS: Microsoft SQL Server 2022 (PawnMasterDB)
-- Author: PawnMaster Database Engineering
-- Purpose:
--   1. Make dbo.customers 100% branch-independent across the entire chain by dropping
--      dbo.customers.branch_id (along with idx_customers_branch, fk_customers_branch,
--      and any dynamically generated FK/default/index constraints).
--   2. Add nullable dbo.interest_payments.collection_branch_id (FK -> dbo.branches(id))
--      and idx_payments_collection_branch to track which branch counter collected
--      periodic cross-branch interest payments (while contract redemption remains
--      strictly enforced at collateral.branch_id).
--
-- Migration Safety Notes:
--   - Deployed AFTER application code removes references to dbo.customers.branch_id
--     (Contract phase of Expand-Contract).
--   - All DDL batches are separated by GO and fully idempotent.
-- =======================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- =======================================================================================
-- UP MIGRATION — STEP 1: DROP idx_customers_branch, fk_customers_branch & dbo.customers.branch_id
-- =======================================================================================
IF OBJECT_ID(N'dbo.customers', N'U') IS NOT NULL
BEGIN
    IF EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'idx_customers_branch'
          AND object_id = OBJECT_ID(N'dbo.customers')
    )
    BEGIN
        EXEC sp_executesql N'DROP INDEX idx_customers_branch ON dbo.customers;';
    END

    IF OBJECT_ID(N'dbo.fk_customers_branch', N'F') IS NOT NULL
    BEGIN
        EXEC sp_executesql N'ALTER TABLE dbo.customers DROP CONSTRAINT fk_customers_branch;';
    END

    IF COL_LENGTH('dbo.customers', 'branch_id') IS NOT NULL
    BEGIN
        DECLARE @dropDepsSql NVARCHAR(MAX) = N'';

        -- Drop any foreign key constraint referencing dbo.customers.branch_id (including Hibernate-generated FKs)
        SELECT @dropDepsSql += N'ALTER TABLE dbo.customers DROP CONSTRAINT ' + QUOTENAME(fk.name) + N';' + CHAR(13)
        FROM sys.foreign_keys fk
        INNER JOIN sys.foreign_key_columns fkc
            ON fk.object_id = fkc.constraint_object_id
        INNER JOIN sys.columns c
            ON fkc.parent_object_id = c.object_id
           AND fkc.parent_column_id = c.column_id
        WHERE fk.parent_object_id = OBJECT_ID(N'dbo.customers')
          AND c.name = 'branch_id';

        -- Drop any default constraint attached to dbo.customers.branch_id
        SELECT @dropDepsSql += N'ALTER TABLE dbo.customers DROP CONSTRAINT ' + QUOTENAME(dc.name) + N';' + CHAR(13)
        FROM sys.default_constraints dc
        INNER JOIN sys.columns c
            ON dc.parent_object_id = c.object_id
           AND dc.parent_column_id = c.column_id
        WHERE dc.parent_object_id = OBJECT_ID(N'dbo.customers')
          AND c.name = 'branch_id';

        -- Drop any remaining non-PK/non-unique-constraint index referencing dbo.customers.branch_id
        SELECT @dropDepsSql += N'DROP INDEX ' + QUOTENAME(i.name) + N' ON dbo.customers;' + CHAR(13)
        FROM sys.indexes i
        INNER JOIN sys.index_columns ic
            ON i.object_id = ic.object_id
           AND i.index_id = ic.index_id
        INNER JOIN sys.columns c
            ON ic.object_id = c.object_id
           AND ic.column_id = c.column_id
        WHERE i.object_id = OBJECT_ID(N'dbo.customers')
          AND i.is_primary_key = 0
          AND i.is_unique_constraint = 0
          AND c.name = 'branch_id';

        IF LEN(@dropDepsSql) > 0
        BEGIN
            EXEC sp_executesql @dropDepsSql;
        END

        EXEC sp_executesql N'ALTER TABLE dbo.customers DROP COLUMN branch_id;';
    END
END
GO

-- =======================================================================================
-- UP MIGRATION — STEP 2: EXPAND dbo.interest_payments WITH collection_branch_id
-- =======================================================================================
IF OBJECT_ID(N'dbo.interest_payments', N'U') IS NOT NULL
BEGIN
    IF COL_LENGTH('dbo.interest_payments', 'collection_branch_id') IS NULL
    BEGIN
        ALTER TABLE dbo.interest_payments ADD collection_branch_id BIGINT NULL;
    END
END
GO

-- =======================================================================================
-- UP MIGRATION — STEP 3: ADD fk_payments_collection_branch & idx_payments_collection_branch
-- =======================================================================================
IF OBJECT_ID(N'dbo.interest_payments', N'U') IS NOT NULL
BEGIN
    IF OBJECT_ID(N'dbo.branches', N'U') IS NOT NULL
       AND OBJECT_ID(N'dbo.fk_payments_collection_branch', N'F') IS NULL
    BEGIN
        ALTER TABLE dbo.interest_payments
        ADD CONSTRAINT fk_payments_collection_branch
            FOREIGN KEY (collection_branch_id) REFERENCES dbo.branches(id);
    END

    IF NOT EXISTS (
        SELECT 1
        FROM sys.indexes
        WHERE name = 'idx_payments_collection_branch'
          AND object_id = OBJECT_ID(N'dbo.interest_payments')
    )
    BEGIN
        BEGIN TRY
            EXEC sp_executesql N'CREATE NONCLUSTERED INDEX idx_payments_collection_branch ON dbo.interest_payments(collection_branch_id) WITH (ONLINE = ON);';
        END TRY
        BEGIN CATCH
            CREATE NONCLUSTERED INDEX idx_payments_collection_branch
                ON dbo.interest_payments(collection_branch_id);
        END CATCH
    END
END
GO

-- =======================================================================================
-- UP MIGRATION — STEP 4: OPTIONAL SAFE BATCHED BACKFILL FOR HISTORICAL PAYMENTS
-- Defaults collection_branch_id to pawn_contracts.branch_id for historical rows where NULL
-- =======================================================================================
DECLARE @batchSize INT = 1000;
DECLARE @rowsUpdated INT = 1;

WHILE @rowsUpdated > 0
BEGIN
    ;WITH HistoricalBatch AS (
        SELECT TOP (@batchSize)
            ip.id,
            pc.branch_id AS fallback_branch_id
        FROM dbo.interest_payments ip
        INNER JOIN dbo.pawn_contracts pc ON pc.id = ip.contract_id
        WHERE ip.collection_branch_id IS NULL
          AND pc.branch_id IS NOT NULL
        ORDER BY ip.id
    )
    UPDATE ip
    SET ip.collection_branch_id = hb.fallback_branch_id,
        ip.updated_at = SYSDATETIME()
    FROM dbo.interest_payments ip
    INNER JOIN HistoricalBatch hb ON hb.id = ip.id;

    SET @rowsUpdated = @@ROWCOUNT;
END
GO

-- =======================================================================================
-- DOWN / ROLLBACK PLAN (COMMENTED — FORWARD-SAFE REFERENCE)
-- =======================================================================================
/*
IF EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'idx_payments_collection_branch'
      AND object_id = OBJECT_ID(N'dbo.interest_payments')
)
    DROP INDEX idx_payments_collection_branch ON dbo.interest_payments;
GO

IF OBJECT_ID(N'dbo.fk_payments_collection_branch', N'F') IS NOT NULL
    ALTER TABLE dbo.interest_payments DROP CONSTRAINT fk_payments_collection_branch;
GO

IF COL_LENGTH('dbo.interest_payments', 'collection_branch_id') IS NOT NULL
    ALTER TABLE dbo.interest_payments DROP COLUMN collection_branch_id;
GO

IF COL_LENGTH('dbo.customers', 'branch_id') IS NULL
    ALTER TABLE dbo.customers ADD branch_id BIGINT NULL;
GO

IF OBJECT_ID(N'dbo.fk_customers_branch', N'F') IS NULL
    ALTER TABLE dbo.customers
    ADD CONSTRAINT fk_customers_branch
        FOREIGN KEY (branch_id) REFERENCES dbo.branches(id);
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'idx_customers_branch'
      AND object_id = OBJECT_ID(N'dbo.customers')
)
    CREATE NONCLUSTERED INDEX idx_customers_branch ON dbo.customers(branch_id);
GO
*/
