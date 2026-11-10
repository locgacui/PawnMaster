-- =========================================================================
-- PAWNMASTER DATABASE SCHEMA (Microsoft SQL Server)
-- Server: SNOWFOX\SQLEXPRESS | Database: PawnMasterDB
-- =========================================================================

IF NOT EXISTS (SELECT name FROM sys.databases WHERE name = N'PawnMasterDB')
BEGIN
    CREATE DATABASE [PawnMasterDB];
END
GO

USE [PawnMasterDB];
GO

-- =========================================================================
-- PART I: VIETNAMESE ADMINISTRATIVE UNITS (Tỉnh/TP, Quận/Huyện, Phường/Xã)
-- =========================================================================

-- 0.1. Table: administrative_regions
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

-- 0.2. Table: administrative_units
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

-- 0.3. Table: provinces (63 Tỉnh / Thành phố)
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

-- 0.4. Table: districts (Quận / Huyện / Thị xã / TP thuộc Tỉnh)
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

-- 0.5. Table: wards (Phường / Xã / Thị trấn)
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

-- =========================================================================
-- PART II: PAWNMASTER CORE DOMAIN TABLES
-- =========================================================================

-- 1. Table: branches (Quản lý Đa Chi Nhánh theo province_code & district_code)
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
GO

-- 2. Table: users (Nhân viên & Quản lý Chi nhánh)
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

-- 3. Table: customers (Khách hàng, Nhóm nghề nghiệp 1-6 & Chứng từ xác minh)
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
ELSE
BEGIN
    IF COL_LENGTH('dbo.customers', 'branch_id') IS NULL
    BEGIN
        ALTER TABLE dbo.customers ADD branch_id BIGINT NULL CONSTRAINT fk_customers_branch FOREIGN KEY REFERENCES dbo.branches(id);
        CREATE INDEX idx_customers_branch ON dbo.customers(branch_id);
    END
    IF COL_LENGTH('dbo.customers', 'occupation_group') IS NULL
    BEGIN
        ALTER TABLE dbo.customers ADD
            province_code VARCHAR(20) NULL CONSTRAINT fk_customers_province FOREIGN KEY REFERENCES dbo.provinces(code),
            district_code VARCHAR(20) NULL CONSTRAINT fk_customers_district FOREIGN KEY REFERENCES dbo.districts(code),
            occupation_group VARCHAR(40) NOT NULL DEFAULT 'FREELANCER',
            monthly_income DECIMAL(18, 2) NULL,
            has_social_insurance BIT NOT NULL DEFAULT 0,
            verification_doc_type VARCHAR(50) NULL,
            verification_doc_urls NVARCHAR(1000) NULL;
    END
END
GO

-- 4. Table: collaterals (Tài sản cầm cố theo Chi nhánh)
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
ELSE IF COL_LENGTH('dbo.collaterals', 'branch_id') IS NULL
BEGIN
    ALTER TABLE dbo.collaterals ADD
        branch_id BIGINT NULL CONSTRAINT fk_collaterals_branch FOREIGN KEY REFERENCES dbo.branches(id),
        max_ltv_percent DECIMAL(5, 2) NULL;
END
GO

-- 5. Table: pawn_contracts (Hợp đồng cầm cố & Phê duyệt 2 bước tại Chi nhánh)
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
ELSE IF COL_LENGTH('dbo.pawn_contracts', 'branch_id') IS NULL
BEGIN
    ALTER TABLE dbo.pawn_contracts ADD
        branch_id BIGINT NULL CONSTRAINT fk_contracts_branch FOREIGN KEY REFERENCES dbo.branches(id),
        applied_ltv_percent DECIMAL(5, 2) NULL,
        loan_term_days INT NOT NULL DEFAULT 30,
        approved_by BIGINT NULL,
        approved_at DATETIME2 NULL,
        approval_notes NVARCHAR(500) NULL;
END
GO

-- 6. Table: interest_payments
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

-- 7. Table: cashbook_transactions (Sổ quỹ Thu/Chi theo Chi nhánh)
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
ELSE IF COL_LENGTH('dbo.cashbook_transactions', 'branch_id') IS NULL
BEGIN
    ALTER TABLE dbo.cashbook_transactions ADD
        branch_id BIGINT NULL CONSTRAINT fk_cashbook_branch FOREIGN KEY REFERENCES dbo.branches(id);
