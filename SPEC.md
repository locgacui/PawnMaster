# Spec: Hệ Thống Quản Lý Cầm Đồ Đa Chi Nhánh PawnMaster

## Phase 0: Capability Map (Bản Đồ Năng Lực Hệ Thống)

Hệ thống quản lý cầm đồ **PawnMaster** được phân rã thành 5 module độc lập, có ranh giới dữ liệu rõ ràng và có thể kiểm thử riêng biệt:

| Module ID | Trách nhiệm chính (Responsibility) | Phụ thuộc (Depends on) |
|---|---|---|
| `branch-user-auth` | Quản lý **Chi nhánh (`Branch`)** theo Tỉnh/Thành phố, Quản lý **Nhân viên (`User`)**, **Đăng nhập (`Login` / JWT Auth)** và **Phân quyền (`RBAC`: `ADMIN`, `MANAGER`, `STAFF`, `ACCOUNTANT`)** gắn với từng chi nhánh. | — |
| `customer-collateral-ltv` | Quản lý khách hàng (`Customer`) phân theo **6 nhóm nghề nghiệp (`CustomerOccupationGroup`)**, **Xác minh hình ảnh chứng từ bắt buộc (`VerificationDocumentType`)**, quản lý tài sản cầm cố (`Collateral`) theo **8 danh mục (`CollateralCategory`)** tại từng chi nhánh, và **Ma trận thẩm định LTV 2 chiều (Nhóm khách hàng × Loại tài sản)**. | `branch-user-auth` |
| `contract-lifecycle` | Quản lý vòng đời hợp đồng cầm cố (`Contract`) theo quy trình **2 bước (Maker-Checker)**:<br>1. Nhân viên (`STAFF`) lập hồ sơ ở trạng thái **Chờ duyệt (`PENDING_APPROVAL`)**.<br>2. Quản lý chi nhánh đó (`MANAGER`) xem xét lại hồ sơ/chứng từ và **Phê duyệt Giải ngân (`ACTIVE`)** hoặc **Từ chối (`CANCELLED`)**.<br>Hỗ trợ quản lý thời hạn vay (`maturityDate`), kỳ đóng lãi (`paymentCycleDays`), thu lãi, trả bớt gốc, gia hạn, chuộc đồ và thanh lý. | `branch-user-auth`, `customer-collateral-ltv` |
| `cashbook-ledger` | Quản lý sổ quỹ & dòng tiền (`Cashbook`) **theo từng chi nhánh**: tự động sinh phiếu Chi giải ngân (`CASH_OUT`) ngay khi Quản lý chi nhánh duyệt hợp đồng, phiếu Thu lãi/gốc/thanh lý (`CASH_IN`), tính số dư lũy kế (`balanceAfter`) và tổng hợp KPI theo chi nhánh / toàn hệ thống. | `contract-lifecycle` |
| `dashboard-integration` | Giao diện điều hành (`dashboard.html`, `dashboard.js`) và Web/API Controller: tích hợp chọn Chi nhánh, Đăng nhập/Phân quyền, bộ tính **6 Nhóm khách hàng + Tải ảnh chứng từ + Loại tài sản + Thời hạn vay**, và quy trình **Trình Quản lý chi nhánh duyệt hồ sơ → Giải ngân**, bảo toàn 100% (148/148) E2E checks. | `branch-user-auth`, `customer-collateral-ltv`, `contract-lifecycle`, `cashbook-ledger` |

**Thứ tự xây dựng (Build order):** `branch-user-auth` → `customer-collateral-ltv` → `contract-lifecycle` → `cashbook-ledger` → `dashboard-integration`

---

## 1. Objective (Mục tiêu & Yêu cầu Nghiệp vụ)

### 1.1. Quản lý Đa Chi Nhánh (`Branch`), Nhân viên (`User`), Đăng nhập & Phân quyền (`RBAC`)

