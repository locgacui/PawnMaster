-- =========================================================================
-- DATA MIGRATION: V20261110__Seed_MultiBranch_Operations_Data.sql
-- Purpose: Seed comprehensive multi-branch operational data across:
--   1. dbo.branches (5 branches across Ho Chi Minh, Ha Noi, Da Nang, Can Tho)
--   2. dbo.users (Branch Managers, Counter Staff, Accountants per branch)
--   3. dbo.customers (22 customers across all 6 CustomerOccupationGroup tiers)
--   4. dbo.collaterals (22 collaterals across MOTORBIKE, CAR, GOLD_JEWELRY, PHONE, LAPTOP, WATCH, REAL_ESTATE)
--   5. dbo.pawn_contracts (22 contracts across PENDING_APPROVAL, ACTIVE, EXTENDED, OVERDUE, REDEEMED, LIQUIDATED)
--   6. dbo.interest_payments (Periodic interest collections, partial principal reductions, redemptions, liquidations)
--   7. dbo.cashbook_transactions (Branch-level CASH_IN and CASH_OUT ledger entries with running balance_after)
-- =========================================================================

USE [PawnMasterDB];
GO

SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
SET XACT_ABORT ON;

BEGIN TRANSACTION;

-- =========================================================================
-- STEP 0: ENSURE DEFAULT CONSTRAINTS ON BASE AUDIT COLUMNS (created_at, is_deleted)
-- =========================================================================
DECLARE @defSql NVARCHAR(MAX) = N'';
SELECT @defSql += N'ALTER TABLE dbo.' + QUOTENAME(t.name) + N' ADD CONSTRAINT DF_' + t.name + N'_created_at DEFAULT SYSDATETIME() FOR created_at;' + CHAR(13)
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id AND c.name = 'created_at'
WHERE c.default_object_id = 0;

SELECT @defSql += N'ALTER TABLE dbo.' + QUOTENAME(t.name) + N' ADD CONSTRAINT DF_' + t.name + N'_is_deleted DEFAULT 0 FOR is_deleted;' + CHAR(13)
FROM sys.tables t
JOIN sys.columns c ON c.object_id = t.object_id AND c.name = 'is_deleted'
WHERE c.default_object_id = 0;

IF LEN(@defSql) > 0 EXEC sp_executesql @defSql;

-- =========================================================================
-- STEP 1: EXPAND & UPDATE BRANCHES (5 CHI NHÁNH TRÊN TOÀN QUỐC)
-- =========================================================================

-- 1.1. Ensure CN-HCM-01 (Quận 1, TP.HCM)
IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-HCM-01')
BEGIN
    INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
    VALUES ('CN-HCM-01', N'Chi nhánh Quận 1', '79', '760', '26749', N'128 Nguyễn Thái Học', '02838221100', 2500000000.00, 520000000.00, 1, 'SYSTEM');
END
ELSE
BEGIN
    UPDATE dbo.branches
    SET province_code = '79', district_code = '760', ward_code = '26749',
        address_detail = N'128 Nguyễn Thái Học', phone_number = '02838221100',
        capital_limit = 2500000000.00, current_cash_balance = 520000000.00, is_active = 1, updated_at = SYSDATETIME()
    WHERE branch_code = 'CN-HCM-01';
END

-- 1.2. Ensure CN-HCM-02 (Quận Bình Thạnh, TP.HCM)
IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-HCM-02')
BEGIN
    INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
    VALUES ('CN-HCM-02', N'Chi nhánh Quận Bình Thạnh', '79', '765', '26938', N'45 Điện Biên Phủ', '02838992200', 2000000000.00, 350000000.00, 1, 'SYSTEM');
END
ELSE
BEGIN
    UPDATE dbo.branches
    SET province_code = '79', district_code = '765', ward_code = '26938',
        address_detail = N'45 Điện Biên Phủ', phone_number = '02838992200',
        capital_limit = 2000000000.00, current_cash_balance = 350000000.00, is_active = 1, updated_at = SYSDATETIME()
    WHERE branch_code = 'CN-HCM-02';
END

-- 1.3. Ensure CN-HN-01 (Quận Cầu Giấy, TP. Hà Nội)
IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-HN-01')
BEGIN
    INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
    VALUES ('CN-HN-01', N'Chi nhánh Cầu Giấy', '01', '005', '00169', N'216 Cầu Giấy', '02437663300', 1500000000.00, 410000000.00, 1, 'SYSTEM');
END
ELSE
BEGIN
    UPDATE dbo.branches
    SET province_code = '01', district_code = '005', ward_code = '00169',
        address_detail = N'216 Cầu Giấy', phone_number = '02437663300',
        capital_limit = 1500000000.00, current_cash_balance = 410000000.00, is_active = 1, updated_at = SYSDATETIME()
    WHERE branch_code = 'CN-HN-01';
END

-- 1.4. Seed CN-DN-01 (Quận Hải Châu, TP. Đà Nẵng)
IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-DN-01')
BEGIN
    INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
    VALUES ('CN-DN-01', N'Chi nhánh Hải Châu', '48', '492', '20242', N'86 Nguyễn Văn Linh', '02363884400', 1800000000.00, 460000000.00, 1, 'SYSTEM');
END

-- 1.5. Seed CN-CT-01 (Quận Ninh Kiều, TP. Cần Thơ)
IF NOT EXISTS (SELECT 1 FROM dbo.branches WHERE branch_code = 'CN-CT-01')
BEGIN
    INSERT INTO dbo.branches (branch_code, branch_name, province_code, district_code, ward_code, address_detail, phone_number, capital_limit, current_cash_balance, is_active, created_by)
    VALUES ('CN-CT-01', N'Chi nhánh Ninh Kiều', '92', '916', '31135', N'102 Đại lộ Hòa Bình', '02923775500', 1200000000.00, 310000000.00, 1, 'SYSTEM');
END

DECLARE @branchQ1 BIGINT = (SELECT id FROM dbo.branches WHERE branch_code = 'CN-HCM-01');
DECLARE @branchBT BIGINT = (SELECT id FROM dbo.branches WHERE branch_code = 'CN-HCM-02');
DECLARE @branchCG BIGINT = (SELECT id FROM dbo.branches WHERE branch_code = 'CN-HN-01');
DECLARE @branchDN BIGINT = (SELECT id FROM dbo.branches WHERE branch_code = 'CN-DN-01');
DECLARE @branchCT BIGINT = (SELECT id FROM dbo.branches WHERE branch_code = 'CN-CT-01');

-- =========================================================================
-- STEP 2: SEED BRANCH USERS (MANAGERS, STAFF, ACCOUNTANTS)
-- =========================================================================
DECLARE @defaultPwdHash VARCHAR(255) = '$2a$10$vE795lne9/BngvPXFUgxRehK972b0jOHXrFT1nqBD5M4zt5YZFY7m';

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'manager_bt')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('manager_bt', @defaultPwdHash, N'Phạm Quốc Quản Lý (Bình Thạnh)', '0901000005', 'manager.bt@pawnmaster.vn', 'MANAGER', @branchBT, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'staff_bt')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('staff_bt', @defaultPwdHash, N'Đặng Thị Giao Dịch (Bình Thạnh)', '0901000006', 'staff.bt@pawnmaster.vn', 'STAFF', @branchBT, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'accountant_bt')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('accountant_bt', @defaultPwdHash, N'Võ Minh Kế Toán (Bình Thạnh)', '0901000007', 'accountant.bt@pawnmaster.vn', 'ACCOUNTANT', @branchBT, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'manager_cg')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('manager_cg', @defaultPwdHash, N'Hoàng Minh Quản Lý (Cầu Giấy)', '0901000008', 'manager.cg@pawnmaster.vn', 'MANAGER', @branchCG, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'staff_cg')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('staff_cg', @defaultPwdHash, N'Nguyễn Thu Giao Dịch (Cầu Giấy)', '0901000009', 'staff.cg@pawnmaster.vn', 'STAFF', @branchCG, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'accountant_cg')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('accountant_cg', @defaultPwdHash, N'Bùi Quang Kế Toán (Cầu Giấy)', '0901000010', 'accountant.cg@pawnmaster.vn', 'ACCOUNTANT', @branchCG, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'manager_dn')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('manager_dn', @defaultPwdHash, N'Trần Đình Quản Lý (Đà Nẵng)', '0901000011', 'manager.dn@pawnmaster.vn', 'MANAGER', @branchDN, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'staff_dn')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('staff_dn', @defaultPwdHash, N'Lê Thị Giao Dịch (Đà Nẵng)', '0901000012', 'staff.dn@pawnmaster.vn', 'STAFF', @branchDN, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'manager_ct')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('manager_ct', @defaultPwdHash, N'Huỳnh Văn Quản Lý (Cần Thơ)', '0901000013', 'manager.ct@pawnmaster.vn', 'MANAGER', @branchCT, 1, 'SYSTEM');

IF NOT EXISTS (SELECT 1 FROM dbo.users WHERE username = 'staff_ct')
    INSERT INTO dbo.users (username, password_hash, full_name, phone_number, email, role, branch_id, is_active, created_by)
    VALUES ('staff_ct', @defaultPwdHash, N'Phan Ngọc Giao Dịch (Cần Thơ)', '0901000014', 'staff.ct@pawnmaster.vn', 'STAFF', @branchCT, 1, 'SYSTEM');

DECLARE @mgrQ1 BIGINT = ISNULL((SELECT id FROM dbo.users WHERE username = 'manager_q1'), 1);
DECLARE @mgrBT BIGINT = ISNULL((SELECT id FROM dbo.users WHERE username = 'manager_bt'), 1);
DECLARE @mgrCG BIGINT = ISNULL((SELECT id FROM dbo.users WHERE username = 'manager_cg'), 1);
DECLARE @mgrDN BIGINT = ISNULL((SELECT id FROM dbo.users WHERE username = 'manager_dn'), 1);
DECLARE @mgrCT BIGINT = ISNULL((SELECT id FROM dbo.users WHERE username = 'manager_ct'), 1);

