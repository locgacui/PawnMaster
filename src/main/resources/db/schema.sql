-- =======================================================================================
-- PAWNMASTER DATABASE SCHEMA — CONSOLIDATED DDL (14 TABLES, INDEXES & FUNCTIONS)
-- Database: PawnMasterDB (Microsoft SQL Server 2022)
-- File 1/2: schema.sql (Chuyên trách tạo Bảng, Ràng buộc, Index tối ưu và Hàm hệ thống)
-- File 2/2: data.sql   (Chuyên trách import toàn bộ Dữ liệu Hành chính, Cấu hình & Nghiệp vụ)
-- =======================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'PawnMasterDB')
BEGIN
    CREATE DATABASE [PawnMasterDB];
END
GO

USE [PawnMasterDB];
GO

-- =======================================================================================
-- PART I: VIETNAMESE ADMINISTRATIVE UNITS (Tỉnh/TP, Quận/Huyện, Phường/Xã - Chuẩn GSO)
-- =======================================================================================

-- 1. Table: administrative_regions (8 Vùng địa lý kinh tế - hành chính)
IF OBJECT_ID(N'dbo.administrative_regions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.administrative_regions (
        id INT NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NOT NULL,
        code_name VARCHAR(255) NULL,
        code_name_en VARCHAR(255) NULL,
        CONSTRAINT administrative_regions_pkey PRIMARY KEY (id)
    );
END
GO

-- 2. Table: administrative_units (10 Cấp/Loại đơn vị hành chính)
IF OBJECT_ID(N'dbo.administrative_units', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.administrative_units (
        id INT NOT NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        short_name NVARCHAR(255) NULL,
        short_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        code_name_en VARCHAR(255) NULL,
        CONSTRAINT administrative_units_pkey PRIMARY KEY (id)
    );
END
GO

-- 3. Table: provinces (63 Tỉnh / Thành phố trực thuộc Trung ương)
IF OBJECT_ID(N'dbo.provinces', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.provinces (
        code VARCHAR(20) NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NOT NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        administrative_unit_id INT NULL,
        administrative_region_id INT NULL,
        CONSTRAINT provinces_pkey PRIMARY KEY (code),
        CONSTRAINT provinces_administrative_region_id_fkey FOREIGN KEY (administrative_region_id) REFERENCES dbo.administrative_regions(id),
        CONSTRAINT provinces_administrative_unit_id_fkey FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id)
    );

    CREATE INDEX idx_provinces_region ON dbo.provinces(administrative_region_id);
    CREATE INDEX idx_provinces_unit ON dbo.provinces(administrative_unit_id);
END
GO

-- 4. Table: districts (700 Quận / Huyện / Thị xã / Thành phố thuộc Tỉnh)
IF OBJECT_ID(N'dbo.districts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.districts (
        code VARCHAR(20) NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        province_code VARCHAR(20) NULL,
        administrative_unit_id INT NULL,
        CONSTRAINT districts_pkey PRIMARY KEY (code),
        CONSTRAINT districts_administrative_unit_id_fkey FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id),
        CONSTRAINT districts_province_code_fkey FOREIGN KEY (province_code) REFERENCES dbo.provinces(code)
    );

    CREATE INDEX idx_districts_province ON dbo.districts(province_code);
    CREATE INDEX idx_districts_unit ON dbo.districts(administrative_unit_id);
END
GO