#### A. Quản lý Đơn vị Hành chính & Chi nhánh theo `province_code` và `district_code` (`BranchEntity`)
- Hệ thống tích hợp bộ dữ liệu đơn vị hành chính Việt Nam (`administrative_regions`, `administrative_units`, `provinces` - 63 Tỉnh/TP, `districts` - 705 Quận/Huyện, `wards` - 10.599 Phường/Xã) trong `PawnMasterDB`.
- Bảng **Chi nhánh (`dbo.branches` / `BranchEntity`)** chỉ cần lưu khóa ngoại chuẩn hóa:
  - `branchCode` (Mã chi nhánh duy nhất, ví dụ: `CN-HCM-01`, `CN-HN-01`)
  - `branchName` (Tên chi nhánh, ví dụ: *Chi nhánh Quận 1 - TP.HCM*)
  - `provinceCode` (`VARCHAR(20)` — FK tham chiếu `dbo.provinces(code)`, ví dụ `'79'` cho TP. Hồ Chí Minh, `'01'` cho Hà Nội)
  - `districtCode` (`VARCHAR(20)` — FK tham chiếu `dbo.districts(code)`, ví dụ `'760'` cho Quận 1, `'005'` cho Quận Cầu Giấy)
  - `wardCode` (`VARCHAR(20)` — FK tùy chọn tham chiếu `dbo.wards(code)`)
  - `addressDetail` (Số nhà, tên đường)
  - `phoneNumber` (Số điện thoại hotline chi nhánh)
  - `capitalLimit` (Hạn mức vốn cấp cho chi nhánh, ví dụ `2.500.000.000 ₫`)
  - `currentCashBalance` (Số dư quỹ tiền mặt hiện tại của chi nhánh)
  - `active` (Trạng thái hoạt động)
- **Phân tách dữ liệu theo chi nhánh:** Nhân viên, Tài sản lưu kho, Hợp đồng cầm cố và Sổ quỹ thu/chi đều được gắn `branch_id` để quản lý độc lập theo từng chi nhánh và tổng hợp báo cáo toàn chuỗi.

#### B. Quản lý Nhân viên (`UserEntity`), Đăng nhập (`Auth`) & Phân quyền (`RoleEnum`)
- **Đăng nhập (`/api/v1/auth/login`):**
  - Xác thực bằng `username` và `password` (mã hóa `BCrypt`).
  - Trả về `accessToken` (JWT) kèm thông tin người dùng (`id`, `username`, `fullName`, `role`, `branchId`, `branchCode`, `branchName`, `provinceOrCity`).
- **4 Vai trò Phân quyền (`RoleEnum`):**
  1. `ADMIN` (**Chủ chuỗi / Tổng Quản trị**): Toàn quyền tạo và cấu hình Chi nhánh (`Branch`), tạo tài khoản `MANAGER`/`STAFF`/`ACCOUNTANT`, xem báo cáo dòng tiền toàn hệ thống và có quyền phê duyệt hợp đồng ở mọi chi nhánh.
  2. `MANAGER` (**Quản lý Chi nhánh**): Quản lý hoạt động tại chi nhánh được phân công (`user.branchId`); xem danh sách nhân viên trong chi nhánh; **kiểm tra lại hồ sơ vay, chứng từ xác minh nghề nghiệp/cư trú, định giá tài sản và Phê duyệt Giải ngân (`approveContract`) hoặc Từ chối (`rejectContract`)** đối với các hợp đồng thuộc chi nhánh mình.
  3. `STAFF` (**Nhân viên Giao dịch / Thẩm định viên tại Chi nhánh**): Tiếp nhận khách hàng tại quầy, kiểm tra CCCD, tải lên hình ảnh chứng từ xác minh công việc/nơi ở, thẩm định tài sản và **Lập hồ sơ hợp đồng mới ở trạng thái Chờ duyệt (`PENDING_APPROVAL`)** trình Quản lý chi nhánh; thực hiện thu lãi định kỳ và chuộc đồ cho hợp đồng trong chi nhánh.
  4. `ACCOUNTANT` (**Kế toán**): Theo dõi, đối soát sổ quỹ Thu - Chi (`CASH_IN`, `CASH_OUT`), kiểm tra số dư quỹ và báo cáo tài chính theo từng chi nhánh hoặc toàn hệ thống.

---

### 1.2. Phân nhóm Khách hàng, Hồ sơ Chứng từ Xác minh & Ma trận LTV 2 chiều (2D LTV Matrix)

