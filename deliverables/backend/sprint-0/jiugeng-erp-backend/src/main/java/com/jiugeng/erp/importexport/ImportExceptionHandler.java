package com.jiugeng.erp.importexport;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes=ImportController.class)
@Profile("!mock")
class ImportExceptionHandler {
    @ExceptionHandler(ImportException.class) ResponseEntity<Problem> known(ImportException e){return ResponseEntity.status(e.status()).body(new Problem(e.code(),e.getMessage()));}
    @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class,IllegalArgumentException.class})
    ResponseEntity<Problem> invalid(Exception e){return ResponseEntity.badRequest().body(new Problem("IMPORT_REQUEST_INVALID","导入请求无效"));}
    @ExceptionHandler(AuthorizationDeniedException.class) ResponseEntity<Problem> forbidden(Exception e){return ResponseEntity.status(403).body(new Problem("CAPABILITY_DENIED","当前账号无权执行此操作"));}
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<Problem> tooLarge(Exception e){return ResponseEntity.status(413).body(new Problem("IMPORT_FILE_TOO_LARGE","上传文件超过 10 MiB"));}
    record Problem(String code,String detail){}
}
