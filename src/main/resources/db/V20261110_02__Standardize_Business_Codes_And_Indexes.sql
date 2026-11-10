-- =======================================================================================
-- PAWNMASTER - BUSINESS CODE STANDARDIZATION & SQL SERVER INDEX OPTIMIZATION MIGRATION
-- File: V20261110_02__Standardize_Business_Codes_And_Indexes.sql
-- Purpose:
--   1. Create & persist code generation rules per table in dbo.business_code_rules
--   2. Optimize SQL Server B-Tree Unique Covering Indexes (INCLUDE + WHERE is_deleted = 0)
--   3. Migrate all existing seed records (customers, collaterals, pawn_contracts,
--      interest_payments, cashbook_transactions) to strictly conform to the new rules
-- =======================================================================================

SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

-- =======================================================================================
-- 1. BẢNG LƯU QUY TẮC MÃ ĐỊNH DANH NGHIỆP VỤ (dbo.business_code_rules)
-- =======================================================================================
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

MERGE dbo.business_code_rules AS target
USING (VALUES
    ('branches', 'branch_code', 'CN',
     'CN-<PROVINCE_ABBR>-<SEQ_2>',
     '^CN-[A-Z]{2,4}-\d{2,3}$',
     'CN-HCM-01', 1, 'NONE',
     'idx_branches_code_cov',
     N'UNIQUE NONCLUSTERED INDEX (branch_code) INCLUDE (branch_name, province_code, district_code, current_cash_balance, is_active) WHERE is_deleted = 0',
     N'Mã chi nhánh theo viết tắt Tỉnh/Thành phố + Số thứ tự 2 chữ số.'),

    ('customers', 'customer_code', 'KH',
     'KH-<YYMM>-<5_BASE36><1_LUHN36_CHK>',
     '^KH-\d{4}-[0-9A-Z]{6}$',
     'KH-2611-01A9K7', 0, 'LUHN_MOD_36',
     'idx_customers_code_cov',
     N'UNIQUE NONCLUSTERED INDEX (customer_code) INCLUDE (full_name, identity_number, phone_number, occupation_group, status) WHERE is_deleted = 0',
     N'Mã khách hàng độc lập chi nhánh (giao dịch toàn chuỗi), gom cụm B-Tree theo YYMM + 5 ký tự Base36 + 1 ký tự Checksum Luhn Mod-36.'),

    ('collaterals', 'collateral_code', 'TS',
     'TS-<YYMM>-B<BRANCH_ID_PAD2>-<CAT2><4_BASE36>',
     '^TS-\d{4}-B\d{2,3}-(XM|OT|DT|LT|VG|DH|BD|KH)[0-9A-Z]{4}$',
     'TS-2611-B01-XM0101', 1, 'NONE',
     'idx_collaterals_code_cov',
     N'UNIQUE NONCLUSTERED INDEX (collateral_code) INCLUDE (customer_id, branch_id, category, appraised_value, status) WHERE is_deleted = 0',
     N'Mã tài sản cầm cố phân theo Tháng (YYMM) + Chi nhánh (Bxx) + Mã nhóm tài sản 2 ký tự (XM/OT/DT/LT/VG/DH/BD/KH) + 4 ký tự Base36.'),

    ('pawn_contracts', 'contract_code', 'HD',
     'HD-<YYMM>-B<BRANCH_ID_PAD2>-<6_SEQ_OR_BASE36>',
     '^HD-\d{4}-B\d{2,3}-[0-9A-Z]{6}$',
     'HD-2611-B01-000101', 1, 'NONE',
     'idx_contracts_code_cov',
     N'UNIQUE NONCLUSTERED INDEX (contract_code) INCLUDE (branch_id, customer_id, collateral_id, principal_amount, remaining_principal, status, maturity_date) WHERE is_deleted = 0',
     N'Mã hợp đồng cầm cố chịu tải lớn (>2,17 tỷ mã/chi nhánh/tháng), đặt YYMM trước Bxx để tối ưu B-Tree Range Seek theo kỳ và theo chi nhánh.'),

    ('interest_payments', 'payment_code', 'PT',
     'PT-<TYPE3>-<YYYYMMDD>-<HHmmss>-<3_BASE36>',
     '^PT-(LAI|GOC|TAT|TLY)-\d{8}-\d{6}-[0-9A-Z]{3}$',
     'PT-LAI-20261110-093122-8FA', 0, 'NONE',
     'idx_payments_code_cov',
     N'UNIQUE NONCLUSTERED INDEX (payment_code) INCLUDE (contract_id, payment_type, total_amount, payment_date) WHERE is_deleted = 0',
     N'Mã phiếu thu hợp đồng phân loại (LAI=Đóng lãi, GOC=Trả bớt gốc, TAT=Tất toán chuộc đồ, TLY=Thanh lý) + Timestamp tới giây + 3 ký tự Base36.'),

    ('cashbook_transactions', 'transaction_code', 'IN/OUT',
     '<IN|OUT>-<YYYYMMDD>-<HHmmss>-<3_BASE36>',
     '^(IN|OUT)-\d{8}-\d{6}-[0-9A-Z]{3}$',
     'IN-20261110-093122-8FA', 1, 'NONE',
     'idx_cashbook_code_cov',
     N'UNIQUE NONCLUSTERED INDEX (transaction_code) INCLUDE (branch_id, transaction_type, category, amount, balance_after, transaction_date) WHERE is_deleted = 0',
     N'Mã giao dịch sổ quỹ Thu (IN) / Chi (OUT) + Timestamp YYYYMMDD-HHmmss tăng đơn điệu + 3 ký tự Base36 chống đụng độ.')
) AS src (table_name, column_name, prefix, format_pattern, regex_validation, example_code, is_branch_scoped, checksum_algorithm, index_name, index_strategy, description)
ON target.table_name = src.table_name AND target.column_name = src.column_name
WHEN MATCHED THEN
    UPDATE SET
        prefix = src.prefix,
        format_pattern = src.format_pattern,
        regex_validation = src.regex_validation,
        example_code = src.example_code,
        is_branch_scoped = src.is_branch_scoped,
        checksum_algorithm = src.checksum_algorithm,
        index_name = src.index_name,
        index_strategy = src.index_strategy,
        description = src.description,
        updated_at = SYSDATETIME()