#### A. 6 Nhóm Khách Hàng & Quy Định Hình Ảnh Chứng Từ Xác Minh Bắt Buộc (`CustomerOccupationGroup` & `VerificationDocumentType`)

Để giảm thiểu rủi ro mất khả năng chi trả và ngăn chặn nợ xấu, khi lập hồ sơ cầm cố, nhân viên bắt buộc phải phân loại khách hàng vào 1 trong 6 nhóm và tải lên hệ thống **hình ảnh chứng từ xác minh hợp lệ** (`verificationDocumentUrls` + `verificationDocumentType`):

| # | Nhóm khách hàng (`CustomerOccupationGroup`) | Đặc điểm thu nhập & rủi ro | Chứng từ hình ảnh bắt buộc tải lên hệ thống (Ít nhất 1 loại) | Mã loại chứng từ (`VerificationDocumentType`) |
|---|---|---|---|---|
| **1** | **Viên chức - Văn phòng** (`OFFICE_WORKER`) | Thu nhập cố định, có HĐLĐ và BHXH. Khả năng chi trả cao nhất. | • Hình ảnh **Bảng lương / Sao kê lương ngân hàng** 3 tháng gần nhất<br>• **Hợp đồng lao động / Quyết định biên chế**<br>• Ảnh chụp màn hình ứng dụng **VssID (Quá trình đóng BHXH)** | `PAYSLIP_BANK_STATEMENT`, `LABOR_CONTRACT`, `SOCIAL_INSURANCE_VSSID` |
| **2** | **Công nhân** (`FACTORY_WORKER`) | Làm việc tại nhà máy/KCN, có lương định kỳ hàng tháng và BHXH. | • Hình ảnh **Phiếu lương / Bảng lương công ty** hoặc tin nhắn báo lương<br>• **Thẻ công nhân + Hợp đồng lao động**<br>• Ảnh chụp màn hình **VssID (BHXH)** | `PAYSLIP_BANK_STATEMENT`, `WORKER_ID_OR_CONTRACT`, `SOCIAL_INSURANCE_VSSID` |
| **3** | **Tài xế công nghệ** (`TECH_DRIVER`) | Thu nhập hàng ngày qua app nhưng phương tiện hao mòn nhanh. | • Ảnh chụp màn hình **App Tài xế (Grab/Be/XanhSM...) thể hiện Hồ sơ chính chủ & Lịch sử thu nhập 4 tuần gần nhất**<br>• Kèm **Hợp đồng thuê nhà / Xác nhận cư trú** (nếu ngoại tỉnh) | `DRIVER_APP_INCOME_SCREENSHOT`, `RESIDENCE_CONFIRMATION_CT07`, `RENTAL_CONTRACT` |
| **4** | **Tiểu thương** (`SMALL_MERCHANT`) | Có sạp chợ / cửa hàng cố định, dòng tiền quay vòng hàng ngày. | • Hình ảnh **Giấy phép ĐKKD hộ cá thể** hoặc **Hợp đồng thuê sạp chợ / mặt bằng**<br>• Hình ảnh **Thực tế quầy sạp đang hoạt động + Sao kê/Sổ thu chi** | `BUSINESS_LICENSE_OR_STALL_LEASE`, `SHOP_PHOTO_AND_CASHFLOW` |
| **5** | **Kinh doanh online** (`ONLINE_SELLER`) | Dòng tiền linh hoạt theo đơn hàng nhưng biến động, không có mặt bằng cố định. | • Ảnh chụp màn hình **Doanh thu Shop (Shopee/TikTok Shop/Lazada/Fanpage)** 3 tháng gần nhất<br>• **Sao kê tài khoản ngân hàng nhận tiền đối soát COD** | `ECOMMERCE_REVENUE_SCREENSHOT`, `BANK_COD_STATEMENT` |
| **6** | **Lao động tự do** (`FREELANCER`) | Không có nghề nghiệp ổn định, không có BHXH, không chứng minh được thu nhập cố định hàng tháng. Rủi ro nợ xấu cao nhất. | • Hình ảnh **Giấy xác nhận thông tin về cư trú (Mẫu CT07 / VNeID định danh mức 2)**<br>• **Hợp đồng thuê nhà / Sổ đăng ký tạm trú** có xác nhận chủ nhà<br>• **Hóa đơn Điện / Nước / Internet** tại địa chỉ đang cư trú thực tế | `RESIDENCE_CONFIRMATION_CT07`, `RENTAL_CONTRACT`, `UTILITY_BILL` |

