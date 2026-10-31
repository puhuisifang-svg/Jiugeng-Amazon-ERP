package com.jiugeng.erp.iam;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = { IamController.class, AuditController.class, RoleController.class, UserDefaultsController.class })
@Profile("!mock")
class IamExceptionHandler {
    private final AuthorizationFailureAudit failures;
    IamExceptionHandler(AuthorizationFailureAudit failures) { this.failures=failures; }
    @ExceptionHandler(IamException.class)
    ResponseEntity<Problem> iam(IamException exception, jakarta.servlet.http.HttpServletRequest request) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if ((!request.getMethod().equals("GET") && (request.getRequestURI().contains("/roles") || request.getRequestURI().contains("/users"))) && auth!=null && auth.getPrincipal() instanceof IamTypes.SessionPrincipal actor) failures.record(actor,request.getRequestURI(),exception.code());
        return ResponseEntity.status(exception.status()).body(new Problem(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
    ResponseEntity<Problem> invalid(Exception exception) {
        return ResponseEntity.badRequest().body(new Problem("INVALID_REQUEST", "请求参数不符合要求"));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    ResponseEntity<Problem> forbidden(AuthorizationDeniedException exception, jakarta.servlet.http.HttpServletRequest request) {
        var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (!request.getMethod().equals("GET") && auth!=null && auth.getPrincipal() instanceof IamTypes.SessionPrincipal actor) failures.record(actor,request.getRequestURI(),"CAPABILITY_DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new Problem("CAPABILITY_DENIED", "当前账号无权执行此操作"));
    }

    record Problem(String code, String detail) {
    }
}