-- =========================================================================
-- STEP 3: SEED CUSTOMERS (22 KHÁCH HÀNG THUỘC 6 NHÓM NGHỀ NGHIỆP)
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.customers WHERE customer_code = 'KH-HCM01-001')
BEGIN
    INSERT INTO dbo.customers (
        customer_code, branch_id, full_name, identity_number, identity_issue_date, identity_issue_place,
        phone_number, email, date_of_birth, gender, province_code, district_code, address, permanent_address,
        occupation_group, monthly_income, has_social_insurance, verification_doc_type, verification_doc_urls,
        id_card_front_url, id_card_back_url, portrait_url, status, notes, created_by
    )
    VALUES
    -- Chi nhánh Quận 1 (11 khách hàng)
    ('KH-HCM01-001', @branchQ1, N'Võ Hoàng Yến', '079198003124', '2022-04-15', N'Cục Cảnh sát QLHC về TTXH',
     '0918345678', 'vohoangyen@gmail.com', '1998-06-12', N'Nữ', '79', '760',
     N'42 Trần Hưng Đạo, Phường Phạm Ngũ Lão, Quận 1, TP.HCM', N'42 Trần Hưng Đạo, Quận 1, TP.HCM',
     'OFFICE_WORKER', 22000000.00, 1, 'PAYSLIP_BANK_STATEMENT', '/uploads/docs/kh_001_payslip.jpg,/uploads/docs/kh_001_vssid.jpg',
     '/uploads/cccd/079198003124_front.jpg', '/uploads/cccd/079198003124_back.jpg', '/uploads/portraits/079198003124.jpg', 'ACTIVE', N'Chuyên viên kế toán, thu nhập ổn định qua Vietcombank', 'staff_q1'),

    ('KH-HCM01-002', @branchQ1, N'Nguyễn Văn An', '079201004821', '2021-09-20', N'Cục Cảnh sát QLHC về TTXH',
     '0908123456', 'nguyenvanan@gmail.com', '1991-03-25', N'Nam', '79', '760',
     N'88 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM', N'88 Nguyễn Huệ, Quận 1, TP.HCM',
     'OFFICE_WORKER', 28000000.00, 1, 'LABOR_CONTRACT', '/uploads/docs/kh_002_contract.jpg,/uploads/docs/kh_002_payslip.jpg',
     '/uploads/cccd/079201004821_front.jpg', '/uploads/cccd/079201004821_back.jpg', '/uploads/portraits/079201004821.jpg', 'ACTIVE', N'Trưởng nhóm kinh doanh, lịch sử trả lãi đúng hạn', 'staff_q1'),

    ('KH-HCM01-003', @branchQ1, N'Trần Thị Mai', '079198005432', '2022-01-10', N'Cục Cảnh sát QLHC về TTXH',
     '0913456789', 'tranthimai@gmail.com', '1989-11-08', N'Nữ', '79', '770',
     N'154 Nguyễn Đình Chiểu, Phường Võ Thị Sáu, Quận 3, TP.HCM', N'154 Nguyễn Đình Chiểu, Quận 3, TP.HCM',
     'SMALL_MERCHANT', 35000000.00, 0, 'BUSINESS_LICENSE_OR_STALL_LEASE', '/uploads/docs/kh_003_stall_lease.jpg',
     '/uploads/cccd/079198005432_front.jpg', '/uploads/cccd/079198005432_back.jpg', '/uploads/portraits/079198005432.jpg', 'ACTIVE', N'Chủ sạp vải chợ Bến Thành, dòng tiền hàng ngày tốt', 'staff_q1'),

    ('KH-HCM01-004', @branchQ1, N'Lê Hoàng Long', '001092003314', '2023-02-18', N'Cục Cảnh sát QLHC về TTXH',
     '0977888999', 'lehoanglong@gmail.com', '1986-07-19', N'Nam', '79', '760',
     N'210 Lê Thánh Tôn, Phường Bến Thành, Quận 1, TP.HCM', N'210 Lê Thánh Tôn, Quận 1, TP.HCM',
     'SMALL_MERCHANT', 65000000.00, 1, 'SHOP_PHOTO_AND_CASHFLOW', '/uploads/docs/kh_004_shop.jpg,/uploads/docs/kh_004_cashflow.jpg',
     '/uploads/cccd/001092003314_front.jpg', '/uploads/cccd/001092003314_back.jpg', '/uploads/portraits/001092003314.jpg', 'ACTIVE', N'Chủ cửa hàng phụ tùng ô tô, vay vốn xoay vòng ngắn hạn', 'staff_q1'),

    ('KH-HCM01-005', @branchQ1, N'Phạm Minh Tuấn', '079185006677', '2021-11-05', N'Cục Cảnh sát QLHC về TTXH',
     '0932111222', 'phamminhtuan@gmail.com', '1995-02-14', N'Nam', '79', '773',
     N'75 Hoàng Diệu, Phường 12, Quận 4, TP.HCM', N'75 Hoàng Diệu, Quận 4, TP.HCM',
     'TECH_DRIVER', 16500000.00, 0, 'DRIVER_APP_INCOME_SCREENSHOT', '/uploads/docs/kh_005_grab_income.jpg,/uploads/docs/kh_005_ct07.jpg',
     '/uploads/cccd/079185006677_front.jpg', '/uploads/cccd/079185006677_back.jpg', '/uploads/portraits/079185006677.jpg', 'ACTIVE', N'Tài xế công nghệ GrabCar, đang trễ hạn đóng lãi 4 ngày', 'staff_q1'),

    ('KH-HCM01-006', @branchQ1, N'Vũ Thị Hoa', '079194002231', '2022-06-30', N'Cục Cảnh sát QLHC về TTXH',
     '0983222333', 'vuthihoa@gmail.com', '1994-09-03', N'Nữ', '79', '778',
     N'312 Huỳnh Tấn Phát, Phường Tân Thuận Đông, Quận 7, TP.HCM', N'312 Huỳnh Tấn Phát, Quận 7, TP.HCM',
     'FACTORY_WORKER', 14000000.00, 1, 'WORKER_ID_OR_CONTRACT', '/uploads/docs/kh_006_worker_id.jpg,/uploads/docs/kh_006_vssid.jpg',
     '/uploads/cccd/079194002231_front.jpg', '/uploads/cccd/079194002231_back.jpg', '/uploads/portraits/079194002231.jpg', 'ACTIVE', N'Công nhân KCX Tân Thuận, có BHXH đầy đủ', 'staff_q1'),

    ('KH-HCM01-007', @branchQ1, N'Đặng Quốc Bảo', '079188009988', '2021-08-12', N'Cục Cảnh sát QLHC về TTXH',
     '0903777666', 'dangquocbao@gmail.com', '1993-12-22', N'Nam', '79', '771',
     N'198 Sư Vạn Hạnh, Phường 12, Quận 10, TP.HCM', N'198 Sư Vạn Hạnh, Quận 10, TP.HCM',
     'ONLINE_SELLER', 32000000.00, 0, 'ECOMMERCE_REVENUE_SCREENSHOT', '/uploads/docs/kh_007_shopee_rev.jpg,/uploads/docs/kh_007_cod.jpg',
     '/uploads/cccd/079188009988_front.jpg', '/uploads/cccd/079188009988_back.jpg', '/uploads/portraits/079188009988.jpg', 'ACTIVE', N'Kinh doanh phụ kiện công nghệ trên Shopee/TikTok Shop', 'staff_q1'),

    ('KH-HCM01-008', @branchQ1, N'Hoàng Thanh Tùng', '079177001122', '2020-12-01', N'Cục Cảnh sát QLHC về TTXH',
     '0918333444', 'hoangthanhtung@gmail.com', '1984-04-17', N'Nam', '79', '760',
     N'65 Nguyễn Du, Phường Bến Nghé, Quận 1, TP.HCM', N'65 Nguyễn Du, Quận 1, TP.HCM',
     'FREELANCER', 45000000.00, 0, 'RESIDENCE_CONFIRMATION_CT07', '/uploads/docs/kh_008_ct07.jpg,/uploads/docs/kh_008_utility.jpg',
     '/uploads/cccd/079177001122_front.jpg', '/uploads/cccd/079177001122_back.jpg', '/uploads/portraits/079177001122.jpg', 'ACTIVE', N'Hợp đồng quá hạn trên 35 ngày, đã chuyển chờ thanh lý', 'staff_q1'),

    ('KH-HCM01-009', @branchQ1, N'Bùi Thu Hương', '079199008899', '2022-07-19', N'Cục Cảnh sát QLHC về TTXH',
     '0945666777', 'buithuhuong@gmail.com', '1992-08-30', N'Nữ', '79', '768',
     N'120 Phan Xích Long, Phường 2, Quận Phú Nhuận, TP.HCM', N'120 Phan Xích Long, Quận Phú Nhuận, TP.HCM',
     'OFFICE_WORKER', 30000000.00, 1, 'SOCIAL_INSURANCE_VSSID', '/uploads/docs/kh_009_vssid.jpg,/uploads/docs/kh_009_payslip.jpg',
     '/uploads/cccd/079199008899_front.jpg', '/uploads/cccd/079199008899_back.jpg', '/uploads/portraits/079199008899.jpg', 'ACTIVE', N'Khách hàng VIP, cầm cố trang sức kim cương có kiểm định GIA', 'staff_q1'),

    ('KH-HCM01-010', @branchQ1, N'Trịnh Công Minh', '079193007788', '2021-05-14', N'Cục Cảnh sát QLHC về TTXH',
     '0966555444', 'trinhcongminh@gmail.com', '1993-01-15', N'Nam', '79', '777',
     N'45 Tên Lửa, Phường Bình Trị Đông B, Quận Bình Tân, TP.HCM', N'45 Tên Lửa, Quận Bình Tân, TP.HCM',
     'FACTORY_WORKER', 15500000.00, 1, 'PAYSLIP_BANK_STATEMENT', '/uploads/docs/kh_010_payslip.jpg',
     '/uploads/cccd/079193007788_front.jpg', '/uploads/cccd/079193007788_back.jpg', '/uploads/portraits/079193007788.jpg', 'ACTIVE', N'Đã tất toán hợp đồng và chuộc xe đúng hạn', 'staff_q1'),

    ('KH-HCM01-011', @branchQ1, N'Ngô Đức Trí', '079190001234', '2022-10-08', N'Cục Cảnh sát QLHC về TTXH',
     '0912345678', 'ngoductri@gmail.com', '1996-05-20', N'Nam', '79', '774',
     N'230 Trần Phú, Phường 9, Quận 5, TP.HCM', N'230 Trần Phú, Quận 5, TP.HCM',
     'ONLINE_SELLER', 24000000.00, 0, 'BANK_COD_STATEMENT', '/uploads/docs/kh_011_cod_statement.jpg',
     '/uploads/cccd/079190001234_front.jpg', '/uploads/cccd/079190001234_back.jpg', '/uploads/portraits/079190001234.jpg', 'ACTIVE', N'Chủ shop thời trang online, đối soát COD hàng tuần', 'staff_q1'),

    -- Chi nhánh Quận Bình Thạnh (4 khách hàng)
    ('KH-HCM02-001', @branchBT, N'Phan Thanh Bình', '079191004512', '2022-03-11', N'Cục Cảnh sát QLHC về TTXH',
     '0909234567', 'phanthanhbinh@gmail.com', '1991-04-10', N'Nam', '79', '765',
     N'180 Xô Viết Nghệ Tĩnh, Phường 21, Quận Bình Thạnh, TP.HCM', N'180 Xô Viết Nghệ Tĩnh, Quận Bình Thạnh, TP.HCM',
     'OFFICE_WORKER', 26000000.00, 1, 'PAYSLIP_BANK_STATEMENT', '/uploads/docs/kh_bt01_payslip.jpg',
     '/uploads/cccd/079191004512_front.jpg', '/uploads/cccd/079191004512_back.jpg', '/uploads/portraits/079191004512.jpg', 'ACTIVE', N'Kỹ sư xây dựng, trả lãi định kỳ tốt', 'staff_bt'),

    ('KH-HCM02-002', @branchBT, N'Lý Ngọc Ánh', '079195008821', '2023-01-22', N'Cục Cảnh sát QLHC về TTXH',
     '0938345678', 'lyngocanh@gmail.com', '1995-10-18', N'Nữ', '79', '765',
     N'92 Bạch Đằng, Phường 24, Quận Bình Thạnh, TP.HCM', N'92 Bạch Đằng, Quận Bình Thạnh, TP.HCM',
     'SMALL_MERCHANT', 42000000.00, 0, 'BUSINESS_LICENSE_OR_STALL_LEASE', '/uploads/docs/kh_bt02_license.jpg',
     '/uploads/cccd/079195008821_front.jpg', '/uploads/cccd/079195008821_back.jpg', '/uploads/portraits/079195008821.jpg', 'ACTIVE', N'Kinh doanh mỹ phẩm tại chợ Bà Chiểu', 'staff_bt'),

    ('KH-HCM02-003', @branchBT, N'Đỗ Gia Huy', '079197003345', '2021-12-19', N'Cục Cảnh sát QLHC về TTXH',
     '0972456789', 'dogiahuy@gmail.com', '1997-07-07', N'Nam', '79', '769',
     N'56 Võ Văn Ngân, Phường Bình Thọ, TP. Thủ Đức, TP.HCM', N'56 Võ Văn Ngân, TP. Thủ Đức, TP.HCM',
     'TECH_DRIVER', 18000000.00, 0, 'DRIVER_APP_INCOME_SCREENSHOT', '/uploads/docs/kh_bt03_driver.jpg',
     '/uploads/cccd/079197003345_front.jpg', '/uploads/cccd/079197003345_back.jpg', '/uploads/portraits/079197003345.jpg', 'ACTIVE', N'Tài xế XanhSM, hồ sơ mới đang chờ Quản lý duyệt', 'staff_bt'),

    ('KH-HCM02-004', @branchBT, N'Huỳnh Thảo Vy', '079200007719', '2022-08-05', N'Cục Cảnh sát QLHC về TTXH',
     '0916567890', 'huynhthaovy@gmail.com', '2000-02-28', N'Nữ', '79', '764',
     N'340 Quang Trung, Phường 10, Quận Gò Vấp, TP.HCM', N'340 Quang Trung, Quận Gò Vấp, TP.HCM',
     'FREELANCER', 15000000.00, 0, 'RENTAL_CONTRACT', '/uploads/docs/kh_bt04_rental.jpg,/uploads/docs/kh_bt04_ct07.jpg',
     '/uploads/cccd/079200007719_front.jpg', '/uploads/cccd/079200007719_back.jpg', '/uploads/portraits/079200007719.jpg', 'ACTIVE', N'Thiết kế đồ họa tự do', 'staff_bt'),

    -- Chi nhánh Cầu Giấy - Hà Nội (4 khách hàng)
    ('KH-HN01-001', @branchCG, N'Nguyễn Tuấn Kiệt', '001189002341', '2021-07-10', N'Cục Cảnh sát QLHC về TTXH',
     '0988112233', 'nguyentuankiet@gmail.com', '1989-03-14', N'Nam', '01', '005',
     N'125 Xuân Thủy, Phường Dịch Vọng Hậu, Quận Cầu Giấy, Hà Nội', N'125 Xuân Thủy, Quận Cầu Giấy, Hà Nội',
     'OFFICE_WORKER', 35000000.00, 1, 'LABOR_CONTRACT', '/uploads/docs/kh_hn01_contract.jpg',
     '/uploads/cccd/001189002341_front.jpg', '/uploads/cccd/001189002341_back.jpg', '/uploads/portraits/001189002341.jpg', 'ACTIVE', N'Trưởng phòng CNTT tại tòa nhà Duy Tân, Cầu Giấy', 'staff_cg'),

    ('KH-HN01-002', @branchCG, N'Đinh Thị Lan Anh', '001193005672', '2022-05-18', N'Cục Cảnh sát QLHC về TTXH',
     '0915223344', 'dinhthilananh@gmail.com', '1993-09-22', N'Nữ', '01', '019',
     N'68 Nguyễn Cơ Thạch, Phường Mỹ Đình 1, Quận Nam Từ Liêm, Hà Nội', N'68 Nguyễn Cơ Thạch, Quận Nam Từ Liêm, Hà Nội',
     'ONLINE_SELLER', 48000000.00, 0, 'ECOMMERCE_REVENUE_SCREENSHOT', '/uploads/docs/kh_hn02_tiktokshop.jpg',
     '/uploads/cccd/001193005672_front.jpg', '/uploads/cccd/001193005672_back.jpg', '/uploads/portraits/001193005672.jpg', 'ACTIVE', N'Chủ kho hàng gia dụng online tại Mỹ Đình', 'staff_cg'),

    ('KH-HN01-003', @branchCG, N'Hoàng Văn Nam', '001196008912', '2022-11-25', N'Cục Cảnh sát QLHC về TTXH',
     '0979334455', 'hoangvannam@gmail.com', '1996-12-05', N'Nam', '01', '021',
     N'42 Phạm Văn Đồng, Phường Cổ Nhuế 1, Quận Bắc Từ Liêm, Hà Nội', N'42 Phạm Văn Đồng, Quận Bắc Từ Liêm, Hà Nội',
     'FACTORY_WORKER', 16000000.00, 1, 'SOCIAL_INSURANCE_VSSID', '/uploads/docs/kh_hn03_vssid.jpg',
     '/uploads/cccd/001196008912_front.jpg', '/uploads/cccd/001196008912_back.jpg', '/uploads/portraits/001196008912.jpg', 'ACTIVE', N'Kỹ thuật viên nhà máy cơ khí Bắc Thăng Long', 'staff_cg'),

    ('KH-HN01-004', @branchCG, N'Phạm Thu Trang', '001192004456', '2023-03-04', N'Cục Cảnh sát QLHC về TTXH',
     '0942445566', 'phamthutrang@gmail.com', '1992-06-19', N'Nữ', '01', '006',
     N'150 Chùa Bộc, Phường Quang Trung, Quận Đống Đa, Hà Nội', N'150 Chùa Bộc, Quận Đống Đa, Hà Nội',
     'SMALL_MERCHANT', 55000000.00, 1, 'SHOP_PHOTO_AND_CASHFLOW', '/uploads/docs/kh_hn04_shop.jpg',
     '/uploads/cccd/001192004456_front.jpg', '/uploads/cccd/001192004456_back.jpg', '/uploads/portraits/001192004456.jpg', 'ACTIVE', N'Chủ boutique thời trang phố Chùa Bộc', 'staff_cg'),

    -- Chi nhánh Đà Nẵng & Cần Thơ (3 khách hàng)
    ('KH-DN01-001', @branchDN, N'Trương Minh Nhật', '048190003211', '2022-02-14', N'Cục Cảnh sát QLHC về TTXH',
     '0905667788', 'truongminhnhat@gmail.com', '1990-08-11', N'Nam', '48', '492',
     N'112 Bạch Đằng, Phường Hải Châu I, Quận Hải Châu, Đà Nẵng', N'112 Bạch Đằng, Quận Hải Châu, Đà Nẵng',
     'OFFICE_WORKER', 25000000.00, 1, 'PAYSLIP_BANK_STATEMENT', '/uploads/docs/kh_dn01_payslip.jpg',
     '/uploads/cccd/048190003211_front.jpg', '/uploads/cccd/048190003211_back.jpg', '/uploads/portraits/048190003211.jpg', 'ACTIVE', N'Quản lý khách sạn ven sông Hàn', 'staff_dn'),

    ('KH-DN01-002', @branchDN, N'Lê Thị Mỹ Duyên', '048194006543', '2022-09-09', N'Cục Cảnh sát QLHC về TTXH',
     '0935778899', 'lethimyduyen@gmail.com', '1994-05-16', N'Nữ', '48', '491',
     N'245 Điện Biên Phủ, Phường Chính Gián, Quận Thanh Khê, Đà Nẵng', N'245 Điện Biên Phủ, Quận Thanh Khê, Đà Nẵng',
     'SMALL_MERCHANT', 38000000.00, 0, 'BUSINESS_LICENSE_OR_STALL_LEASE', '/uploads/docs/kh_dn02_license.jpg',
     '/uploads/cccd/048194006543_front.jpg', '/uploads/cccd/048194006543_back.jpg', '/uploads/portraits/048194006543.jpg', 'ACTIVE', N'Chủ cửa hàng đặc sản miền Trung', 'staff_dn'),

    ('KH-CT01-001', @branchCT, N'Lâm Quốc Thịnh', '092188009876', '2021-10-20', N'Cục Cảnh sát QLHC về TTXH',
     '0919889900', 'lamquocthinh@gmail.com', '1988-11-29', N'Nam', '92', '916',
     N'78 Nguyễn Trãi, Phường Tân An, Quận Ninh Kiều, Cần Thơ', N'78 Nguyễn Trãi, Quận Ninh Kiều, Cần Thơ',
     'SMALL_MERCHANT', 50000000.00, 1, 'BUSINESS_LICENSE_OR_STALL_LEASE', '/uploads/docs/kh_ct01_license.jpg',
     '/uploads/cccd/092188009876_front.jpg', '/uploads/cccd/092188009876_back.jpg', '/uploads/portraits/092188009876.jpg', 'ACTIVE', N'Chủ vựa trái cây xuất khẩu Ninh Kiều', 'staff_ct');