---

#### B. Bảng Ma Trận Hạn Mức LTV Tối Đa (`Max Standard LTV %`) theo `(Nhóm Khách Hàng × Loại Tài Sản)`

| Nhóm khách hàng (`CustomerOccupationGroup`) \ Loại tài sản (`CollateralCategory`) | Vàng / Trang sức (`GOLD_JEWELRY`) | Xe máy (`MOTORBIKE`) | Ô tô (`CAR`) | Điện thoại (`PHONE`) | Laptop (`LAPTOP`) | Đồng hồ (`WATCH`) | Bất động sản (`REAL_ESTATE`) | Khác (`OTHER`) |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **1. Viên chức - Văn phòng (`OFFICE_WORKER`)** *(Có Bảng lương / HĐLĐ / VssID)* | **92%** | **90%** | **85%** | **80%** | **80%** | **75%** | **75%** | **70%** |
| **2. Công nhân (`FACTORY_WORKER`)** *(Có Phiếu lương / Thẻ CN / VssID)* | **90%** | **80%** | **75%** | **75%** | **75%** | **70%** | **70%** | **65%** |
| **3. Tài xế công nghệ (`TECH_DRIVER`)** *(Có Ảnh thu nhập App tài xế)* | **88%** | **70%** | **70%** | **70%** | **70%** | **65%** | **65%** | **60%** |
| **4. Tiểu thương (`SMALL_MERCHANT`)** *(Có HĐ thuê sạp / GP ĐKKD)* | **90%** | **80%** | **80%** | **75%** | **75%** | **75%** | **70%** | **65%** |
| **5. Kinh doanh online (`ONLINE_SELLER`)** *(Có Ảnh doanh thu Shop / Sao kê COD)* | **88%** | **75%** | **75%** | **70%** | **70%** | **68%** | **65%** | **60%** |
| **6. Lao động tự do (`FREELANCER`)** *(Có Xác nhận cư trú CT07 / HĐ thuê nhà)* | **85%** | **65%** | **65%** | **60%** | **60%** | **55%** | **55%** | **50%** |

---

### 1.3. Quy trình Hợp đồng Cầm cố 2 Bước (Trình Duyệt Chi Nhánh → Giải Ngân) & Thời hạn vay

#### A. Quy trình Phê duyệt Hồ sơ trước khi Giải ngân (Maker-Checker Workflow)
1. **Bước 1 — Nhân viên Lập Hồ Sơ Cầm Cố (`createContract` - Role: `STAFF` / `MANAGER`):**
   - Nhân viên chi nhánh nhập thông tin khách hàng, chọn **Nhóm khách hàng (1–6)**, đính kèm **hình ảnh chứng từ xác minh bắt buộc** (`verificationDocumentType`, `verificationDocumentUrls`), nhập thông tin **Tài sản cầm cố** và **Giá trị thẩm định (`appraisedValue`)**.
   - Hệ thống kiểm tra:
     - Đã có hình ảnh chứng từ hợp lệ với nhóm khách hàng chưa (`ErrorCode.MISSING_VERIFICATION_DOCUMENTS` / `ErrorCode.INVALID_VERIFICATION_DOCUMENT`).
     - Tính tỷ lệ $\text{LTV}_{\text{actual}} = (\text{Principal Amount} / \text{Appraised Value}) \times 100\%$ và đối chiếu với trần $\text{LTV}_{\text{matrix}}(\text{Occupation}, \text{Category})$. Nếu vượt quá 100% giá trị thẩm định → Chặn cứng (`ErrorCode.EXCEEDS_MAX_LTV`).
     - Tính **Ngày đáo hạn hợp đồng (`maturityDate`)** theo **Thời hạn vay (`loanTermDays`**, mặc định 30 ngày) và **Ngày đóng lãi kỳ tiếp theo (`nextInterestDueDate`)** theo **Kỳ đóng lãi (`paymentCycleDays`**, mặc định 10 ngày).
   - Lưu hợp đồng ở trạng thái **`ContractStatus.PENDING_APPROVAL` (Chờ Quản lý chi nhánh duyệt)** gắn với `branchId` của chi nhánh. **Chưa chi tiền ra khỏi sổ quỹ.**
