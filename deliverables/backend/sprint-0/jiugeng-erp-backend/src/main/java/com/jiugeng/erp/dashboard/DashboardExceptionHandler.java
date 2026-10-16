package com.jiugeng.erp.dashboard;

import org.springframework.context.annotation.Profile;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.ResponseEntity;

@Profile("!mock")
@RestControllerAdvice(assignableTypes = {DashboardController.class, ShipmentDocumentController.class, ShipmentDispatchController.class})
class DashboardExceptionHandler {
    @ExceptionHandler(DashboardException.class)
    ResponseEntity<Problem> business(DashboardException exception) {
        return ResponseEntity.status(exception.status()).body(new Problem(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, org.springframework.http.converter.HttpMessageNotReadableException.class})
    ResponseEntity<Problem> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new Problem("DASHBOARD_REQUEST_INVALID", "请求参数不符合要求"));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<Problem> denied(Exception exception) {
        return ResponseEntity.status(403).body(new Problem("CAPABILITY_DENIED", "当前账号无权执行此操作"));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Problem> tooLarge(Exception exception) {
        return ResponseEntity.status(413).body(new Problem("DASHBOARD_FILE_TOO_LARGE", "文件超过上传限制；图片最多 5 MB，标签文件最多 10 MB"));
    }

    record Problem(String code, String detail) {}

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Problem> duplicate(Exception exception) {
        return ResponseEntity.status(409).body(new Problem("SHIPMENT_CONFLICT","记录已变化或请求已处理，请刷新后重试"));
    }
}