-- 5. Table: wards (10.597 Phường / Xã / Thị trấn)
IF OBJECT_ID(N'dbo.wards', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.wards (
        code VARCHAR(20) NOT NULL,
        [name] NVARCHAR(255) NOT NULL,
        name_en NVARCHAR(255) NULL,
        full_name NVARCHAR(255) NULL,
        full_name_en NVARCHAR(255) NULL,
        code_name VARCHAR(255) NULL,
        district_code VARCHAR(20) NULL,
        administrative_unit_id INT NULL,
        CONSTRAINT wards_pkey PRIMARY KEY (code),
        CONSTRAINT wards_administrative_unit_id_fkey FOREIGN KEY (administrative_unit_id) REFERENCES dbo.administrative_units(id),
        CONSTRAINT wards_district_code_fkey FOREIGN KEY (district_code) REFERENCES dbo.districts(code)
    );

    CREATE INDEX idx_wards_district ON dbo.wards(district_code);
    CREATE INDEX idx_wards_unit ON dbo.wards(administrative_unit_id);
END
GO

-- =======================================================================================
-- PART II: PAWNMASTER CORE DOMAIN TABLES (7 Bảng Nghiệp vụ Cốt lõi)
-- =======================================================================================

-- 6. Table: branches (Chi nhánh / Phòng Giao dịch Cầm đồ)
IF OBJECT_ID(N'dbo.branches', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.branches (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        branch_code VARCHAR(50) NOT NULL,
        branch_name NVARCHAR(150) NOT NULL,
        province_code VARCHAR(20) NOT NULL,
        district_code VARCHAR(20) NOT NULL,
        ward_code VARCHAR(20) NULL,
        address_detail NVARCHAR(255) NULL,
        phone_number VARCHAR(20) NULL,
        capital_limit DECIMAL(18, 2) NOT NULL DEFAULT 2500000000.00,
        current_cash_balance DECIMAL(18, 2) NOT NULL DEFAULT 500000000.00,
        is_active BIT NOT NULL DEFAULT 1,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_branches_code UNIQUE (branch_code),
        CONSTRAINT fk_branches_province FOREIGN KEY (province_code) REFERENCES dbo.provinces(code),
        CONSTRAINT fk_branches_district FOREIGN KEY (district_code) REFERENCES dbo.districts(code),
        CONSTRAINT fk_branches_ward FOREIGN KEY (ward_code) REFERENCES dbo.wards(code)
    );

    CREATE UNIQUE INDEX idx_branches_code ON dbo.branches(branch_code);
    CREATE INDEX idx_branches_province_district ON dbo.branches(province_code, district_code);
END
ELSE IF COL_LENGTH('dbo.branches', 'ward_code') IS NULL
BEGIN
    ALTER TABLE dbo.branches ADD
        ward_code VARCHAR(20) NULL CONSTRAINT fk_branches_ward FOREIGN KEY REFERENCES dbo.wards(code);
END
GO

-- 7. Table: users (Nhân viên & Phân quyền Quản trị RBAC)
IF OBJECT_ID(N'dbo.users', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        username VARCHAR(50) NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        full_name NVARCHAR(150) NOT NULL,
        phone_number VARCHAR(20) NULL,
        email VARCHAR(120) NULL,
        role VARCHAR(30) NOT NULL DEFAULT 'STAFF',
        branch_id BIGINT NULL,
        is_active BIT NOT NULL DEFAULT 1,
        last_login_at DATETIME2 NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_users_username UNIQUE (username),
        CONSTRAINT fk_users_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(id)
    );

    CREATE UNIQUE INDEX idx_users_username ON dbo.users(username);
    CREATE INDEX idx_users_phone ON dbo.users(phone_number);
    CREATE INDEX idx_users_branch ON dbo.users(branch_id);
END
ELSE IF COL_LENGTH('dbo.users', 'branch_id') IS NULL
BEGIN
    ALTER TABLE dbo.users ADD branch_id BIGINT NULL CONSTRAINT fk_users_branch FOREIGN KEY REFERENCES dbo.branches(id);
    CREATE INDEX idx_users_branch ON dbo.users(branch_id);
END
GO

-- 8. Table: customers (Khách hàng Toàn chuỗi, Nhóm nghề nghiệp 1-6 & Chứng từ KYC)
IF OBJECT_ID(N'dbo.customers', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.customers (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        customer_code VARCHAR(50) NOT NULL,
        branch_id BIGINT NULL,
        full_name NVARCHAR(150) NOT NULL,
        identity_number VARCHAR(25) NOT NULL,
        identity_issue_date DATE NULL,
        identity_issue_place NVARCHAR(150) NULL,
        phone_number VARCHAR(20) NOT NULL,
        email VARCHAR(120) NULL,
        date_of_birth DATE NULL,
        gender NVARCHAR(20) NULL,
        province_code VARCHAR(20) NULL,
        district_code VARCHAR(20) NULL,
        address NVARCHAR(255) NULL,
        permanent_address NVARCHAR(255) NULL,
        occupation_group VARCHAR(40) NOT NULL DEFAULT 'FREELANCER',
        monthly_income DECIMAL(18, 2) NULL,
        has_social_insurance BIT NOT NULL DEFAULT 0,
        verification_doc_type VARCHAR(50) NULL,
        verification_doc_urls NVARCHAR(1000) NULL,
        id_card_front_url VARCHAR(500) NULL,
        id_card_back_url VARCHAR(500) NULL,
        portrait_url VARCHAR(500) NULL,
        status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
        notes NVARCHAR(500) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_customers_code UNIQUE (customer_code),
        CONSTRAINT uq_customers_identity UNIQUE (identity_number),
        CONSTRAINT fk_customers_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(id),
        CONSTRAINT fk_customers_province FOREIGN KEY (province_code) REFERENCES dbo.provinces(code),
        CONSTRAINT fk_customers_district FOREIGN KEY (district_code) REFERENCES dbo.districts(code)
    );

    CREATE UNIQUE INDEX idx_customers_code ON dbo.customers(customer_code);
    CREATE UNIQUE INDEX idx_customers_identity ON dbo.customers(identity_number);
    CREATE INDEX idx_customers_branch ON dbo.customers(branch_id);
    CREATE INDEX idx_customers_phone ON dbo.customers(phone_number);
    CREATE INDEX idx_customers_occupation ON dbo.customers(occupation_group);
END
GO

-- 9. Table: collaterals (Tài sản Cầm cố & Lưu kho theo Chi nhánh)
IF OBJECT_ID(N'dbo.collaterals', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.collaterals (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        collateral_code VARCHAR(50) NOT NULL,
        customer_id BIGINT NOT NULL,
        branch_id BIGINT NULL,
        name NVARCHAR(200) NOT NULL,
        category VARCHAR(50) NOT NULL,
        brand NVARCHAR(100) NULL,
        model NVARCHAR(100) NULL,
        serial_or_frame_number VARCHAR(100) NULL,
        license_plate VARCHAR(30) NULL,
        condition_description NVARCHAR(500) NULL,
        appraised_value DECIMAL(18, 2) NOT NULL,
        max_ltv_percent DECIMAL(5, 2) NULL,
        storage_location NVARCHAR(150) NULL,
        image_urls NVARCHAR(1000) NULL,
        status VARCHAR(30) NOT NULL DEFAULT 'IN_VAULT',
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_collaterals_code UNIQUE (collateral_code),
        CONSTRAINT fk_collaterals_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id),
        CONSTRAINT fk_collaterals_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(id)
    );

    CREATE UNIQUE INDEX idx_collaterals_code ON dbo.collaterals(collateral_code);
    CREATE INDEX idx_collaterals_customer ON dbo.collaterals(customer_id);
    CREATE INDEX idx_collaterals_branch ON dbo.collaterals(branch_id);
    CREATE INDEX idx_collaterals_status ON dbo.collaterals(status);
    CREATE INDEX idx_collaterals_serial ON dbo.collaterals(serial_or_frame_number);
END
GO

-- 10. Table: pawn_contracts (Hợp đồng Cầm đồ & Phê duyệt 2 bước tại Chi nhánh)
IF OBJECT_ID(N'dbo.pawn_contracts', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.pawn_contracts (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        contract_code VARCHAR(50) NOT NULL,
        branch_id BIGINT NULL,
        customer_id BIGINT NOT NULL,
        collateral_id BIGINT NOT NULL,
        principal_amount DECIMAL(18, 2) NOT NULL,
        remaining_principal DECIMAL(18, 2) NOT NULL,
        applied_ltv_percent DECIMAL(5, 2) NULL,
        interest_type VARCHAR(30) NOT NULL DEFAULT 'DAILY_PER_MILLION',
        interest_rate DECIMAL(12, 4) NOT NULL,
        storage_fee_rate DECIMAL(12, 4) NULL DEFAULT 0,
        loan_term_days INT NOT NULL DEFAULT 30,
        payment_cycle_days INT NOT NULL DEFAULT 10,
        start_date DATE NOT NULL,
        maturity_date DATE NOT NULL,
        next_interest_due_date DATE NOT NULL,
        closed_date DATE NULL,
        total_interest_paid DECIMAL(18, 2) NOT NULL DEFAULT 0,
        status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
        approved_by BIGINT NULL,
        approved_at DATETIME2 NULL,
        approval_notes NVARCHAR(500) NULL,
        notes NVARCHAR(500) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_contracts_code UNIQUE (contract_code),
        CONSTRAINT uq_contracts_collateral UNIQUE (collateral_id),
        CONSTRAINT fk_contracts_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(id),
        CONSTRAINT fk_contracts_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id),
        CONSTRAINT fk_contracts_collateral FOREIGN KEY (collateral_id) REFERENCES dbo.collaterals(id)
    );

    CREATE UNIQUE INDEX idx_contracts_code ON dbo.pawn_contracts(contract_code);
    CREATE INDEX idx_contracts_branch ON dbo.pawn_contracts(branch_id);
    CREATE INDEX idx_contracts_customer ON dbo.pawn_contracts(customer_id);
    CREATE INDEX idx_contracts_status ON dbo.pawn_contracts(status);
    CREATE INDEX idx_contracts_due_date ON dbo.pawn_contracts(next_interest_due_date, maturity_date);
END
GO

-- 11. Table: interest_payments (Lịch sử Đóng lãi, Trả bớt gốc & Tất toán)
IF OBJECT_ID(N'dbo.interest_payments', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.interest_payments (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        payment_code VARCHAR(50) NOT NULL,
        contract_id BIGINT NOT NULL,
        payment_type VARCHAR(30) NOT NULL,
        from_date DATE NULL,
        to_date DATE NULL,
        days_count INT NULL,
        interest_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
        storage_fee_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
        penalty_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
        principal_paid_amount DECIMAL(18, 2) NOT NULL DEFAULT 0,
        total_amount DECIMAL(18, 2) NOT NULL,
        payment_date DATETIME2 NOT NULL,
        payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH',
        notes NVARCHAR(500) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_payments_code UNIQUE (payment_code),
        CONSTRAINT fk_payments_contract FOREIGN KEY (contract_id) REFERENCES dbo.pawn_contracts(id)
    );

    CREATE UNIQUE INDEX idx_payments_code ON dbo.interest_payments(payment_code);
    CREATE INDEX idx_payments_contract ON dbo.interest_payments(contract_id);
    CREATE INDEX idx_payments_date ON dbo.interest_payments(payment_date);
END
GO

-- 12. Table: cashbook_transactions (Sổ quỹ Thu/Chi theo Chi nhánh)
IF OBJECT_ID(N'dbo.cashbook_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.cashbook_transactions (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        transaction_code VARCHAR(50) NOT NULL,
        branch_id BIGINT NULL,
        transaction_type VARCHAR(20) NOT NULL,
        category VARCHAR(40) NOT NULL,
        amount DECIMAL(18, 2) NOT NULL,
        balance_after DECIMAL(18, 2) NULL,
        payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH',
        transaction_date DATETIME2 NOT NULL,
        contract_id BIGINT NULL,
        customer_id BIGINT NULL,
        payer_or_receiver_name NVARCHAR(150) NULL,
        description NVARCHAR(500) NULL,
        notes NVARCHAR(500) NULL,
        performed_by BIGINT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_cashbook_code UNIQUE (transaction_code),
        CONSTRAINT fk_cashbook_branch FOREIGN KEY (branch_id) REFERENCES dbo.branches(id),
        CONSTRAINT fk_cashbook_contract FOREIGN KEY (contract_id) REFERENCES dbo.pawn_contracts(id),
        CONSTRAINT fk_cashbook_customer FOREIGN KEY (customer_id) REFERENCES dbo.customers(id)
    );

    CREATE UNIQUE INDEX idx_cashbook_code ON dbo.cashbook_transactions(transaction_code);
    CREATE INDEX idx_cashbook_branch ON dbo.cashbook_transactions(branch_id);
    CREATE INDEX idx_cashbook_type_date ON dbo.cashbook_transactions(transaction_type, transaction_date);
    CREATE INDEX idx_cashbook_contract ON dbo.cashbook_transactions(contract_id);
END
GO

-- =======================================================================================
-- PART III: SYSTEM POLICY & CODE GOVERNANCE TABLES (ltv_policies & business_code_rules)
-- =======================================================================================

-- 13. Table: ltv_policies (Cấu hình Ma trận LTV tối đa theo Nhóm nghề nghiệp & Loại tài sản)
IF OBJECT_ID(N'dbo.ltv_policies', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.ltv_policies (
        id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        occupation_group VARCHAR(50) NOT NULL,
        collateral_category VARCHAR(50) NOT NULL,
        max_ltv_percent DECIMAL(5, 2) NOT NULL,
        description NVARCHAR(255) NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        created_by VARCHAR(100) NULL DEFAULT 'SYSTEM',
        updated_by VARCHAR(100) NULL,
        is_deleted BIT NOT NULL DEFAULT 0,
        CONSTRAINT uq_ltv_policies_group_category UNIQUE (occupation_group, collateral_category),
        CONSTRAINT chk_ltv_policies_percent CHECK (max_ltv_percent > 0 AND max_ltv_percent <= 100)
    );

    CREATE UNIQUE INDEX idx_ltv_policies_group_cat ON dbo.ltv_policies(occupation_group, collateral_category);
END
GO

-- 14. Table: business_code_rules (Bảng Lưu Quy tắc Mã Định danh Nghiệp vụ & Chiến lược Index)
IF OBJECT_ID(N'dbo.business_code_rules', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.business_code_rules (
        id INT IDENTITY(1,1) NOT NULL PRIMARY KEY,
        table_name VARCHAR(60) NOT NULL,
        column_name VARCHAR(60) NOT NULL,
        prefix VARCHAR(15) NOT NULL,
        format_pattern VARCHAR(120) NOT NULL,
        regex_validation VARCHAR(150) NOT NULL,
        example_code VARCHAR(50) NOT NULL,
        is_branch_scoped BIT NOT NULL DEFAULT 0,
        checksum_algorithm VARCHAR(40) NULL,
        index_name VARCHAR(80) NOT NULL,
        index_strategy NVARCHAR(300) NOT NULL,
        description NVARCHAR(500) NOT NULL,
        created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
        updated_at DATETIME2 NULL,
        CONSTRAINT uq_business_code_rules_table_col UNIQUE (table_name, column_name)
    );
END
GO

-- =======================================================================================
-- PART IV: HIGH-PERFORMANCE COVERING INDEXES & SYSTEM FUNCTIONS
-- =======================================================================================

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_branches_code_cov' AND object_id = OBJECT_ID('dbo.branches'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_branches_code_cov
    ON dbo.branches(branch_code)
    INCLUDE (branch_name, province_code, district_code, current_cash_balance, is_active)
    WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_customers_code_cov' AND object_id = OBJECT_ID('dbo.customers'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_customers_code_cov
    ON dbo.customers(customer_code)
    INCLUDE (full_name, identity_number, phone_number, occupation_group, status)
    WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_collaterals_code_cov' AND object_id = OBJECT_ID('dbo.collaterals'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_collaterals_code_cov
    ON dbo.collaterals(collateral_code)
    INCLUDE (customer_id, branch_id, category, appraised_value, status)
    WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_contracts_code_cov' AND object_id = OBJECT_ID('dbo.pawn_contracts'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_contracts_code_cov
    ON dbo.pawn_contracts(contract_code)
    INCLUDE (branch_id, customer_id, collateral_id, principal_amount, remaining_principal, status, maturity_date)
    WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_payments_code_cov' AND object_id = OBJECT_ID('dbo.interest_payments'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_payments_code_cov
    ON dbo.interest_payments(payment_code)
    INCLUDE (contract_id, payment_type, total_amount, payment_date)
    WHERE is_deleted = 0;
END

IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_cashbook_code_cov' AND object_id = OBJECT_ID('dbo.cashbook_transactions'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_cashbook_code_cov
    ON dbo.cashbook_transactions(transaction_code)
    INCLUDE (branch_id, transaction_type, category, amount, balance_after, transaction_date)
    WHERE is_deleted = 0;
END
GO

-- Hàm tính ký tự Checksum Luhn Mod-36 cho customer_code (đồng bộ 100% với BusinessCodeGenerator.java)
CREATE OR ALTER FUNCTION dbo.fn_CalcLuhnMod36(@input VARCHAR(64))
RETURNS CHAR(1)
WITH SCHEMABINDING
AS
BEGIN
    DECLARE @alphabet VARCHAR(36) = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ';
    DECLARE @clean VARCHAR(64) = UPPER(ISNULL(@input, ''));
    DECLARE @i INT = LEN(@clean);
    DECLARE @factor INT = 2;
    DECLARE @sum INT = 0;
    DECLARE @ch CHAR(1);
    DECLARE @cp INT;
    DECLARE @addend INT;

    WHILE @i >= 1
    BEGIN
        SET @ch = SUBSTRING(@clean, @i, 1);
        SET @cp = CHARINDEX(@ch, @alphabet) - 1;
        IF @cp >= 0
        BEGIN
            SET @addend = @factor * @cp;
            SET @factor = CASE WHEN @factor = 2 THEN 1 ELSE 2 END;
            SET @addend = (@addend / 36) + (@addend % 36);
            SET @sum = @sum + @addend;
        END
        SET @i = @i - 1;
    END

    DECLARE @checkCp INT = (36 - (@sum % 36)) % 36;
    RETURN SUBSTRING(@alphabet, @checkCp + 1, 1);
END
GO

-- Đảm bảo các cột Audit có DEFAULT constraint khi bảng được tạo từ Hibernate trước đó
DECLARE @tbl NVARCHAR(128);
DECLARE @sqlDef NVARCHAR(MAX);
DECLARE cur_defaults CURSOR LOCAL FAST_FORWARD FOR
    SELECT t.name
    FROM sys.tables t
    WHERE t.name IN ('branches', 'users', 'customers', 'collaterals', 'pawn_contracts', 'interest_payments', 'cashbook_transactions', 'ltv_policies');

OPEN cur_defaults;
FETCH NEXT FROM cur_defaults INTO @tbl;
WHILE @@FETCH_STATUS = 0
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sys.default_constraints dc
        INNER JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
        WHERE dc.parent_object_id = OBJECT_ID('dbo.' + @tbl) AND c.name = 'created_at'
    )
    BEGIN
        SET @sqlDef = N'ALTER TABLE dbo.' + QUOTENAME(@tbl) + N' ADD CONSTRAINT DF_' + @tbl + N'_created_at DEFAULT SYSDATETIME() FOR created_at;';
        EXEC sp_executesql @sqlDef;
    END

    IF NOT EXISTS (
        SELECT 1 FROM sys.default_constraints dc
        INNER JOIN sys.columns c ON dc.parent_object_id = c.object_id AND dc.parent_column_id = c.column_id
        WHERE dc.parent_object_id = OBJECT_ID('dbo.' + @tbl) AND c.name = 'is_deleted'
    )
    BEGIN
        SET @sqlDef = N'ALTER TABLE dbo.' + QUOTENAME(@tbl) + N' ADD CONSTRAINT DF_' + @tbl + N'_is_deleted DEFAULT 0 FOR is_deleted;';
        EXEC sp_executesql @sqlDef;
    END

    FETCH NEXT FROM cur_defaults INTO @tbl;
END
CLOSE cur_defaults;
DEALLOCATE cur_defaults;
GO
