package com.jiugeng.erp.watch;
import org.springframework.context.annotation.Profile;import org.springframework.http.ResponseEntity;import org.springframework.http.converter.HttpMessageNotReadableException;import org.springframework.security.authorization.AuthorizationDeniedException;import org.springframework.web.bind.MethodArgumentNotValidException;import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={WatchController.class,WatchWorkbench.class,WatchNoveltyController.class}) @Profile("!mock") class WatchExceptionHandler{
 @ExceptionHandler(WatchException.class) ResponseEntity<Problem> business(WatchException e){return ResponseEntity.status(e.status()).body(new Problem(e.code(),e.getMessage()));}
 @ExceptionHandler({MethodArgumentNotValidException.class,HttpMessageNotReadableException.class}) ResponseEntity<Problem> invalid(Exception e){return ResponseEntity.badRequest().body(new Problem("INVALID_REQUEST","请求参数不符合要求"));}
 @ExceptionHandler(AuthorizationDeniedException.class) ResponseEntity<Problem> forbidden(Exception e){return ResponseEntity.status(403).body(new Problem("CAPABILITY_DENIED","当前账号无权执行此操作"));}
 record Problem(String code,String detail){}
}
