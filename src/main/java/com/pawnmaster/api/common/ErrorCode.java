package com.pawnmaster.api.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common & System Errors (1000 - 1999)
    SUCCESS(1000, "Thao tác thành công", HttpStatus.OK),
    INTERNAL_SERVER_ERROR(1001, "Lỗi hệ thống nội bộ", HttpStatus.INTERNAL_SERVER_ERROR),
    INVALID_REQUEST(1002, "Dữ liệu đầu vào không hợp lệ", HttpStatus.BAD_REQUEST),
    RESOURCE_NOT_FOUND(1003, "Không tìm thấy dữ liệu yêu cầu", HttpStatus.NOT_FOUND),
    UNAUTHORIZED(1004, "Chưa xác thực hoặc phiên đăng nhập không hợp lệ", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(1005, "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),
    CONFLICT_DATA(1006, "Dữ liệu bị trùng lặp hoặc xung đột", HttpStatus.CONFLICT),

    // Customer Module Errors (2000 - 2999)
    CUSTOMER_NOT_FOUND(2001, "Không tìm thấy khách hàng", HttpStatus.NOT_FOUND),
    CUSTOMER_IDENTITY_EXISTS(2002, "Số CCCD/CMND của khách hàng đã tồn tại", HttpStatus.CONFLICT),
    MISSING_VERIFICATION_DOCUMENTS(2003, "Thiếu hình ảnh chứng từ xác minh bắt buộc theo nhóm nghề nghiệp", HttpStatus.BAD_REQUEST),
    INVALID_VERIFICATION_DOCUMENT(2004, "Loại chứng từ xác minh không phù hợp với nhóm khách hàng", HttpStatus.BAD_REQUEST),

    // Collateral Module Errors (3000 - 3999)
    COLLATERAL_NOT_FOUND(3001, "Không tìm thấy tài sản cầm cố", HttpStatus.NOT_FOUND),
    COLLATERAL_INVALID_STATUS(3002, "Trạng thái tài sản không hợp lệ", HttpStatus.BAD_REQUEST),
    EXCEEDS_MAX_LTV(3003, "Số tiền vay vượt quá hạn mức tỷ lệ LTV tối đa cho phép", HttpStatus.BAD_REQUEST),

    // Contract Module Errors (4000 - 4999)
    CONTRACT_NOT_FOUND(4001, "Không tìm thấy hợp đồng cầm đồ", HttpStatus.NOT_FOUND),
    CONTRACT_ALREADY_CLOSED(4002, "Hợp đồng đã tất toán hoặc thanh lý", HttpStatus.BAD_REQUEST),
    CONTRACT_NOT_PENDING_APPROVAL(4003, "Hợp đồng không ở trạng thái chờ duyệt", HttpStatus.BAD_REQUEST),
    BRANCH_ACCESS_DENIED(4004, "Bạn không có quyền thao tác trên chi nhánh khác", HttpStatus.FORBIDDEN),

    // Cashbook Module Errors (5000 - 5999)
    CASHBOOK_ENTRY_NOT_FOUND(5001, "Không tìm thấy phiếu thu/chi", HttpStatus.NOT_FOUND),
    INSUFFICIENT_CASH_BALANCE(5002, "Số dư quỹ tiền mặt không đủ", HttpStatus.BAD_REQUEST),

    // Branch Module Errors (6000 - 6999)
    BRANCH_NOT_FOUND(6001, "Không tìm thấy chi nhánh", HttpStatus.NOT_FOUND),
    BRANCH_CODE_EXISTS(6002, "Mã chi nhánh đã tồn tại", HttpStatus.CONFLICT),
    BRANCH_INACTIVE(6003, "Chi nhánh đang tạm ngưng hoạt động", HttpStatus.BAD_REQUEST),
    PROVINCE_NOT_FOUND(6004, "Không tìm thấy Tỉnh/Thành phố", HttpStatus.NOT_FOUND),
    DISTRICT_NOT_FOUND(6005, "Không tìm thấy Quận/Huyện", HttpStatus.NOT_FOUND),
    WARD_NOT_FOUND(6006, "Không tìm thấy Phường/Xã", HttpStatus.NOT_FOUND),

    // User & Auth Module Errors (7000 - 7999)
    USER_NOT_FOUND(7001, "Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    USERNAME_EXISTS(7002, "Tên đăng nhập đã tồn tại", HttpStatus.CONFLICT),
    EMAIL_EXISTS(7003, "Email đã được sử dụng", HttpStatus.CONFLICT),
    USER_INACTIVE(7004, "Tài khoản người dùng đã bị khóa hoặc ngừng hoạt động", HttpStatus.FORBIDDEN),
    INVALID_CREDENTIALS(7005, "Tên đăng nhập hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
}

