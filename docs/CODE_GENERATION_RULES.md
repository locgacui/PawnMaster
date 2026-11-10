# Quy chuẩn Thiết kế Mã định danh Nghiệp vụ (Business Code Specification) & Tối ưu Index SQL Server

> **Phiên bản**: 2.0 (Chuẩn hóa Multi-Branch & High-Scale)  
> **Áp dụng cho**: Hệ thống Core Cầm Đồ Đa Chi Nhánh **PawnMaster**  
> **Class thực thi Trung tâm**: [`BusinessCodeGenerator.java`](../src/main/java/com/pawnmaster/api/common/BusinessCodeGenerator.java)  
> **Bảng lưu quy tắc trong DB**: `dbo.business_code_rules` ([`V20261110_02__Standardize_Business_Codes_And_Indexes.sql`](../src/main/resources/db/V20261110_02__Standardize_Business_Codes_And_Indexes.sql))

---

## 1. Phân tích Điểm nghẽn của Định dạng Cũ & Nguyên lý Thiết kế Mới

| Vấn đề của định dạng cũ | Tác động khi Scale lớn | Giải pháp chuẩn hóa mới |
| :--- | :--- | :--- |
| **`HD-2026-0129`** (4 chữ số/năm) | Giới hạn cứng **9.999 hợp đồng/năm**. Khi chuỗi có 50+ chi nhánh sẽ tràn số ngay trong tháng đầu và gây tranh chấp khóa (`Sequence Lock Contention`) toàn hệ thống. | Phân tách theo **`YYMM` + `B<BranchId>` + `6 ký tự Base36/Seq`** (`HD-2611-B01-000129` hoặc `HD-2611-B01-8F3K2A`), chịu tải tới **2,17 tỷ** hợp đồng/chi nhánh/tháng. |
| **`KH-HCM01-001`** (Gắn mã chi nhánh vào KH) | Khách hàng trong chuỗi có thể cầm đồ ở Q.1 hôm nay và giao dịch ở Thủ Đức hoặc Đà Nẵng tháng sau. Gắn cứng `HCM01` gây sai lệch ngữ nghĩa toàn chuỗi. | **Tách hoàn toàn khỏi Chi nhánh**: `KH-<YYMM>-<5_Base36><1_Checksum>` (`KH-2611-8F3K9C`). Định danh duy nhất toàn chuỗi kèm ký tự kiểm tra **Luhn Mod-36**. |
| **`UUID.substring(0, 8)`** (`KH-9F8A1B2C`, `CB-4A1E90B2`) | Sinh chuỗi hoàn toàn ngẫu nhiên ở đầu mã làm **phân mảnh nặng B-Tree Index (Page Splits ~50%)** trong SQL Server vì mỗi lệnh `INSERT` chèn vào một trang ngẫu nhiên. | Đặt **Tiền tố Đơn điệu theo Thời gian (`YYMM` hoặc `YYYYMMDD-HHmmss`)** ngay sau Prefix giúp các bản ghi mới luôn `APPEND` vào nhánh phải của B-Tree Index. |
| Dùng `NVARCHAR` hoặc cột quá rộng `VARCHAR(100)` cho mã code | Tốn gấp đôi bộ nhớ (2 bytes/char), làm giảm số lượng Index Key trên mỗi trang 8KB của SQL Server. | Chuẩn hóa 100% mã code về **`VARCHAR(32)` ASCII (Uppercase `0-9`, `A-Z`, `-`)** kết hợp **Covering Non-Clustered Unique Index (`INCLUDE`)**. |

---

## 2. Bảng Quy tắc Mã Code Chuẩn hóa cho Toàn bộ các Bảng