WHEN NOT MATCHED THEN
    INSERT (table_name, column_name, prefix, format_pattern, regex_validation, example_code, is_branch_scoped, checksum_algorithm, index_name, index_strategy, description)
    VALUES (src.table_name, src.column_name, src.prefix, src.format_pattern, src.regex_validation, src.example_code, src.is_branch_scoped, src.checksum_algorithm, src.index_name, src.index_strategy, src.description);
GO

-- =======================================================================================
-- 2. TỐI ƯU COVERING NONCLUSTERED INDEXES TRÊN SQL SERVER CHO CÁC CỘT CODE
-- =======================================================================================

-- 2.1. branches(branch_code)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_branches_code_cov' AND object_id = OBJECT_ID('dbo.branches'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_branches_code_cov
    ON dbo.branches(branch_code)
    INCLUDE (branch_name, province_code, district_code, current_cash_balance, is_active)
    WHERE is_deleted = 0;
END

-- 2.2. customers(customer_code)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_customers_code_cov' AND object_id = OBJECT_ID('dbo.customers'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_customers_code_cov
    ON dbo.customers(customer_code)
    INCLUDE (full_name, identity_number, phone_number, occupation_group, status)
    WHERE is_deleted = 0;
END

-- 2.3. collaterals(collateral_code)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_collaterals_code_cov' AND object_id = OBJECT_ID('dbo.collaterals'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_collaterals_code_cov
    ON dbo.collaterals(collateral_code)
    INCLUDE (customer_id, branch_id, category, appraised_value, status)
    WHERE is_deleted = 0;
END

-- 2.4. pawn_contracts(contract_code)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_contracts_code_cov' AND object_id = OBJECT_ID('dbo.pawn_contracts'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_contracts_code_cov
    ON dbo.pawn_contracts(contract_code)
    INCLUDE (branch_id, customer_id, collateral_id, principal_amount, remaining_principal, status, maturity_date)
    WHERE is_deleted = 0;
END

-- 2.5. interest_payments(payment_code)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_payments_code_cov' AND object_id = OBJECT_ID('dbo.interest_payments'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_payments_code_cov
    ON dbo.interest_payments(payment_code)
    INCLUDE (contract_id, payment_type, total_amount, payment_date)
    WHERE is_deleted = 0;
END