2. **Bước 2 — Quản lý Chi nhánh Xem Hồ Sơ & Phê Duyệt Giải Ngân (`approveAndDisburseContract` / `rejectContract` - Role: `MANAGER` của chi nhánh đó hoặc `ADMIN`):**
   - Quản lý của chi nhánh (`user.branchId == contract.branchId`) mở hồ sơ `PENDING_APPROVAL`, rà soát lại:
     - Hình ảnh chứng từ công việc/cư trú (Bảng lương, VssID, Xác nhận cư trú CT07, Hợp đồng thuê nhà...).
     - Hình ảnh & định giá tài sản cầm cố, tỷ lệ LTV và khả năng chi trả của khách hàng.
   - **Khi Quản lý bấm Phê duyệt & Giải ngân (`approveAndDisburseContract`):**
     - Ghi nhận người duyệt (`approvedBy`), thời gian duyệt (`approvedAt`), ghi chú phê duyệt (`approvalNotes`).
     - Chuyển trạng thái hợp đồng từ `PENDING_APPROVAL` → `ACTIVE`.
     - Chuyển trạng thái tài sản sang `IN_VAULT` (nhập kho/két chi nhánh).
     - Tự động sinh phiếu Chi giải ngân (`CASH_OUT` / `DISBURSEMENT`) trừ vào số dư quỹ của chi nhánh đó (`branch.currentCashBalance`).
   - **Khi Quản lý bấm Từ chối (`rejectContract`):**
     - Chuyển trạng thái hợp đồng từ `PENDING_APPROVAL` → `CANCELLED`, ghi rõ lý do từ chối, không phát sinh giao dịch sổ quỹ.

#### B. Các nghiệp vụ sau khi Hợp đồng đã Giải ngân (`ACTIVE`)
- **Thu lãi định kỳ & Trả bớt gốc (`collectInterest`):** Tính lãi theo `DAILY_PER_MILLION`, `PERCENT_MONTHLY` hoặc `PERCENT_WEEKLY` + phí lưu kho (`storageFeeRate`), dời `nextInterestDueDate` thêm `paymentCycleDays`, giảm `remainingPrincipal` nếu khách trả bớt gốc, và tự động ghi phiếu Thu (`CASH_IN`) vào sổ quỹ chi nhánh.
- **Tất toán & Chuộc tài sản (`redeemContract`):** Thu toàn bộ gốc còn lại (`remainingPrincipal`) + lãi phát sinh đến ngày chuộc, chuyển hợp đồng sang `REDEEMED`, tài sản sang `REDEEMED` (xuất kho trả khách), và ghi phiếu Thu (`CASH_IN`) vào sổ quỹ chi nhánh.
- **Quá hạn & Thanh lý (`markOverdue` / `liquidateContract`):** Chuyển hợp đồng quá hạn sang `OVERDUE`, tài sản sang `WAITING_LIQUIDATION`, và khi bán thanh lý sẽ chuyển sang `LIQUIDATED` kèm phiếu Thu thanh lý (`CASH_IN` / `LIQUIDATION_INCOME`).

---

### 1.4. Quản lý Dòng tiền & Sổ quỹ theo Chi nhánh (`Cashbook`)
- Mọi giao dịch `CASH_IN` / `CASH_OUT` đều lưu `branch_id` để theo dõi dòng tiền và số dư quỹ (`balanceAfter`) riêng biệt cho từng chi nhánh cũng như tổng hợp toàn hệ thống.
- Kiểm tra tồn quỹ chi nhánh trước khi giải ngân: Nếu số tiền giải ngân vượt quá số dư quỹ hiện tại của chi nhánh (`branch.currentCashBalance`), báo lỗi `ErrorCode.INSUFFICIENT_CASH_BALANCE`.

---

## 2. Tech Stack