END

-- =========================================================================
-- STEP 4: SEED COLLATERALS (22 TÀI SẢN CẦM CỐ THEO CHI NHÁNH)
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0119')
BEGIN
    INSERT INTO dbo.collaterals (
        collateral_code, customer_id, branch_id, [name], category, brand, model,
        serial_or_frame_number, license_plate, condition_description,
        appraised_value, max_ltv_percent, storage_location, image_urls, status, created_by
    )
    VALUES
    -- Chi nhánh Quận 1 (11 tài sản khớp với HD-2026-0119 .. HD-2026-0129)
    ('TS-2026-0129', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-001'), @branchQ1,
     N'Yamaha Grande Hybrid 2024', 'MOTORBIKE', N'Yamaha', N'Grande Hybrid 2024',
     'RLCSEG100R0012901', '59-L2 678.90', N'Xe mới 98%, đầy đủ cà vẹt bản gốc và 2 chìa khóa Smartkey',
     40000000.00, 90.00, N'Kho bãi K1 - Ô 01', '/uploads/collaterals/ts_0129_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0128', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-002'), @branchQ1,
     N'Honda SH 150i ABS (2023)', 'MOTORBIKE', N'Honda', N'SH 150i ABS',
     'RLHJF9000P0012888', '59-P1 988.22', N'Màu đen bóng, ODO 6.500km, nguyên zin 100%, giữ đăng ký gốc',
     55000000.00, 90.00, N'Kho bãi K1 - Ô 02', '/uploads/collaterals/ts_0128_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0127', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-003'), @branchQ1,
     N'Dây chuyền vàng 9999 (3 chỉ)', 'GOLD_JEWELRY', N'SJC', N'Vàng 24K 9999',
     'SJC-24K-3CHI-0127', NULL, N'Vàng 9999 nguyên chất 3.0 chỉ, đã kiểm định quang phổ kèm hóa đơn SJC',
     21000000.00, 90.00, N'Két an toàn Két-A03', '/uploads/collaterals/ts_0127_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0126', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-004'), @branchQ1,
     N'Toyota Vios 1.5G (2021)', 'CAR', N'Toyota', N'Vios 1.5G CVT',
     'RL4B29F31M0056789', '51H-678.90', N'Xe gia đình không kinh doanh, ODO 32.000km, bảo hiểm thân vỏ còn hạn',
     280000000.00, 80.00, N'Bãi đỗ B2 - Khu Ô tô', '/uploads/collaterals/ts_0126_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0125', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-005'), @branchQ1,
     N'iPhone 15 Pro Max 256GB', 'PHONE', N'Apple', N'iPhone 15 Pro Max 256GB Titan Tự Nhiên',
     '358921004899123', NULL, N'Bản VN/A fullbox, pin 96%, đã thoát iCloud hoàn toàn và niêm phong túi chống ẩm',
     22000000.00, 70.00, N'Két ĐT-05', '/uploads/collaterals/ts_0125_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0124', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-006'), @branchQ1,
     N'Honda Vision Smartkey (2022)', 'MOTORBIKE', N'Honda', N'Vision Cá Tính 2022',
     'RLHJK0300N0045678', '29-B1 456.78', N'Màu xanh nhám, máy êm, không trầy xước lớn, kèm cà vẹt gốc chính chủ',
     20000000.00, 80.00, N'Kho bãi K2 - Ô 08', '/uploads/collaterals/ts_0124_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0123', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-007'), @branchQ1,
     N'MacBook Pro 16" M3 Max 32GB', 'LAPTOP', N'Apple', N'MacBook Pro 16-inch M3 Max',
     'C02F93M3MAX8899', NULL, N'RAM 32GB, SSD 1TB, sạc 28 lần, kèm bộ sạc MagSafe 140W zin',
     50000000.00, 70.00, N'Tủ Laptop-02', '/uploads/collaterals/ts_0123_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0122', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-008'), @branchQ1,
     N'Rolex Submariner Date 126610LN', 'WATCH', N'Rolex', N'Submariner Date 41mm Oystersteel',
     'RLX-126610LN-7788', NULL, N'Fullbox thẻ bảo hành toàn cầu 2022, đã giám định máy Caliber 3235 chuẩn hãng',
     280000000.00, 55.00, N'Két VIP-01', '/uploads/collaterals/ts_0122_1.jpg', 'WAITING_LIQUIDATION', 'staff_q1'),

    ('TS-2026-0121', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-009'), @branchQ1,
     N'Nhẫn kim cương PNJ 5.4 ly GIA', 'GOLD_JEWELRY', N'PNJ', N'Vỏ vàng trắng 18K đính Kim cương 5.4mm',
     'GIA-6421987654', NULL, N'Nước E, độ sạch VVS1, đầy đủ giấy kiểm định GIA và hóa đơn PNJ',
     48000000.00, 92.00, N'Két an toàn Két-B02', '/uploads/collaterals/ts_0121_1.jpg', 'IN_VAULT', 'staff_q1'),

    ('TS-2026-0120', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-010'), @branchQ1,
     N'Yamaha Exciter 155 VVA (2022)', 'MOTORBIKE', N'Yamaha', N'Exciter 155 VVA GP',
     'RLCEG1550N0033412', '60-B8 334.12', N'Xe côn tay bản GP xanh bạc, khách đã tất toán và nhận lại tài sản',
     28000000.00, 80.00, N'Đã xuất kho trả khách', '/uploads/collaterals/ts_0120_1.jpg', 'REDEEMED', 'staff_q1'),

    ('TS-2026-0119', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-011'), @branchQ1,
     N'iPad Pro 12.9" M2 128GB Wifi', 'PHONE', N'Apple', N'iPad Pro 12.9-inch M2',
     'DLXF89M2PRO128', NULL, N'Kèm bút Apple Pencil 2 và bàn phím Magic Keyboard, ngoại hình 99%',
     18000000.00, 70.00, N'Két ĐT-08', '/uploads/collaterals/ts_0119_1.jpg', 'IN_VAULT', 'staff_q1'),

    -- Chi nhánh Quận Bình Thạnh (4 tài sản: TS-2026-0201 .. TS-2026-0204)
    ('TS-2026-0201', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-001'), @branchBT,
     N'Honda AirBlade 160 ABS (2024)', 'MOTORBIKE', N'Honda', N'AirBlade 160 Special Edition',
     'RLHJK1600R0091234', '59-S3 412.89', N'Xe chính chủ Quận Bình Thạnh, ODO 3.100km, mới 99%',
     42000000.00, 90.00, N'Kho BT-K1 - Ô 03', '/uploads/collaterals/ts_0201_1.jpg', 'IN_VAULT', 'staff_bt'),

    ('TS-2026-0202', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-002'), @branchBT,
     N'Lắc tay vàng 24K SJC (5 chỉ)', 'GOLD_JEWELRY', N'SJC', N'Lắc vàng 9999 5 chỉ',
     'SJC-24K-5CHI-0202', NULL, N'Vàng 9999 đủ tuổi, trọng lượng 5.0 chỉ, niêm phong túi Két BT-A01',
     36000000.00, 90.00, N'Két BT-A01', '/uploads/collaterals/ts_0202_1.jpg', 'IN_VAULT', 'staff_bt'),

    ('TS-2026-0203', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-003'), @branchBT,
     N'VinFast Feliz S (2024)', 'MOTORBIKE', N'VinFast', N'Feliz S Pin LFP',
     'RLVFE2024R0055123', '59-X3 778.12', N'Xe máy điện VinFast kèm bộ sạc zin 1000W, giấy tờ chính chủ',
     25000000.00, 70.00, N'Kho BT-K1 - Ô 09', '/uploads/collaterals/ts_0203_1.jpg', 'IN_VAULT', 'staff_bt'),

    ('TS-2026-0204', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-004'), @branchBT,
     N'Dell XPS 15 9530 Core i7-13700H', 'LAPTOP', N'Dell', N'XPS 15 9530 OLED',
     'DLLXPS9530VN779', NULL, N'RAM 32GB, RTX 4060, màn hình OLED 3.5K, đã thanh lý thu hồi vốn',
     32000000.00, 60.00, N'Đã xuất thanh lý', '/uploads/collaterals/ts_0204_1.jpg', 'LIQUIDATED', 'staff_bt'),

    -- Chi nhánh Cầu Giấy - Hà Nội (4 tài sản: TS-2026-0301 .. TS-2026-0304)
    ('TS-2026-0301', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-001'), @branchCG,
     N'Mazda 3 1.5L Luxury (2022)', 'CAR', N'Mazda', N'Mazda 3 Sedan 1.5L Luxury',
     'MM8BM22L0N0088123', '30H-889.15', N'Màu đỏ Soul Red, ODO 24.000km, đăng kiểm và bảo hiểm đầy đủ',
     450000000.00, 85.00, N'Bãi xe CG-B1', '/uploads/collaterals/ts_0301_1.jpg', 'IN_VAULT', 'staff_cg'),

    ('TS-2026-0302', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-002'), @branchCG,
     N'Miếng vàng SJC 9999 (1 lượng)', 'GOLD_JEWELRY', N'SJC', N'Vàng miếng SJC 1 lượng',
     'SJC-1LUONG-883920', NULL, N'Bao bì chống giả SJC nguyên vẹn, kiểm tra máy quang phổ đạt 99.99%',
     80000000.00, 88.00, N'Két CG-VIP01', '/uploads/collaterals/ts_0302_1.jpg', 'IN_VAULT', 'staff_cg'),

    ('TS-2026-0303', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-003'), @branchCG,
     N'Honda Winner X V3 ABS (2023)', 'MOTORBIKE', N'Honda', N'Winner X 150 ABS',
     'RLHKC3800P0034511', '29-P1 662.34', N'Xe côn tay thể thao, máy nguyên bản, giấy đăng ký chính chủ',
     26000000.00, 80.00, N'Kho CG-K1 - Ô 04', '/uploads/collaterals/ts_0303_1.jpg', 'IN_VAULT', 'staff_cg'),

    ('TS-2026-0304', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-004'), @branchCG,
     N'Omega Seamaster Aqua Terra 150M', 'WATCH', N'Omega', N'Seamaster Aqua Terra Co-Axial Master',
     'OMG-8900-445566', NULL, N'Đồng hồ Thụy Sĩ chính hãng, hộp gỗ + 3 thẻ Pictograms/Master Chronometer',
     110000000.00, 75.00, N'Két CG-VIP02', '/uploads/collaterals/ts_0304_1.jpg', 'IN_VAULT', 'staff_cg'),

    -- Chi nhánh Đà Nẵng & Cần Thơ (3 tài sản: TS-2026-0401, TS-2026-0402, TS-2026-0501)
    ('TS-2026-0401', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-001'), @branchDN,
     N'Honda SH Mode 125cc ABS (2023)', 'MOTORBIKE', N'Honda', N'SH Mode 125 Đặc Biệt',
     'RLHJK0100P0077123', '43-C1 812.45', N'Màu bạc nhám, chính chủ Hải Châu, Đà Nẵng',
     48000000.00, 90.00, N'Kho DN-K1 - Ô 01', '/uploads/collaterals/ts_0401_1.jpg', 'IN_VAULT', 'staff_dn'),

    ('TS-2026-0402', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-002'), @branchDN,
     N'Bộ trang sức vàng 18K DOJI (8 chỉ)', 'GOLD_JEWELRY', N'DOJI', N'Bộ dây chuyền & lắc tay 18K',
     'DOJI-18K-0402', NULL, N'Kèm giấy đảm bảo vàng DOJI Đà Nẵng, niêm phong két an toàn',
     40000000.00, 90.00, N'Két DN-A01', '/uploads/collaterals/ts_0402_1.jpg', 'IN_VAULT', 'staff_dn'),

    ('TS-2026-0501', (SELECT id FROM dbo.customers WHERE customer_code = 'KH-CT01-001'), @branchCT,
     N'Ford Ranger Wildtrak 2.0L Bi-Turbo (2022)', 'CAR', N'Ford', N'Ranger Wildtrak 4x4 AT',
     'MPBUMFF60NX099887', '65A-289.99', N'Xe bán tải màu cam, ODO 28.000km, giấy tờ chính chủ Quận Ninh Kiều',
     550000000.00, 80.00, N'Bãi xe CT-B1', '/uploads/collaterals/ts_0501_1.jpg', 'IN_VAULT', 'staff_ct');