END
GO

-- Seed & Update Default Branches (linked to provinces, districts, wards from ImportData_vn_units.sql)
IF EXISTS (SELECT 1 FROM dbo.provinces WHERE code IN ('01', '79'))
   AND EXISTS (SELECT 1 FROM dbo.districts WHERE code IN ('005', '760', '765'))
BEGIN
    DECLARE @wardQ1 VARCHAR(20) = CASE WHEN EXISTS (SELECT 1 FROM dbo.wards WHERE code = '26749') THEN '26749' ELSE NULL END;
    DECLARE @wardBT VARCHAR(20) = CASE WHEN EXISTS (SELECT 1 FROM dbo.wards WHERE code = '26938') THEN '26938' ELSE NULL END;
    DECLARE @wardCG VARCHAR(20) = CASE WHEN EXISTS (SELECT 1 FROM dbo.wards WHERE code = '00169') THEN '00169' ELSE NULL END;

    -- 1. Chi nhánh Quận 1 (TP. Hồ Chí Minh - Quận 1 - Phường Phạm Ngũ Lão)
    IF EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-HCM-01')
        UPDATE dbo.branches
        SET branch_name = N'Chi nhánh Quận 1',
            province_code = '79',
            district_code = '760',
            ward_code = COALESCE(@wardQ1, ward_code),
            address_detail = N'128 Nguyễn Thái Học',
            phone_number = '02838221100',
            capital_limit = 2500000000.00,
            updated_at = SYSDATETIME(),
            updated_by = 'SYSTEM'
        WHERE branch_code = 'CN-HCM-01';
    ELSE
        INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
        VALUES ('CN-HCM-01', N'Chi nhánh Quận 1', '79', '760', @wardQ1, N'128 Nguyễn Thái Học', '02838221100', 2500000000.00, 520000000.00, 1, 'SYSTEM');

    -- 2. Chi nhánh Quận Bình Thạnh (TP. Hồ Chí Minh - Quận Bình Thạnh - Phường 15)
    IF EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-HCM-02')
        UPDATE dbo.branches
        SET branch_name = N'Chi nhánh Quận Bình Thạnh',
            province_code = '79',
            district_code = '765',
            ward_code = COALESCE(@wardBT, ward_code),
            address_detail = N'45 Điện Biên Phủ',
            phone_number = '02838992200',
            capital_limit = 2000000000.00,
            updated_at = SYSDATETIME(),
            updated_by = 'SYSTEM'
        WHERE branch_code = 'CN-HCM-02';
    ELSE
        INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
        VALUES ('CN-HCM-02', N'Chi nhánh Quận Bình Thạnh', '79', '765', @wardBT, N'45 Điện Biên Phủ', '02838992200', 2000000000.00, 350000000.00, 1, 'SYSTEM');

    -- 3. Chi nhánh Cầu Giấy (TP. Hà Nội - Quận Cầu Giấy - Phường Quan Hoa)
    IF EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-HN-01')
        UPDATE dbo.branches
        SET branch_name = N'Chi nhánh Cầu Giấy',
            province_code = '01',
            district_code = '005',
            ward_code = COALESCE(@wardCG, ward_code),
            address_detail = N'216 Cầu Giấy',
            phone_number = '02437663300',
            capital_limit = 1500000000.00,
            updated_at = SYSDATETIME(),
            updated_by = 'SYSTEM'
        WHERE branch_code = 'CN-HN-01';
    ELSE
        INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
        VALUES ('CN-HN-01', N'Chi nhánh Cầu Giấy', '01', '005', @wardCG, N'216 Cầu Giấy', '02437663300', 1500000000.00, 410000000.00, 1, 'SYSTEM');
END
GO

-- Seed Default Admin User (username: admin)
IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'admin')
BEGIN
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, is_active, created_by)
    VALUES (
        'admin',
        '$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.AQubh4a',
        N'Quản Trị Viên Hệ Thống',
        '0901234567',
        'admin@pawnmaster.com',
        'ADMIN',
        1,
        'SYSTEM'
    );
END
GO