| STT | Bảng (Table) | Cột (Column) | Kiểu dữ liệu SQL | Định dạng chuẩn (Pattern) | Độ dài | Regex Kiểm tra | Ví dụ Thực tế |
| :-: | :--- | :--- | :--- | :--- | :-: | :--- | :--- |
| 1 | `dbo.branches` | `branch_code` | `VARCHAR(20)` | `CN-<PROVINCE_ABBR>-<SEQ_2>` | 9–11 | `^CN-[A-Z]{2,4}-\d{2,3}$` | `CN-HCM-01`, `CN-HN-01` |
| 2 | `dbo.customers` | `customer_code` | `VARCHAR(24)` | `KH-<YYMM>-<5_BASE36><1_CHK>` | 14 | `^KH-\d{4}-[0-9A-Z]{6}$` | `KH-2611-8F3K9C` |
| 3 | `dbo.collaterals` | `collateral_code` | `VARCHAR(28)` | `TS-<YYMM>-B<BRANCH_ID>-<CAT2><4_BASE36>` | 18–19 | `^TS-\d{4}-B\d{2,3}-(XM\|OT\|DT\|LT\|VG\|DH\|BD\|KH)[0-9A-Z]{4}$` | `TS-2611-B01-XM0129` |
| 4 | `dbo.pawn_contracts` | `contract_code` | `VARCHAR(28)` | `HD-<YYMM>-B<BRANCH_ID>-<6_SEQ_OR_BASE36>` | 18–19 | `^HD-\d{4}-B\d{2,3}-[0-9A-Z]{6}$` | `HD-2611-B01-000129` |
| 5 | `dbo.interest_payments` | `payment_code` | `VARCHAR(32)` | `PT-<TYPE3>-<YYYYMMDD>-<HHmmss>-<3_BASE36>` | 26 | `^PT-(LAI\|GOC\|TAT\|TLY)-\d{8}-\d{6}-[0-9A-Z]{3}$` | `PT-LAI-20261110-093122-8FA` |
| 6 | `dbo.cashbook_transactions` | `transaction_code` | `VARCHAR(32)` | `<IN\|OUT>-<YYYYMMDD>-<HHmmss>-<3_BASE36>` | 22–23 | `^(IN\|OUT)-\d{8}-\d{6}-[0-9A-Z]{3}$` | `IN-20261110-093122-8FA` |

---

## 3. Chi tiết Cấu trúc & Ý nghĩa Từng Bảng

### 3.1. Bảng Khách hàng (`dbo.customers.customer_code`)
* **Định dạng**: `KH-<YYMM>-<Payload_5_Base36><Checksum_1_Base36>`
* **Ví dụ**: `KH-2611-01A9K7`
* **Giải thích**:
  * `KH`: Tiền tố Khách hàng toàn hệ thống.
  * `YYMM`: Năm - Tháng đăng ký lần đầu (VD: `2611` = Tháng 11/2026). Giúp B-Tree Index phân cụm theo tháng, không gây phân mảnh trang dữ liệu cũ.
  * `Payload_5_Base36`: 5 ký tự hệ cơ số 36 (`0-9`, `A-Z`) kết hợp từ xung nhịp thời gian (millisecond trong tháng) và bộ sinh ngẫu nhiên mật mã (`SecureRandom`), cung cấp $36^5 = 60.466.176$ không gian mã mỗi tháng.
  * `Checksum_1_Base36`: 1 ký tự kiểm tra tính theo thuật toán **Luhn Mod-36** trên chuỗi `KH<YYMM><Payload_5_Base36>`.
  * **Không phụ thuộc chi nhánh**: Khách hàng giao dịch xuyên suốt mọi chi nhánh trong chuỗi bằng duy nhất 1 mã `customer_code` và số CCCD (`identity_number`).

### 3.2. Bảng Hợp đồng Cầm đồ (`dbo.pawn_contracts.contract_code`)
* **Định dạng**: `HD-<YYMM>-B<BranchId_Pad2>-<6_Char_Seq_Or_Base36>`
* **Ví dụ**: `HD-2611-B01-000129` (hoặc `HD-2611-B01-7K9M2X`)
* **Giải thích**:
  * `HD`: Tiền tố Hợp đồng cầm đồ.
  * `YYMM`: Năm - Tháng mở hợp đồng (`2611`). Đặt trước mã chi nhánh để các truy vấn báo cáo theo kỳ (`WHERE contract_code LIKE 'HD-2611-%'`) và truy vấn theo chi nhánh trong kỳ (`WHERE contract_code LIKE 'HD-2611-B01-%'`) đều tận dụng được **Index Range Seek**.
  * `B<BranchId_Pad2>`: Mã định danh chi nhánh rút gọn (`B01` cho Branch ID 1, `B02` cho Branch ID 2,...). Ngắn gọn hơn `CN-HCM-01` (tiết kiệm 6 bytes trên mỗi khóa Index).
  * `<6_Char_Seq_Or_Base36>`: 6 ký tự định danh tăng dần theo thời gian (Time-ordered Base36 hoặc Sequence 6 chữ số), mở rộng sức chứa từ 10.000 hợp đồng/năm lên **2.176.782.336** tổ hợp/chi nhánh/tháng.