-- 2.6. cashbook_transactions(transaction_code)
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'idx_cashbook_code_cov' AND object_id = OBJECT_ID('dbo.cashbook_transactions'))
BEGIN
    CREATE UNIQUE NONCLUSTERED INDEX idx_cashbook_code_cov
    ON dbo.cashbook_transactions(transaction_code)
    INCLUDE (branch_id, transaction_type, category, amount, balance_after, transaction_date)
    WHERE is_deleted = 0;
END
GO

-- =======================================================================================
-- 2B. HÀM TÍNH CHECKSUM LUHN MOD-36 TRONG SQL SERVER (dbo.fn_CalcLuhnMod36)
-- Đồng bộ thuật toán 100% với BusinessCodeGenerator.computeLuhnMod36Checksum() trong Java
-- =======================================================================================
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

-- =======================================================================================
-- 3. CHUẨN HÓA TOÀN BỘ DỮ LIỆU MÃ CODE TRONG DATABASE THEO QUY TẮC MỚI
-- =======================================================================================
BEGIN TRANSACTION;

-- 3.1. Chuẩn hóa customers.customer_code: KH-<YYMM>-<5_BASE36><1_LUHN36_CHK> (Độc lập chi nhánh)
;WITH CustomerPayload AS (
    SELECT
        c.id,
        FORMAT(ISNULL(
            (SELECT MIN(pc.start_date) FROM dbo.pawn_contracts pc WHERE pc.customer_id = c.id),
            CAST(ISNULL(c.created_at, SYSDATETIME()) AS DATE)
        ), 'yyMM') AS yymm,
        CONCAT(
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((c.id * 3 + 7) % 36) + 1, 1),
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((c.id * 11 + 19) % 36) + 1, 1),
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((c.id * 17 + 5) % 36) + 1, 1),
            RIGHT('00' + CAST(c.id AS VARCHAR(10)), 2)
        ) AS payload5
    FROM dbo.customers c
)
UPDATE c
SET c.customer_code = CONCAT(
        'KH-',
        cp.yymm,
        '-',
        cp.payload5,
        dbo.fn_CalcLuhnMod36(CONCAT('KH', cp.yymm, cp.payload5))
    ),
    c.updated_at = SYSDATETIME()
FROM dbo.customers c
INNER JOIN CustomerPayload cp ON c.id = cp.id;

-- 3.2. Chuẩn hóa collaterals.collateral_code: TS-<YYMM>-B<BRANCH_ID_PAD2>-<CAT2><4_BASE36>
;WITH CollateralFormatted AS (
    SELECT
        col.id,
        CONCAT(
            'TS-',
            FORMAT(ISNULL(pc.start_date, CAST(col.created_at AS DATE)), 'yyMM'),
            '-B',
            RIGHT('00' + CAST(ISNULL(col.branch_id, 1) AS VARCHAR(5)), 2),
            '-',
            CASE col.category
                WHEN 'MOTORBIKE'    THEN 'XM'
                WHEN 'CAR'          THEN 'OT'
                WHEN 'PHONE'        THEN 'DT'
                WHEN 'LAPTOP'       THEN 'LT'
                WHEN 'GOLD_JEWELRY' THEN 'VG'
                WHEN 'WATCH'        THEN 'DH'
                WHEN 'REAL_ESTATE'  THEN 'BD'
                ELSE 'KH'
            END,
            RIGHT('0000' + CAST(100 + ROW_NUMBER() OVER (PARTITION BY col.branch_id ORDER BY col.id) AS VARCHAR(10)), 4)
        ) AS new_collateral_code
    FROM dbo.collaterals col
    LEFT JOIN dbo.pawn_contracts pc ON pc.collateral_id = col.id
)
UPDATE col
SET col.collateral_code = cf.new_collateral_code,
    col.updated_at = SYSDATETIME()
FROM dbo.collaterals col
INNER JOIN CollateralFormatted cf ON col.id = cf.id;

-- 3.3. Chuẩn hóa pawn_contracts.contract_code: HD-<YYMM>-B<BRANCH_ID_PAD2>-<6_SEQ_OR_BASE36>
;WITH ContractFormatted AS (
    SELECT
        pc.id,
        pc.contract_code AS old_contract_code,
        CONCAT(
            'HD-',
            FORMAT(pc.start_date, 'yyMM'),
            '-B',
            RIGHT('00' + CAST(ISNULL(pc.branch_id, 1) AS VARCHAR(5)), 2),
            '-',
            RIGHT('000000' + CAST(100 + ROW_NUMBER() OVER (PARTITION BY pc.branch_id, FORMAT(pc.start_date, 'yyMM') ORDER BY pc.start_date, pc.id) AS VARCHAR(10)), 6)
        ) AS new_contract_code
    FROM dbo.pawn_contracts pc
)
UPDATE pc
SET pc.contract_code = cf.new_contract_code,
    pc.updated_at = SYSDATETIME()
