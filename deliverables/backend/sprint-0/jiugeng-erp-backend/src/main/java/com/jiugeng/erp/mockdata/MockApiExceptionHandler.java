package com.jiugeng.erp.mockdata;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Profile("mock")
@RestControllerAdvice(assignableTypes = { MockErpController.class, MockDashboardController.class })
class MockApiExceptionHandler {

    @ExceptionHandler({ MockUnauthorizedException.class, MissingRequestHeaderException.class })
    ResponseEntity<MockProblem> unauthorized(Exception exception) {
        return problem(HttpStatus.UNAUTHORIZED, "MOCK_SESSION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(MockForbiddenException.class)
    ResponseEntity<MockProblem> forbidden(MockForbiddenException exception) {
        return problem(HttpStatus.FORBIDDEN, "MOCK_CAPABILITY_DENIED", exception.getMessage());
    }

    @ExceptionHandler(MockBusinessException.class)
    ResponseEntity<MockProblem> business(MockBusinessException exception) {
        return problem(exception.status(), exception.code(), exception.getMessage());
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, HttpMessageNotReadableException.class })
    ResponseEntity<MockProblem> invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is missing or invalid");
    }

    private ResponseEntity<MockProblem> problem(HttpStatus status, String code, String detail) {
        return ResponseEntity.status(status)
                .body(new MockProblem(true, MockErpData.SOURCE, code, detail));
    }

    record MockProblem(boolean mockData, String source, String code, String detail) {
    }
}

final class MockUnauthorizedException extends RuntimeException {
    MockUnauthorizedException(String message) {
        super(message);
    }
}

final class MockForbiddenException extends RuntimeException {
    MockForbiddenException(String message) {
        super(message);
    }
}

final class MockBusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    MockBusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