END

-- =========================================================================
-- STEP 5: SEED PAWN CONTRACTS (22 HỢP ĐỒNG CẦM CỐ ĐA TRẠNG THÁI)
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0119')
BEGIN
    INSERT INTO dbo.pawn_contracts (
        contract_code, branch_id, customer_id, collateral_id,
        principal_amount, remaining_principal, applied_ltv_percent,
        interest_type, interest_rate, storage_fee_rate,
        loan_term_days, payment_cycle_days,
        start_date, maturity_date, next_interest_due_date, closed_date,
        total_interest_paid, status, approved_by, approved_at, approval_notes, notes, created_by
    )
    VALUES
    -- 1. HD-2026-0129: PENDING_APPROVAL (Chờ Quản lý Quận 1 duyệt giải ngân)
    ('HD-2026-0129', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-001'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0129'),
     30000000.00, 30000000.00, 75.00,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-11-10', '2026-12-10', '2026-11-20', NULL,
     0.00, 'PENDING_APPROVAL', NULL, NULL, NULL,
     N'Hồ sơ mới lập tại quầy Quận 1, đã kiểm tra bảng lương & VssID, trình Quản lý duyệt giải ngân', 'staff_q1'),

    -- 2. HD-2026-0128: ACTIVE (Đến hạn đóng lãi)
    ('HD-2026-0128', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-002'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0128'),
     45000000.00, 45000000.00, 81.82,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-20', '2026-11-19', '2026-11-09', NULL,
     900000.00, 'ACTIVE', @mgrQ1, '2026-10-20 09:30:00', N'Hồ sơ hợp lệ, đồng ý giải ngân 45 triệu',
     N'Đã thu lãi kỳ 1 (900.000 ₫), đến hạn đóng lãi kỳ 2', 'staff_q1'),

    -- 3. HD-2026-0127: ACTIVE (Cầm vàng 9999)
    ('HD-2026-0127', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-003'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0127'),
     18000000.00, 18000000.00, 85.71,
     'DAILY_PER_MILLION', 1500.0000, 0.0000, 30, 15,
     '2026-10-30', '2026-11-29', '2026-11-14', NULL,
     0.00, 'ACTIVE', @mgrQ1, '2026-10-30 10:15:00', N'Đã kiểm định vàng SJC 9999, duyệt giải ngân',
     N'Cầm vàng 24K lãi suất ưu đãi 1.500đ/triệu/ngày', 'staff_q1'),

    -- 4. HD-2026-0126: EXTENDED (Cầm ô tô Toyota Vios - Đã gia hạn)
    ('HD-2026-0126', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-004'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0126'),
     220000000.00, 220000000.00, 78.57,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 60, 30,
     '2026-09-18', '2026-12-17', '2026-11-17', NULL,
     13200000.00, 'EXTENDED', @mgrQ1, '2026-09-18 14:00:00', N'Đã thẩm định xe và giấy tờ gốc, duyệt giải ngân 220 triệu',
     N'Khách đã đóng đủ lãi 30 ngày đầu (13.200.000 ₫) và gia hạn kỳ mới', 'staff_q1'),

    -- 5. HD-2026-0125: OVERDUE (iPhone 15 Pro Max - Quá hạn 4 ngày)
    ('HD-2026-0125', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-005'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0125'),
     15000000.00, 15000000.00, 68.18,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-16', '2026-11-15', '2026-11-05', NULL,
     300000.00, 'OVERDUE', @mgrQ1, '2026-10-16 11:20:00', N'Duyệt giải ngân theo hạn mức tài xế công nghệ',
     N'Trễ hạn đóng lãi kỳ 2 từ ngày 05/11/2026, đã gọi nhắc lịch', 'staff_q1'),

    -- 6. HD-2026-0124: ACTIVE (Honda Vision - Đến hạn hôm nay)
    ('HD-2026-0124', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-006'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0124'),
     16000000.00, 16000000.00, 80.00,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-30', '2026-11-29', '2026-11-09', NULL,
     0.00, 'ACTIVE', @mgrQ1, '2026-10-30 15:40:00', N'Công nhân KCX Tân Thuận có VssID, duyệt LTV 80%',
     N'Đến hạn thu lãi kỳ đầu tiên', 'staff_q1'),

    -- 7. HD-2026-0123: OVERDUE (MacBook Pro M3 Max - Quá hạn 12 ngày)
    ('HD-2026-0123', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-007'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0123'),
     35000000.00, 35000000.00, 70.00,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-08', '2026-11-07', '2026-10-28', NULL,
     700000.00, 'OVERDUE', @mgrQ1, '2026-10-08 16:10:00', N'Duyệt giải ngân 35 triệu (LTV 70% nhóm Online Seller)',
     N'Quá hạn 12 ngày, khách hẹn cuối tuần thanh toán cả gốc và lãi', 'staff_q1'),

    -- 8. HD-2026-0122: OVERDUE / Chờ thanh lý (Rolex Submariner - Tài sản WAITING_LIQUIDATION)
    ('HD-2026-0122', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-008'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0122'),
     150000000.00, 150000000.00, 53.57,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 15,
     '2026-09-19', '2026-10-19', '2026-10-04', NULL,
     0.00, 'OVERDUE', @mgrQ1, '2026-09-19 11:00:00', N'Lao động tự do có CT07, áp dụng LTV an toàn 53.57%',
     N'Quá hạn trên 35 ngày, tài sản đã chuyển sang danh mục Chờ thanh lý (WAITING_LIQUIDATION)', 'staff_q1'),

    -- 9. HD-2026-0121: ACTIVE (Nhẫn kim cương PNJ GIA)
    ('HD-2026-0121', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-009'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0121'),
     40000000.00, 40000000.00, 83.33,
     'DAILY_PER_MILLION', 1500.0000, 0.0000, 30, 10,
     '2026-10-27', '2026-11-26', '2026-11-16', NULL,
     600000.00, 'ACTIVE', @mgrQ1, '2026-10-27 10:00:00', N'Khách văn phòng có VssID + giấy GIA, duyệt giải ngân',
     N'Đã đóng lãi kỳ 1 đúng hạn (600.000 ₫)', 'staff_q1'),

    -- 10. HD-2026-0120: REDEEMED (Yamaha Exciter 155 - Đã chuộc đồ)
    ('HD-2026-0120', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-010'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0120'),
     22000000.00, 0.00, 78.57,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-09', '2026-11-08', '2026-11-08', '2026-11-08',
     1320000.00, 'REDEEMED', @mgrQ1, '2026-10-09 09:15:00', N'Duyệt giải ngân 22 triệu',
     N'Khách đã tất toán toàn bộ gốc 22.000.000 ₫ + lãi và nhận lại xe ngày 08/11/2026', 'staff_q1'),

    -- 11. HD-2026-0119: ACTIVE (iPad Pro M2)
    ('HD-2026-0119', @branchQ1,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-011'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0119'),
     12000000.00, 12000000.00, 66.67,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-23', '2026-11-22', '2026-11-12', NULL,
     240000.00, 'ACTIVE', @mgrQ1, '2026-10-23 13:45:00', N'Duyệt giải ngân 12 triệu',
     N'Đã thu lãi kỳ 1 (240.000 ₫)', 'staff_q1'),

    -- Chi nhánh Quận Bình Thạnh (4 hợp đồng: HD-2026-0201 .. HD-2026-0204)
    ('HD-2026-0201', @branchBT,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-001'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0201'),
     35000000.00, 30000000.00, 83.33,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-25', '2026-11-24', '2026-11-14', NULL,
     700000.00, 'ACTIVE', @mgrBT, '2026-10-25 09:20:00', N'Quản lý Bình Thạnh duyệt giải ngân 35 triệu',
     N'Khách đã đóng lãi kỳ 1 (700.000 ₫) và trả bớt gốc 5.000.000 ₫, dư nợ còn 30.000.000 ₫', 'staff_bt'),

    ('HD-2026-0202', @branchBT,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-002'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0202'),
     30000000.00, 30000000.00, 83.33,
     'DAILY_PER_MILLION', 1500.0000, 0.0000, 30, 15,
     '2026-11-01', '2026-12-01', '2026-11-16', NULL,
     0.00, 'ACTIVE', @mgrBT, '2026-11-01 10:30:00', N'Duyệt cầm 5 chỉ vàng 24K SJC',
     N'Hợp đồng đang hoạt động tốt tại Chi nhánh Bình Thạnh', 'staff_bt'),

    ('HD-2026-0203', @branchBT,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-003'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0203'),
     16000000.00, 16000000.00, 64.00,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-11-10', '2026-12-10', '2026-11-20', NULL,
     0.00, 'PENDING_APPROVAL', NULL, NULL, NULL,
     N'Hồ sơ mới lập tại Chi nhánh Bình Thạnh, chờ Quản lý Bình Thạnh duyệt', 'staff_bt'),

    ('HD-2026-0204', @branchBT,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-004'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0204'),
     18000000.00, 0.00, 56.25,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-09-10', '2026-10-10', '2026-10-10', '2026-11-05',
     360000.00, 'LIQUIDATED', @mgrBT, '2026-09-10 14:15:00', N'Duyệt giải ngân 18 triệu',
     N'Quá hạn lâu ngày không chuộc, cửa hàng đã thanh lý thu hồi 21.500.000 ₫ ngày 05/11/2026', 'staff_bt'),

    -- Chi nhánh Cầu Giấy - Hà Nội (4 hợp đồng: HD-2026-0301 .. HD-2026-0304)
    ('HD-2026-0301', @branchCG,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-001'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0301'),
     320000000.00, 320000000.00, 71.11,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 60, 15,
     '2026-10-15', '2026-12-14', '2026-11-14', NULL,
     9600000.00, 'ACTIVE', @mgrCG, '2026-10-15 10:00:00', N'Quản lý Cầu Giấy duyệt giải ngân 320 triệu cầm xe Mazda 3',
     N'Đã thu lãi kỳ 1 (9.600.000 ₫), xe lưu bãi an toàn', 'staff_cg'),

    ('HD-2026-0302', @branchCG,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-002'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0302'),
     65000000.00, 65000000.00, 81.25,
     'DAILY_PER_MILLION', 1500.0000, 0.0000, 30, 15,
     '2026-10-28', '2026-11-27', '2026-11-12', NULL,
     0.00, 'ACTIVE', @mgrCG, '2026-10-28 11:30:00', N'Duyệt cầm 1 lượng vàng SJC 9999',
     N'Khách vay bổ sung vốn nhập hàng TMĐT mùa cuối năm', 'staff_cg'),

    ('HD-2026-0303', @branchCG,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-003'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0303'),
     18000000.00, 18000000.00, 69.23,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-18', '2026-11-17', '2026-11-07', NULL,
     360000.00, 'OVERDUE', @mgrCG, '2026-10-18 15:00:00', N'Duyệt giải ngân 18 triệu',
     N'Trễ hạn đóng lãi kỳ 2 tại Chi nhánh Cầu Giấy', 'staff_cg'),

    ('HD-2026-0304', @branchCG,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-004'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0304'),
     75000000.00, 75000000.00, 68.18,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-11-10', '2026-12-10', '2026-11-20', NULL,
     0.00, 'PENDING_APPROVAL', NULL, NULL, NULL,
     N'Chờ Quản lý Chi nhánh Cầu Giấy phê duyệt giải ngân 75 triệu', 'staff_cg'),

    -- Chi nhánh Đà Nẵng & Cần Thơ (3 hợp đồng: HD-2026-0401, HD-2026-0402, HD-2026-0501)
    ('HD-2026-0401', @branchDN,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-001'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0401'),
     38000000.00, 38000000.00, 79.17,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 30, 10,
     '2026-10-26', '2026-11-25', '2026-11-15', NULL,
     760000.00, 'ACTIVE', @mgrDN, '2026-10-26 09:00:00', N'Quản lý Đà Nẵng duyệt giải ngân 38 triệu',
     N'Đã đóng lãi kỳ 1 (760.000 ₫)', 'staff_dn'),

    ('HD-2026-0402', @branchDN,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-002'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0402'),
     32000000.00, 32000000.00, 80.00,
     'DAILY_PER_MILLION', 1500.0000, 0.0000, 30, 15,
     '2026-11-02', '2026-12-02', '2026-11-17', NULL,
     0.00, 'ACTIVE', @mgrDN, '2026-11-02 14:20:00', N'Duyệt giải ngân 32 triệu cầm trang sức DOJI',
     N'Đang vay trong hạn tại Chi nhánh Hải Châu - Đà Nẵng', 'staff_dn'),

    ('HD-2026-0501', @branchCT,
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-CT01-001'),
     (SELECT id FROM dbo.collaterals WHERE collateral_code = 'TS-2026-0501'),
     380000000.00, 380000000.00, 69.09,
     'DAILY_PER_MILLION', 2000.0000, 0.0000, 60, 15,
     '2026-10-20', '2026-12-19', '2026-11-19', NULL,
     11400000.00, 'ACTIVE', @mgrCT, '2026-10-20 10:10:00', N'Quản lý Cần Thơ duyệt giải ngân 380 triệu',
     N'Đã thu lãi kỳ 1 (11.400.000 ₫) tại Chi nhánh Ninh Kiều - Cần Thơ', 'staff_ct');