FROM dbo.pawn_contracts pc
INNER JOIN ContractFormatted cf ON pc.id = cf.id;

-- 3.4. Chuẩn hóa interest_payments.payment_code: PT-<TYPE3>-<YYYYMMDD>-<HHmmss>-<3_BASE36>
;WITH PaymentFormatted AS (
    SELECT
        ip.id,
        CONCAT(
            'PT-',
            CASE ip.payment_type
                WHEN 'INTEREST_PAYMENT'  THEN 'LAI'
                WHEN 'PARTIAL_PRINCIPAL' THEN 'GOC'
                WHEN 'REDEMPTION'        THEN 'TAT'
                WHEN 'LIQUIDATION'       THEN 'TLY'
                ELSE 'LAI'
            END,
            '-',
            FORMAT(ip.payment_date, 'yyyyMMdd-HHmmss'),
            '-',
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((ip.id * 7) % 36) + 1, 1),
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((ip.id * 13 + 5) % 36) + 1, 1),
            RIGHT('0' + CAST(ip.id % 10 AS VARCHAR(2)), 1)
        ) AS new_payment_code
    FROM dbo.interest_payments ip
)
UPDATE ip
SET ip.payment_code = pf.new_payment_code,
    ip.updated_at = SYSDATETIME()
FROM dbo.interest_payments ip
INNER JOIN PaymentFormatted pf ON ip.id = pf.id;

-- 3.5. Chuẩn hóa cashbook_transactions.transaction_code: <IN|OUT>-<YYYYMMDD>-<HHmmss>-<3_BASE36>
;WITH CashbookFormatted AS (
    SELECT
        cb.id,
        CONCAT(
            CASE cb.transaction_type
                WHEN 'CASH_OUT' THEN 'OUT'
                ELSE 'IN'
            END,
            '-',
            FORMAT(cb.transaction_date, 'yyyyMMdd-HHmmss'),
            '-',
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((cb.id * 11 + 3) % 36) + 1, 1),
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((cb.id * 17 + 9) % 36) + 1, 1),
            SUBSTRING('0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ', ((cb.id * 23 + 1) % 36) + 1, 1)
        ) AS new_tx_code
    FROM dbo.cashbook_transactions cb
)
UPDATE cb
SET cb.transaction_code = cbf.new_tx_code,
    cb.updated_at = SYSDATETIME()
FROM dbo.cashbook_transactions cb
INNER JOIN CashbookFormatted cbf ON cb.id = cbf.id;

-- Cập nhật đồng bộ mã hợp đồng mới trong cột description/notes của cashbook_transactions
UPDATE cb
SET cb.description = CONCAT(
        CASE cb.category
            WHEN 'DISBURSEMENT'         THEN N'Chi giải ngân hợp đồng '
            WHEN 'INTEREST_COLLECTION'  THEN N'Thu tiền lãi hợp đồng '
            WHEN 'PRINCIPAL_COLLECTION' THEN N'Thu tiền gốc/tất toán hợp đồng '
            WHEN 'LIQUIDATION_INCOME'   THEN N'Thu tiền thanh lý tài sản hợp đồng '
            ELSE N'Giao dịch hợp đồng '
        END,
        pc.contract_code
    ),
    cb.notes = CONCAT(
        CASE cb.category
            WHEN 'DISBURSEMENT'         THEN N'Chi giải ngân hợp đồng '
            WHEN 'INTEREST_COLLECTION'  THEN N'Thu tiền lãi hợp đồng '
            WHEN 'PRINCIPAL_COLLECTION' THEN N'Thu tiền gốc/tất toán hợp đồng '
            WHEN 'LIQUIDATION_INCOME'   THEN N'Thu tiền thanh lý tài sản hợp đồng '
            ELSE N'Giao dịch hợp đồng '
        END,
        pc.contract_code
    )
FROM dbo.cashbook_transactions cb
INNER JOIN dbo.pawn_contracts pc ON cb.contract_id = pc.id
WHERE cb.contract_id IS NOT NULL;

COMMIT TRANSACTION;
GO