- **Ngôn ngữ & Runtime:** Java 17
- **Backend Framework:** Spring Boot 3.3.5 (`spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-security`, `spring-boot-starter-thymeleaf`)
- **Database:** Microsoft SQL Server (`mssql-jdbc`) cho Production/Local; H2 Database (scope `test`) cho chạy Unit & Integration Tests tự động.
- **Mapping & Security:** Lombok, MapStruct 1.5.5.Final, JJWT 0.12.6, BCryptPasswordEncoder.
- **Frontend:** HTML5 / Thymeleaf 3 (Dual-mode rendering), Bootstrap 5.3.3, Bootstrap Icons 1.11.3, Chart.js 4.4.7, Vanilla JS (`dashboard.js`).

---

## 3. Commands (Các lệnh thực thi chuẩn)

```powershell
# Biên dịch mã nguồn Java
mvn clean compile

# Chạy toàn bộ Unit & Integration Tests (Backend Spring Boot)
mvn test

# Chạy bộ kiểm thử E2E Dashboard UI (148 checks bắt buộc đạt 100%)
node tests/run_tests.js

# Khởi chạy ứng dụng Spring Boot
mvn spring-boot:run
```

---

## 4. Project Structure (Cấu trúc thư mục)

```text
e:\vibecode\PawnMaster\
├── src/main/java/com/pawnmaster/api/
│   ├── common/                    → BaseEntity, BaseResponse, BusinessException, ErrorCode, GlobalExceptionHandler
│   ├── config/                    → SecurityConfig, JpaAuditingConfig, WebMvcConfig
│   ├── modules/
│   │   ├── branch/                → BranchEntity, BranchRepository, BranchDto, BranchService, BranchController
│   │   ├── auth/                  → AuthController, AuthService, LoginRequest, LoginResponse
│   │   ├── user/                  → UserEntity (gắn branchId), RoleEnum, UserRepository, UserDto, UserService, UserController
│   │   ├── customer/              → CustomerEntity, CustomerOccupationGroup (6 nhóm), VerificationDocumentType, Repo, DTO, Service, Controller
│   │   ├── collateral/            → CollateralEntity (gắn branchId), CollateralCategory (8 loại), CollateralStatus, LtvMatrixPolicy, Repo, DTO, Service, Controller
│   │   ├── contract/              → ContractEntity (gắn branchId, PENDING_APPROVAL, approvedBy), InterestPaymentEntity, Repo, DTO, Service, Controller
│   │   ├── cashbook/              → CashbookEntity (gắn branchId), TransactionType, TransactionCategory, Repo, DTO, Service, Controller
│   │   └── dashboard/             → DashboardController & DashboardSummaryService
│   └── security/                  → JwtAuthenticationFilter, JwtTokenProvider, UserDetailsServiceImpl
├── src/main/resources/
│   ├── db/schema.sql              → Schema SQL Server (bổ sung bảng branches, cột branch_id, occupation_group, verification_doc_*, approval fields)
│   ├── static/css/dashboard.css   → Fintech Design Tokens & Stylesheet
│   ├── static/js/dashboard.js     → Logic tính LTV 2 chiều, chọn chứng từ xác minh, trình duyệt & phê duyệt hợp đồng theo chi nhánh
│   └── templates/dashboard.html   → Giao diện điều hành cầm đồ đa chi nhánh (Dual-mode Thymeleaf + Static)
├── src/test/java/com/pawnmaster/api/
│   └── modules/                   → Unit & Integration tests cho Branch, Auth/User, LtvMatrixPolicy, DocumentVerification, Contract Approval, Cashbook
└── tests/
    └── run_tests.js               → 148 E2E checks cho giao diện Dashboard
```

---

## 5. Code Style & Conventions

Kiểm tra chặt chẽ quyền hạn của Quản lý chi nhánh trước khi phê duyệt và giải ngân hợp đồng:

