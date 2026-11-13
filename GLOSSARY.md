# PawnMaster

Hệ thống quản trị nghiệp vụ cầm đồ đa chi nhánh, định giá tài sản thế chấp, kiểm soát hạn mức giải ngân theo khẩu vị rủi ro và lưu giữ niêm phong kho/két sắt.

## Ngôn Ngữ Nghiệp Vụ (Ubiquitous Language)

### Tài Sản Thế Chấp & Thẩm Định

**Danh mục Tài sản Cầm cố (Collateral Category)**:
Phân loại nghiệp vụ cấp cao của tài sản thế chấp (Xe máy, Ô tô, Vàng / Trang sức, Điện thoại, Laptop, Đồng hồ, Tài sản khác), quyết định trực tiếp trần tỷ lệ cho vay tối đa (Trần LTV) và quy trình lưu kho/niêm phong.
_Avoid_: Loại hàng hóa, nhóm sản phẩm

**Nhóm Thông số Định danh (Collateral Spec Group)**:
Tập hợp các thuộc tính pháp lý và kỹ thuật dùng chung cấu trúc biểu mẫu giữa một hoặc nhiều Danh mục Tài sản Cầm cố (Phương tiện giao thông, Thiết bị công nghệ & Đồng hồ, Vàng bạc đá quý, Tài sản khác). Chuyển đổi giữa các danh mục cùng nhóm giữ nguyên bộ thuộc tính định danh; chuyển sang nhóm khác sẽ thay đổi bộ thuộc tính định danh áp dụng cho hợp đồng.
_Avoid_: Form con, tab tài sản

**Hồ sơ Thuộc tính Chuyên biệt Tài sản (Normalized Collateral Detail)**:
Mô hình mở rộng 1:1 theo khóa chính dùng chung (`collateral_id` FK -> `dbo.collaterals(id) ON DELETE CASCADE`) gồm 4 bảng chuyên biệt (`collateral_vehicle_details`, `collateral_tech_details`, `collateral_gold_details`, `collateral_other_details`) lưu trữ có kiểu dữ liệu rõ ràng các thông số riêng của từng Nhóm Thông số Định danh, đồng thời tự động đồng bộ tóm tắt về hai cột tra cứu nhanh `serial_or_frame_number` và `license_plate` trên bảng cha `dbo.collaterals`.
_Avoid_: Nhồi JSON vào condition_description, EAV rời rạc

**Thông số Thẩm định Thực nhập (Operator-Entered Spec)**:
Giá trị định danh hoặc đặc điểm kỹ thuật của tài sản do giao dịch viên trực tiếp kiểm tra và ghi nhận tại quầy cho hồ sơ đang lập, phân biệt hoàn toàn với dữ liệu minh họa mặc định khi mới mở biểu mẫu.
_Avoid_: Dữ liệu trên form, text có sẵn

**Dữ liệu Mẫu Mặc định (Default Seed Sample)**:
Giá trị minh họa ban đầu hiển thị sẵn khi khởi tạo mới không gian lập hợp đồng nhằm gợi ý định dạng chuẩn, chưa đại diện cho tài sản thật của khách hàng và được tự động dọn sạch khi giao dịch viên đổi sang danh mục tài sản khác.
_Avoid_: Dữ liệu nháp, dữ liệu khách hàng cũ

**Bộ nhớ Tạm Thông số Phiên (Session Spec Stash)**:
Vùng lưu giữ tạm thời các Thông số Thẩm định Thực nhập của một Nhóm Thông số Định danh trong phiên lập hợp đồng hiện hành khi giao dịch viên chuyển sang nhóm tài sản khác, cho phép khôi phục nguyên vẹn nếu quay lại danh mục cũ nhưng không đưa vào hợp đồng của danh mục mới.
_Avoid_: Bản nháp hợp đồng (Contract Draft)

**Giá trị Thẩm định Thực tế (Appraised Value)**:
Mức giá thị trường hợp lý của tài sản cầm cố tại thời điểm giao dịch do nhân viên thẩm định xác lập sau khi kiểm tra ngoại quan, pháp lý và khả năng thanh khoản.
_Avoid_: Giá mua ban đầu, số tiền vay

**Vị trí Lưu kho / Ngăn Két Niêm phong (Custody Location)**:
Địa chỉ vật lý cụ thể tại chi nhánh giao dịch (ô bãi xe, tủ chống ẩm hoặc ngăn két sắt) nơi tài sản cầm cố được dán tem chữ ký giáp lai và lưu giữ đến khi tất toán chuộc đồ.
_Avoid_: Địa chỉ chi nhánh

### Hạn Mức & Hồ Sơ Khách Hàng

**Trần LTV (LTV Ceiling)**:
Tỷ lệ phần trăm tối đa giữa số tiền gốc giải ngân trên Giá trị Thẩm định Thực tế được phép cấp tự động theo ma trận kết hợp giữa Nhóm Nghề nghiệp của khách hàng và Danh mục Tài sản Cầm cố.
_Avoid_: Lãi suất trần, hạn mức vốn chi nhánh

**Danh bạ Khách hàng Toàn hệ thống (Global Customer Registry)**:
Hồ sơ định danh KYC và địa chỉ thường trú pháp lý của khách hàng dùng chung xuyên suốt mọi chi nhánh, độc lập với chi nhánh nơi phát sinh từng hợp đồng cầm cố.
_Avoid_: Khách hàng chi nhánh

### Điều Kiện Vay, Lãi Suất & Giải Ngân

**Phương thức Tính lãi theo Tuần (`PERCENT_WEEKLY`)**:
Chế độ tính lãi theo tỷ lệ phần trăm (%) trên dư nợ gốc cho mỗi chu kỳ 7 ngày ($\text{Lãi} = \text{Gốc còn lại} \times \frac{\text{interest\_rate}}{100} \times \frac{\text{Số ngày}}{7}$), thường đi kèm chu kỳ đóng lãi 7 ngày hoặc 14 ngày/kỳ.
_Avoid_: Lãi ngày, lãi tháng

**Phương thức Giải ngân (`DisbursementMethod`)**:
Hình thức xuất tiền cho vay khi Quản lý phê duyệt hợp đồng (`CASH` — chi tiền mặt trực tiếp tại quầy hoặc `BANK_TRANSFER` — chuyển khoản vào tài khoản ngân hàng thụ hưởng), đồng bộ 1-1 xuống `cashbook_transactions.payment_method`.
_Avoid_: Ghi chú giải ngân tự do trong notes

**Danh mục Ngân hàng Thụ hưởng (`dbo.banks`)**:
Bảng danh mục chuẩn hóa các ngân hàng tại Việt Nam kèm mã code (`VCB`, `TCB`...) và mã BIN NAPAS/VietQR, phục vụ chọn dropdown ngân hàng giải ngân và ràng buộc khóa ngoại trên `dbo.pawn_contracts.disbursement_bank_code`.
_Avoid_: Tự gõ tên ngân hàng tự do