### 3.3. Bảng Tài sản Cầm cố (`dbo.collaterals.collateral_code`)
* **Định dạng**: `TS-<YYMM>-B<BranchId_Pad2>-<Cat2><4_Char_Base36>`
* **Ví dụ**: `TS-2611-B01-XM8F2A`
* **Bảng ánh xạ `<Cat2>` (2 ký tự loại tài sản)**:
  * `XM`: `MOTORBIKE` (Xe máy)
  * `OT`: `CAR` (Ô tô)
  * `DT`: `PHONE` (Điện thoại)
  * `LT`: `LAPTOP` (Laptop / Máy tính)
  * `VG`: `GOLD_JEWELRY` (Vàng bạc / Trang sức)
  * `DH`: `WATCH` (Đồng hồ cao cấp)
  * `BD`: `REAL_ESTATE` (Bất động sản)
  * `KH`: `OTHER` (Tài sản khác)

### 3.4. Bảng Sổ quỹ Thu/Chi (`dbo.cashbook_transactions.transaction_code`)
* **Định dạng**: `<IN|OUT>-<YYYYMMDD>-<HHmmss>-<3_Char_Base36>`
* **Ví dụ**: `IN-20261110-093122-8FA` (Phiếu thu), `OUT-20261110-081500-3C9` (Phiếu chi)
* **Giải thích**:
  * `IN` / `OUT`: Phân biệt tức thì dòng tiền Vào (`CASH_IN`) và dòng tiền Ra (`CASH_OUT`).
  * `YYYYMMDD-HHmmss`: Timestamp chính xác tới giây theo múi giờ vận hành, đảm bảo khóa Index luôn tăng đơn điệu theo thời gian thực.
  * `3_Char_Base36`: 3 ký tự ngẫu nhiên Base36 ($36^3 = 46.656$ mã/giây) chống đụng độ tuyệt đối khi nhiều giao dịch viên bấm thu/chi hoặc duyệt giải ngân trong cùng 1 giây.

### 3.5. Bảng Lịch sử Đóng lãi / Thu nợ (`dbo.interest_payments.payment_code`)
* **Định dạng**: `PT-<Type3>-<YYYYMMDD>-<HHmmss>-<3_Char_Base36>`
* **Ví dụ**: `PT-LAI-20261110-093122-8FA`
* **Bảng ánh xạ `<Type3>` (`PaymentType`)**:
  * `LAI`: `INTEREST_PAYMENT` (Đóng lãi định kỳ)
  * `GOC`: `PARTIAL_PRINCIPAL` (Trả bớt gốc)
  * `TAT`: `REDEMPTION` (Tất toán & Chuộc đồ)
  * `TLY`: `LIQUIDATION` (Thu tiền thanh lý tài sản)

---

## 4. Chiến lược Đặt Index trong SQL Server

1. **Giữ Clustered Primary Key trên `id BIGINT IDENTITY(1,1)`**:
   * Đảm bảo mọi bảng có Clustered Key nhỏ gọn (8 bytes), tăng tuần tự tuyệt đối, và mọi Foreign Key (`contract_id`, `customer_id`, `branch_id`) chỉ tốn 8 bytes.
2. **Đặt `UNIQUE NONCLUSTERED INDEX` kèm `INCLUDE` (Covering Index) cho cột Code**:
   * Giúp các truy vấn tra cứu theo mã code (`WHERE contract_code = @code AND is_deleted = 0`) thực hiện **Index Seek** và trả về ngay các cột thiết yếu (`id`, `branch_id`, `status`,...) mà **không cần Key Lookup** quay lại Clustered Index:
   ```sql
   CREATE UNIQUE NONCLUSTERED INDEX idx_contracts_code_cov
   ON dbo.pawn_contracts(contract_code)
   INCLUDE (branch_id, customer_id, collateral_id, status, principal_amount, remaining_principal)
   WHERE is_deleted = 0;
   ```
3. **Tận dụng Left-Prefix SARGable Range Scan**:
   * Nhờ cấu trúc `HD-YYMM-Bxx-...` và `IN-YYYYMMDD-HHmmss-...`, các truy vấn lọc theo tháng hoặc theo ngày (`LIKE 'HD-2611-%'` hoặc `LIKE 'IN-20261110-%'`) đều là **SARGable Prefix Search** (Index Seek trên B-Tree).