```java
// Ví dụ kiểm tra quyền Quản lý chi nhánh khi duyệt hồ sơ hợp đồng chờ giải ngân
@Transactional
public ContractResponse approveAndDisburseContract(Long contractId, Long approverUserId, String approvalNotes) {
    UserEntity approver = userRepository.findByIdAndDeletedFalse(approverUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

    ContractEntity contract = contractRepository.findByIdAndDeletedFalse(contractId)
            .orElseThrow(() -> new BusinessException(ErrorCode.CONTRACT_NOT_FOUND));

    if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
        throw new BusinessException(ErrorCode.INVALID_REQUEST, "Chỉ có thể phê duyệt hợp đồng đang ở trạng thái Chờ duyệt");
    }

    // Chỉ ADMIN hoặc MANAGER của chính chi nhánh đó mới được duyệt giải ngân
    boolean isAdmin = approver.getRole() == RoleEnum.ADMIN;
    boolean isBranchManager = approver.getRole() == RoleEnum.MANAGER
            && approver.getBranch() != null
            && approver.getBranch().getId().equals(contract.getBranch().getId());

    if (!isAdmin && !isBranchManager) {
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "Chỉ Quản lý của chi nhánh " + contract.getBranch().getBranchName() + " mới có quyền duyệt giải ngân hồ sơ này");
    }
    // Chuyển trạng thái ACTIVE, nhập kho IN_VAULT và xuất quỹ CASH_OUT của chi nhánh...
}
```

---

## 6. Testing Strategy (Chiến lược Kiểm thử)

1. **Unit Tests cho Ma trận LTV 2 chiều & Chứng từ xác minh (`LtvMatrixPolicyTest`):**
   - Kiểm tra 6 nhóm khách hàng × 8 loại tài sản (ví dụ: `OFFICE_WORKER` + `MOTORBIKE` = `90%`; `FREELANCER` + `MOTORBIKE` = `65%`).
   - Kiểm tra bắt buộc chứng từ hình ảnh theo từng nhóm khách hàng (Bảng lương/VssID cho Nhân viên/Công nhân; Xác nhận cư trú CT07/Hợp đồng thuê nhà cho Lao động tự do).
2. **Unit & Service Tests cho Chi nhánh, Phân quyền & Quy trình Duyệt Hợp đồng 2 bước (`ContractApprovalWorkflowTest`, `BranchUserAuthTest`):**
   - Kiểm tra `STAFF` tại Chi nhánh A tạo hợp đồng → Trạng thái là `PENDING_APPROVAL`, số dư quỹ chi nhánh chưa bị trừ.
   - Kiểm tra `STAFF` hoặc `MANAGER` của Chi nhánh khác cố tình duyệt hợp đồng của Chi nhánh A → Bị chặn bởi `ErrorCode.FORBIDDEN`.
   - Kiểm tra `MANAGER` của đúng Chi nhánh A bấm Duyệt → Hợp đồng chuyển sang `ACTIVE`, tài sản `IN_VAULT`, sổ quỹ Chi nhánh A tự động ghi `CASH_OUT` và trừ đúng số tiền giải ngân.
   - Kiểm tra `MANAGER` từ chối hồ sơ → Hợp đồng chuyển sang `CANCELLED`, quỹ không bị trừ.
3. **Unit & Service Tests cho Thu lãi, Trả bớt gốc, Chuộc đồ, Thanh lý & Dòng tiền (`CashbookServiceTest`):**
   - Kiểm tra mọi biến động tiền tệ đều cập nhật đúng số dư quỹ (`balanceAfter`) của chi nhánh tương ứng.
4. **Frontend E2E Verification (`node tests/run_tests.js`):**
   - Bảo đảm 100% (148/148 checks pass) trên bộ kiểm thử giao diện Dashboard.

---

## 7. Boundaries (Ranh giới & Quy tắc An toàn)

- **Always do:**
  - Dùng `BigDecimal` cho mọi phép tính tiền tệ, lãi suất và tỷ lệ LTV.
  - Bắt buộc xác minh chứng từ hình ảnh (`verificationDocumentType` + `verificationDocumentUrls`) tương ứng với nhóm nghề nghiệp của khách hàng.
  - Bắt buộc hợp đồng mới phải qua bước Quản lý chi nhánh xét duyệt (`PENDING_APPROVAL` → `ACTIVE`) trước khi xuất quỹ giải ngân.
  - Chạy `mvn test` và `node tests/run_tests.js` để xác minh 100% tests xanh.
  - Chạy `graphify update .` sau khi thay đổi các file mã nguồn.
- **Ask first:**
  - Thay đổi các ràng buộc khóa ngoại (Foreign Keys) hiện có trong `schema.sql`.