END

-- =========================================================================
-- STEP 6: SEED INTEREST PAYMENTS (LỊCH SỬ ĐÓNG LÃI, TRẢ GỐC, CHUỘC ĐỒ, THANH LÝ)
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.interest_payments WHERE payment_code = 'PT-2026-0001')
BEGIN
    INSERT INTO dbo.interest_payments (
        payment_code, contract_id, payment_type, from_date, to_date, days_count,
        interest_amount, storage_fee_amount, penalty_amount, principal_paid_amount, total_amount,
        payment_date, payment_method, notes, created_by
    )
    VALUES
    -- Kỳ lãi 1 của HD-2026-0126 (Toyota Vios - Quận 1)
    ('PT-2026-0001', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0126'),
     'INTEREST_PAYMENT', '2026-09-18', '2026-10-18', 30,
     13200000.00, 0.00, 0.00, 0.00, 13200000.00,
     '2026-10-18 10:15:00', 'BANK_TRANSFER', N'Thu lãi 30 ngày đầu và gia hạn hợp đồng HD-2026-0126', 'staff_q1'),

    -- Kỳ lãi 1 của HD-2026-0123 (MacBook Pro M3 - Quận 1)
    ('PT-2026-0002', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0123'),
     'INTEREST_PAYMENT', '2026-10-08', '2026-10-18', 10,
     700000.00, 0.00, 0.00, 0.00, 700000.00,
     '2026-10-18 15:20:00', 'CASH', N'Thu lãi kỳ 1 (10 ngày) hợp đồng HD-2026-0123', 'staff_q1'),

    -- Kỳ lãi 1 của HD-2026-0120 (Yamaha Exciter - Quận 1)
    ('PT-2026-0003', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0120'),
     'INTEREST_PAYMENT', '2026-10-09', '2026-10-29', 20,
     880000.00, 0.00, 0.00, 0.00, 880000.00,
     '2026-10-29 09:40:00', 'CASH', N'Thu lãi 20 ngày đầu hợp đồng HD-2026-0120', 'staff_q1'),

    -- Kỳ lãi 1 của HD-2026-0125 (iPhone 15 Pro Max - Quận 1)
    ('PT-2026-0004', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0125'),
     'INTEREST_PAYMENT', '2026-10-16', '2026-10-26', 10,
     300000.00, 0.00, 0.00, 0.00, 300000.00,
     '2026-10-26 11:05:00', 'CASH', N'Thu lãi kỳ 1 hợp đồng HD-2026-0125', 'staff_q1'),

    -- Kỳ lãi 1 của HD-2026-0128 (Honda SH 150i - Quận 1)
    ('PT-2026-0005', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0128'),
     'INTEREST_PAYMENT', '2026-10-20', '2026-10-30', 10,
     900000.00, 0.00, 0.00, 0.00, 900000.00,
     '2026-10-30 14:00:00', 'BANK_TRANSFER', N'Thu lãi kỳ 1 hợp đồng HD-2026-0128', 'staff_q1'),

    -- Kỳ lãi 1 của HD-2026-0119 (iPad Pro M2 - Quận 1)
    ('PT-2026-0006', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0119'),
     'INTEREST_PAYMENT', '2026-10-23', '2026-11-02', 10,
     240000.00, 0.00, 0.00, 0.00, 240000.00,
     '2026-11-02 16:30:00', 'CASH', N'Thu lãi kỳ 1 hợp đồng HD-2026-0119', 'staff_q1'),

    -- Kỳ lãi 1 của HD-2026-0121 (Nhẫn kim cương PNJ - Quận 1)
    ('PT-2026-0007', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0121'),
     'INTEREST_PAYMENT', '2026-10-27', '2026-11-06', 10,
     600000.00, 0.00, 0.00, 0.00, 600000.00,
     '2026-11-06 10:45:00', 'BANK_TRANSFER', N'Thu lãi kỳ 1 hợp đồng HD-2026-0121', 'staff_q1'),

    -- Tất toán & Chuộc đồ HD-2026-0120 (Yamaha Exciter - Quận 1)
    ('PT-2026-0008', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0120'),
     'REDEMPTION', '2026-10-29', '2026-11-08', 10,
     440000.00, 0.00, 0.00, 22000000.00, 22440000.00,
     '2026-11-08 11:15:00', 'BANK_TRANSFER', N'Tất toán gốc 22.000.000 ₫ + lãi 440.000 ₫, xuất kho trả xe Yamaha Exciter', 'staff_q1'),

    -- Đóng lãi + Trả bớt gốc HD-2026-0201 (Chi nhánh Bình Thạnh)
    ('PT-2026-0009', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0201'),
     'PARTIAL_PRINCIPAL', '2026-10-25', '2026-11-04', 10,
     700000.00, 0.00, 0.00, 5000000.00, 5700000.00,
     '2026-11-04 09:50:00', 'BANK_TRANSFER', N'Thu lãi kỳ 1 (700.000 ₫) + khách trả bớt gốc 5.000.000 ₫', 'staff_bt'),

    -- Thanh lý tài sản HD-2026-0204 (Dell XPS 15 - Chi nhánh Bình Thạnh)
    ('PT-2026-0010', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0204'),
     'LIQUIDATION', '2026-09-10', '2026-11-05', 56,
     360000.00, 0.00, 0.00, 18000000.00, 21500000.00,
     '2026-11-05 15:00:00', 'CASH', N'Thanh lý Laptop Dell XPS 15 quá hạn thu hồi vốn & lãi', 'manager_bt'),

    -- Kỳ lãi 1 của HD-2026-0301 (Mazda 3 - Chi nhánh Cầu Giấy)
    ('PT-2026-0011', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0301'),
     'INTEREST_PAYMENT', '2026-10-15', '2026-10-30', 15,
     9600000.00, 0.00, 0.00, 0.00, 9600000.00,
     '2026-10-30 10:20:00', 'BANK_TRANSFER', N'Thu lãi kỳ 1 (15 ngày) hợp đồng cầm ô tô Mazda 3 HD-2026-0301', 'staff_cg'),

    -- Kỳ lãi 1 của HD-2026-0303 (Winner X - Chi nhánh Cầu Giấy)
    ('PT-2026-0012', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0303'),
     'INTEREST_PAYMENT', '2026-10-18', '2026-10-28', 10,
     360000.00, 0.00, 0.00, 0.00, 360000.00,
     '2026-10-28 16:00:00', 'CASH', N'Thu lãi kỳ 1 hợp đồng HD-2026-0303', 'staff_cg'),

    -- Kỳ lãi 1 của HD-2026-0401 (SH Mode - Chi nhánh Đà Nẵng)
    ('PT-2026-0013', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0401'),
     'INTEREST_PAYMENT', '2026-10-26', '2026-11-05', 10,
     760000.00, 0.00, 0.00, 0.00, 760000.00,
     '2026-11-05 11:00:00', 'CASH', N'Thu lãi kỳ 1 hợp đồng HD-2026-0401 tại Đà Nẵng', 'staff_dn'),

    -- Kỳ lãi 1 của HD-2026-0501 (Ford Ranger - Chi nhánh Cần Thơ)
    ('PT-2026-0014', (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0501'),
     'INTEREST_PAYMENT', '2026-10-20', '2026-11-04', 15,
     11400000.00, 0.00, 0.00, 0.00, 11400000.00,
     '2026-11-04 14:30:00', 'BANK_TRANSFER', N'Thu lãi kỳ 1 (15 ngày) hợp đồng HD-2026-0501 tại Cần Thơ', 'staff_ct');
END

-- =========================================================================
-- STEP 7: SEED CASHBOOK TRANSACTIONS (SỔ QUỸ DÒNG TIỀN THU/CHI THEO CHI NHÁNH)
-- =========================================================================
IF NOT EXISTS (SELECT 1 FROM dbo.cashbook_transactions WHERE transaction_code = 'SQ-HCM01-0001')
BEGIN
    INSERT INTO dbo.cashbook_transactions (
        transaction_code, branch_id, transaction_type, category, amount, balance_after,
        payment_method, transaction_date, contract_id, customer_id,
        payer_or_receiver_name, [description], notes, performed_by, created_by
    )
    VALUES
    -- =====================================================================
    -- 7.1. DÒNG TIỀN CHI NHÁNH QUẬN 1 (CN-HCM-01) -> Tồn quỹ cuối: 520.000.000 ₫
    -- =====================================================================
    ('SQ-HCM01-0001', @branchQ1, 'CASH_IN', 'CAPITAL_CONTRIBUTION', 1031620000.00, 1031620000.00,
     'BANK_TRANSFER', '2026-09-15 08:00:00', NULL, NULL,
     N'Hội Sở PawnMaster', N'Cấp vốn lưu động đầu kỳ cho Chi nhánh Quận 1', N'Bổ sung quỹ giải ngân Q4/2026', 1, 'admin'),

    ('SQ-HCM01-0002', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 220000000.00, 811620000.00,
     'BANK_TRANSFER', '2026-09-18 14:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0126'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-004'),
     N'Lê Hoàng Long', N'Chi giải ngân hợp đồng cầm ô tô Toyota Vios HD-2026-0126', N'Đã duyệt bởi Quản lý Quận 1', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0003', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 150000000.00, 661620000.00,
     'BANK_TRANSFER', '2026-09-19 11:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0122'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-008'),
     N'Hoàng Thanh Tùng', N'Chi giải ngân hợp đồng cầm đồng hồ Rolex HD-2026-0122', N'Đã duyệt bởi Quản lý Quận 1', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0004', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 35000000.00, 626620000.00,
     'BANK_TRANSFER', '2026-10-08 16:15:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0123'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-007'),
     N'Đặng Quốc Bảo', N'Chi giải ngân hợp đồng cầm MacBook Pro M3 Max HD-2026-0123', N'Đã duyệt bởi Quản lý Quận 1', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0005', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 22000000.00, 604620000.00,
     'CASH', '2026-10-09 09:20:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0120'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-010'),
     N'Trịnh Công Minh', N'Chi giải ngân hợp đồng cầm xe Yamaha Exciter HD-2026-0120', N'Chi tiền mặt tại quầy', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0006', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 15000000.00, 589620000.00,
     'CASH', '2026-10-16 11:25:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0125'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-005'),
     N'Phạm Minh Tuấn', N'Chi giải ngân hợp đồng cầm iPhone 15 Pro Max HD-2026-0125', N'Chi tiền mặt tại quầy', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0007', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 13200000.00, 602820000.00,
     'BANK_TRANSFER', '2026-10-18 10:15:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0126'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-004'),
     N'Lê Hoàng Long', N'Thu tiền lãi 30 ngày hợp đồng HD-2026-0126 (Gia hạn)', N'Phiếu thu PT-2026-0001', 3, 'staff_q1'),

    ('SQ-HCM01-0008', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 700000.00, 603520000.00,
     'CASH', '2026-10-18 15:20:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0123'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-007'),
     N'Đặng Quốc Bảo', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0123', N'Phiếu thu PT-2026-0002', 3, 'staff_q1'),

    ('SQ-HCM01-0009', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 45000000.00, 558520000.00,
     'BANK_TRANSFER', '2026-10-20 09:35:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0128'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-002'),
     N'Nguyễn Văn An', N'Chi giải ngân hợp đồng cầm xe Honda SH 150i HD-2026-0128', N'Đã duyệt bởi Quản lý Quận 1', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0010', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 12000000.00, 546520000.00,
     'CASH', '2026-10-23 13:50:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0119'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-011'),
     N'Ngô Đức Trí', N'Chi giải ngân hợp đồng cầm iPad Pro M2 HD-2026-0119', N'Chi tiền mặt tại quầy', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0011', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 300000.00, 546820000.00,
     'CASH', '2026-10-26 11:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0125'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-005'),
     N'Phạm Minh Tuấn', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0125', N'Phiếu thu PT-2026-0004', 3, 'staff_q1'),

    ('SQ-HCM01-0012', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 40000000.00, 506820000.00,
     'BANK_TRANSFER', '2026-10-27 10:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0121'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-009'),
     N'Bùi Thu Hương', N'Chi giải ngân hợp đồng cầm Nhẫn kim cương PNJ HD-2026-0121', N'Đã duyệt bởi Quản lý Quận 1', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0013', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 880000.00, 507700000.00,
     'CASH', '2026-10-29 09:40:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0120'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-010'),
     N'Trịnh Công Minh', N'Thu tiền lãi 20 ngày đầu hợp đồng HD-2026-0120', N'Phiếu thu PT-2026-0003', 3, 'staff_q1'),

    ('SQ-HCM01-0014', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 18000000.00, 489700000.00,
     'CASH', '2026-10-30 10:20:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0127'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-003'),
     N'Trần Thị Mai', N'Chi giải ngân hợp đồng cầm Dây chuyền vàng 9999 HD-2026-0127', N'Chi tiền mặt tại quầy', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0015', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 900000.00, 490600000.00,
     'BANK_TRANSFER', '2026-10-30 14:00:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0128'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-002'),
     N'Nguyễn Văn An', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0128', N'Phiếu thu PT-2026-0005', 3, 'staff_q1'),

    ('SQ-HCM01-0016', @branchQ1, 'CASH_OUT', 'DISBURSEMENT', 16000000.00, 474600000.00,
     'CASH', '2026-10-30 15:45:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0124'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-006'),
     N'Vũ Thị Hoa', N'Chi giải ngân hợp đồng cầm xe Honda Vision HD-2026-0124', N'Chi tiền mặt tại quầy', @mgrQ1, 'manager_q1'),

    ('SQ-HCM01-0017', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 240000.00, 474840000.00,
     'CASH', '2026-11-02 16:30:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0119'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-011'),
     N'Ngô Đức Trí', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0119', N'Phiếu thu PT-2026-0006', 3, 'staff_q1'),

    ('SQ-HCM01-0018', @branchQ1, 'CASH_IN', 'INTEREST_COLLECTION', 600000.00, 475440000.00,
     'BANK_TRANSFER', '2026-11-06 10:45:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0121'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-009'),
     N'Bùi Thu Hương', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0121', N'Phiếu thu PT-2026-0007', 3, 'staff_q1'),

    ('SQ-HCM01-0019', @branchQ1, 'CASH_IN', 'PRINCIPAL_COLLECTION', 22440000.00, 497880000.00,
     'BANK_TRANSFER', '2026-11-08 11:15:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0120'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM01-010'),
     N'Trịnh Công Minh', N'Thu tất toán gốc + lãi chuộc xe Yamaha Exciter HD-2026-0120', N'Phiếu thu PT-2026-0008', 3, 'staff_q1'),

    ('SQ-HCM01-0020', @branchQ1, 'CASH_IN', 'CAPITAL_CONTRIBUTION', 22120000.00, 520000000.00,
     'BANK_TRANSFER', '2026-11-09 08:30:00', NULL, NULL,
     N'Hội Sở PawnMaster', N'Kết chuyển bổ sung tồn quỹ định mức Chi nhánh Quận 1', N'Đảm bảo tồn quỹ 520.000.000 ₫ đầu ca', 4, 'accountant_q1'),

    -- =====================================================================
    -- 7.2. DÒNG TIỀN CHI NHÁNH BÌNH THẠNH (CN-HCM-02) -> Tồn quỹ cuối: 350.000.000 ₫
    -- =====================================================================
    ('SQ-HCM02-0001', @branchBT, 'CASH_IN', 'CAPITAL_CONTRIBUTION', 405800000.00, 405800000.00,
     'BANK_TRANSFER', '2026-09-05 08:30:00', NULL, NULL,
     N'Hội Sở PawnMaster', N'Cấp vốn lưu động đầu kỳ Chi nhánh Bình Thạnh', N'Vốn vận hành Q4/2026', 1, 'admin'),

    ('SQ-HCM02-0002', @branchBT, 'CASH_OUT', 'DISBURSEMENT', 18000000.00, 387800000.00,
     'CASH', '2026-09-10 14:20:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0204'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-004'),
     N'Huỳnh Thảo Vy', N'Chi giải ngân hợp đồng cầm Laptop Dell XPS 15 HD-2026-0204', N'Duyệt bởi Quản lý Bình Thạnh', @mgrBT, 'manager_bt'),

    ('SQ-HCM02-0003', @branchBT, 'CASH_OUT', 'DISBURSEMENT', 35000000.00, 352800000.00,
     'BANK_TRANSFER', '2026-10-25 09:25:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0201'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-001'),
     N'Phan Thanh Bình', N'Chi giải ngân hợp đồng cầm xe Honda AirBlade 160 HD-2026-0201', N'Duyệt bởi Quản lý Bình Thạnh', @mgrBT, 'manager_bt'),

    ('SQ-HCM02-0004', @branchBT, 'CASH_OUT', 'DISBURSEMENT', 30000000.00, 322800000.00,
     'CASH', '2026-11-01 10:35:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0202'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-002'),
     N'Lý Ngọc Ánh', N'Chi giải ngân hợp đồng cầm 5 chỉ vàng 24K SJC HD-2026-0202', N'Duyệt bởi Quản lý Bình Thạnh', @mgrBT, 'manager_bt'),

    ('SQ-HCM02-0005', @branchBT, 'CASH_IN', 'PRINCIPAL_COLLECTION', 5700000.00, 328500000.00,
     'BANK_TRANSFER', '2026-11-04 09:50:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0201'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-001'),
     N'Phan Thanh Bình', N'Thu lãi kỳ 1 (700.000 ₫) + trả bớt gốc (5.000.000 ₫) HĐ HD-2026-0201', N'Phiếu thu PT-2026-0009', @mgrBT, 'staff_bt'),

    ('SQ-HCM02-0006', @branchBT, 'CASH_IN', 'LIQUIDATION_INCOME', 21500000.00, 350000000.00,
     'CASH', '2026-11-05 15:00:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0204'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HCM02-004'),
     N'Cửa hàng Laptop 24h (Đơn vị thu mua thanh lý)', N'Thu tiền thanh lý tài sản quá hạn Dell XPS 15 HD-2026-0204', N'Phiếu thu PT-2026-0010', @mgrBT, 'manager_bt'),

    -- =====================================================================
    -- 7.3. DÒNG TIỀN CHI NHÁNH CẦU GIẤY - HÀ NỘI (CN-HN-01) -> Tồn quỹ cuối: 410.000.000 ₫
    -- =====================================================================
    ('SQ-HN01-0001', @branchCG, 'CASH_IN', 'CAPITAL_CONTRIBUTION', 803040000.00, 803040000.00,
     'BANK_TRANSFER', '2026-10-10 08:00:00', NULL, NULL,
     N'Hội Sở PawnMaster', N'Cấp vốn lưu động đầu kỳ Chi nhánh Cầu Giấy - Hà Nội', N'Vốn vận hành Q4/2026', 1, 'admin'),

    ('SQ-HN01-0002', @branchCG, 'CASH_OUT', 'DISBURSEMENT', 320000000.00, 483040000.00,
     'BANK_TRANSFER', '2026-10-15 10:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0301'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-001'),
     N'Nguyễn Tuấn Kiệt', N'Chi giải ngân hợp đồng cầm ô tô Mazda 3 HD-2026-0301', N'Duyệt bởi Quản lý Cầu Giấy', @mgrCG, 'manager_cg'),

    ('SQ-HN01-0003', @branchCG, 'CASH_OUT', 'DISBURSEMENT', 18000000.00, 465040000.00,
     'CASH', '2026-10-18 15:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0303'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-003'),
     N'Hoàng Văn Nam', N'Chi giải ngân hợp đồng cầm xe Winner X HD-2026-0303', N'Duyệt bởi Quản lý Cầu Giấy', @mgrCG, 'manager_cg'),

    ('SQ-HN01-0004', @branchCG, 'CASH_OUT', 'DISBURSEMENT', 65000000.00, 400040000.00,
     'BANK_TRANSFER', '2026-10-28 11:35:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0302'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-002'),
     N'Đinh Thị Lan Anh', N'Chi giải ngân hợp đồng cầm 1 lượng vàng SJC HD-2026-0302', N'Duyệt bởi Quản lý Cầu Giấy', @mgrCG, 'manager_cg'),

    ('SQ-HN01-0005', @branchCG, 'CASH_IN', 'INTEREST_COLLECTION', 360000.00, 400400000.00,
     'CASH', '2026-10-28 16:00:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0303'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-003'),
     N'Hoàng Văn Nam', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0303', N'Phiếu thu PT-2026-0012', @mgrCG, 'staff_cg'),

    ('SQ-HN01-0006', @branchCG, 'CASH_IN', 'INTEREST_COLLECTION', 9600000.00, 410000000.00,
     'BANK_TRANSFER', '2026-10-30 10:20:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0301'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-HN01-001'),
     N'Nguyễn Tuấn Kiệt', N'Thu tiền lãi kỳ 1 (15 ngày) hợp đồng Mazda 3 HD-2026-0301', N'Phiếu thu PT-2026-0011', @mgrCG, 'staff_cg'),

    -- =====================================================================
    -- 7.4. DÒNG TIỀN CHI NHÁNH ĐÀ NẴNG (CN-DN-01) -> Tồn quỹ cuối: 460.000.000 ₫
    -- =====================================================================
    ('SQ-DN01-0001', @branchDN, 'CASH_IN', 'CAPITAL_CONTRIBUTION', 529240000.00, 529240000.00,
     'BANK_TRANSFER', '2026-10-20 08:00:00', NULL, NULL,
     N'Hội Sở PawnMaster', N'Cấp vốn lưu động đầu kỳ Chi nhánh Hải Châu - Đà Nẵng', N'Vốn vận hành Q4/2026', 1, 'admin'),

    ('SQ-DN01-0002', @branchDN, 'CASH_OUT', 'DISBURSEMENT', 38000000.00, 491240000.00,
     'BANK_TRANSFER', '2026-10-26 09:05:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0401'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-001'),
     N'Trương Minh Nhật', N'Chi giải ngân hợp đồng cầm xe SH Mode HD-2026-0401', N'Duyệt bởi Quản lý Đà Nẵng', @mgrDN, 'manager_dn'),

    ('SQ-DN01-0003', @branchDN, 'CASH_OUT', 'DISBURSEMENT', 32000000.00, 459240000.00,
     'CASH', '2026-11-02 14:25:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0402'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-002'),
     N'Lê Thị Mỹ Duyên', N'Chi giải ngân hợp đồng cầm trang sức vàng 18K DOJI HD-2026-0402', N'Duyệt bởi Quản lý Đà Nẵng', @mgrDN, 'manager_dn'),

    ('SQ-DN01-0004', @branchDN, 'CASH_IN', 'INTEREST_COLLECTION', 760000.00, 460000000.00,
     'CASH', '2026-11-05 11:00:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0401'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-DN01-001'),
     N'Trương Minh Nhật', N'Thu tiền lãi kỳ 1 hợp đồng HD-2026-0401', N'Phiếu thu PT-2026-0013', @mgrDN, 'staff_dn'),

    -- =====================================================================
    -- 7.5. DÒNG TIỀN CHI NHÁNH CẦN THƠ (CN-CT-01) -> Tồn quỹ cuối: 310.000.000 ₫
    -- =====================================================================
    ('SQ-CT01-0001', @branchCT, 'CASH_IN', 'CAPITAL_CONTRIBUTION', 678600000.00, 678600000.00,
     'BANK_TRANSFER', '2026-10-15 08:00:00', NULL, NULL,
     N'Hội Sở PawnMaster', N'Cấp vốn lưu động đầu kỳ Chi nhánh Ninh Kiều - Cần Thơ', N'Vốn vận hành Q4/2026', 1, 'admin'),

    ('SQ-CT01-0002', @branchCT, 'CASH_OUT', 'DISBURSEMENT', 380000000.00, 298600000.00,
     'BANK_TRANSFER', '2026-10-20 10:15:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0501'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-CT01-001'),
     N'Lâm Quốc Thịnh', N'Chi giải ngân hợp đồng cầm xe bán tải Ford Ranger Wildtrak HD-2026-0501', N'Duyệt bởi Quản lý Cần Thơ', @mgrCT, 'manager_ct'),

    ('SQ-CT01-0003', @branchCT, 'CASH_IN', 'INTEREST_COLLECTION', 11400000.00, 310000000.00,
     'BANK_TRANSFER', '2026-11-04 14:30:00',
     (SELECT id FROM dbo.pawn_contracts WHERE contract_code = 'HD-2026-0501'),
     (SELECT id FROM dbo.customers WHERE customer_code = 'KH-CT01-001'),
     N'Lâm Quốc Thịnh', N'Thu tiền lãi kỳ 1 (15 ngày) hợp đồng Ford Ranger HD-2026-0501', N'Phiếu thu PT-2026-0014', @mgrCT, 'staff_ct');
END

COMMIT TRANSACTION;
GO

-- Verification Summary
SELECT
    (SELECT COUNT(*) FROM dbo.branches) AS branches_cnt,
    (SELECT COUNT(*) FROM dbo.users) AS users_cnt,
    (SELECT COUNT(*) FROM dbo.customers) AS customers_cnt,
    (SELECT COUNT(*) FROM dbo.collaterals) AS collaterals_cnt,
    (SELECT COUNT(*) FROM dbo.pawn_contracts) AS contracts_cnt,
    (SELECT COUNT(*) FROM dbo.interest_payments) AS payments_cnt,
    (SELECT COUNT(*) FROM dbo.cashbook_transactions) AS cashbook_cnt;

SELECT
    b.branch_code,
    b.branch_name,
    b.capital_limit,
    b.current_cash_balance,
    (SELECT COUNT(*) FROM dbo.customers c WHERE c.branch_id = b.id) AS customers_count,
    (SELECT COUNT(*) FROM dbo.pawn_contracts pc WHERE pc.branch_id = b.id) AS contracts_count,
    ISNULL((SELECT SUM(pc.remaining_principal) FROM dbo.pawn_contracts pc WHERE pc.branch_id = b.id AND pc.status IN ('ACTIVE','EXTENDED','OVERDUE')), 0) AS active_principal_balance
FROM dbo.branches b
ORDER BY b.id;
GO

/*
-- =========================================================================
-- DOWN / ROLLBACK MIGRATION PLAN (REFERENCE ONLY - DO NOT RUN AUTOMATICALLY)
-- =========================================================================
BEGIN TRANSACTION;
DELETE FROM dbo.cashbook_transactions WHERE transaction_code LIKE 'SQ-%';
DELETE FROM dbo.interest_payments WHERE payment_code LIKE 'PT-2026-%';
DELETE FROM dbo.pawn_contracts WHERE contract_code LIKE 'HD-2026-%';
DELETE FROM dbo.collaterals WHERE collateral_code LIKE 'TS-2026-%';
DELETE FROM dbo.customers WHERE customer_code LIKE 'KH-%';
DELETE FROM dbo.users WHERE username IN ('manager_bt','staff_bt','accountant_bt','manager_cg','staff_cg','accountant_cg','manager_dn','staff_dn','manager_ct','staff_ct');
DELETE FROM dbo.branches WHERE branch_code IN ('CN-DN-01','CN-CT-01');
COMMIT TRANSACTION;
*/