- **Never do:**
  - Không cho phép nhân viên (`STAFF`) tự ý giải ngân hợp đồng mà không qua Quản lý chi nhánh (`MANAGER`) hoặc `ADMIN` phê duyệt.
  - Không cho phép Quản lý chi nhánh A duyệt hồ sơ của chi nhánh B.
  - Không phá vỡ bất kỳ kiểm thử nào trong 148 checks của `tests/run_tests.js`.

---

## 8. Success Criteria (Tiêu chí Nghiệm thu)

- [ ] **SC1 - Quản lý Đa Chi Nhánh (`Branch`), Nhân viên (`User`), Login & Phân quyền (`RBAC`):** Quản lý được danh sách chi nhánh theo Tỉnh/Thành phố (ví dụ: Chi nhánh Quận 1 - TP.HCM, Chi nhánh Cầu Giấy - Hà Nội), quản lý nhân viên theo chi nhánh, đăng nhập JWT và phân quyền chặt chẽ (`ADMIN`, `MANAGER`, `STAFF`, `ACCOUNTANT`).
- [ ] **SC2 - 6 Nhóm khách hàng, Chứng từ xác minh & Ma trận LTV 2 chiều:** Kiểm tra bắt buộc hình ảnh chứng từ theo từng nhóm (Bảng lương/VssID cho Nhân viên/Công nhân; Xác nhận cư trú/HĐ thuê nhà cho Lao động tự do...) và tính chính xác hạn mức LTV theo `(Nhóm khách hàng × Loại tài sản)`.
- [ ] **SC3 - Quy trình Phê duyệt Hợp đồng 2 bước tại Chi nhánh (Maker-Checker):** Nhân viên lập hợp đồng ở trạng thái `PENDING_APPROVAL` (chưa chi tiền); chỉ Quản lý của đúng chi nhánh đó (`MANAGER`) hoặc `ADMIN` xem lại hồ sơ và bấm Phê duyệt mới chuyển sang `ACTIVE` và chi tiền giải ngân từ quỹ chi nhánh.
- [ ] **SC4 - Quản lý Thời hạn vay, Thu lãi, Chuộc đồ, Thanh lý & Dòng tiền Chi nhánh:** Đầy đủ nghiệp vụ tính ngày đáo hạn (`maturityDate`), kỳ đóng lãi (`nextInterestDueDate`), thu lãi, trả bớt gốc, chuộc đồ, thanh lý và tự động hạch toán sổ quỹ (`CASH_IN`/`CASH_OUT`) theo từng chi nhánh.
- [ ] **SC5 - Giao diện Điều hành (`dashboard.html` & `dashboard.js`):** Hiển thị thông tin Chi nhánh & Người dùng đang đăng nhập, tích hợp bộ chọn Nhóm khách hàng + Chứng từ xác minh + Tính LTV 2 chiều + Quy trình Trình duyệt / Phê duyệt giải ngân trên UI, đồng thời đạt 148/148 E2E checks (`node tests/run_tests.js`) và pass 100% `mvn test`.

---

## 9. Open Questions (Xác nhận để bắt đầu Lập kế hoạch & Triển khai)

Bản đặc tả [`SPEC.md`](file:///e:/vibecode/PawnMaster/SPEC.md) đã tích hợp đầy đủ:
1. **Quản lý Đa Chi nhánh theo Tỉnh/Thành phố (`Branch`)**.
2. **Quản lý Nhân viên (`User`), Đăng nhập (`Login`) & Phân quyền (`RBAC`)**.
3. **Quy trình 2 bước (Maker-Checker):** Nhân viên lập hồ sơ (`PENDING_APPROVAL`) → Quản lý chi nhánh xem lại hồ sơ & chứng từ rồi mới Phê duyệt Giải ngân (`ACTIVE` + xuất quỹ `CASH_OUT`).
4. **6 Nhóm khách hàng + Chứng từ hình ảnh bắt buộc + Ma trận LTV 2 chiều (Nhóm KH × Loại TS) + Thời hạn vay & Dòng tiền**.

👉 **Bạn hãy xác nhận phê duyệt (`Approve` / `Đồng ý`) bản đặc tả này để chúng ta chuyển sang bước Lập kế hoạch (`tasks/plan.md`, `tasks/todo.md`) và bắt đầu triển khai code.**
