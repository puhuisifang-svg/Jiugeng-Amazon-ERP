package com.jiugeng.erp.cartonlabel;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes=LabelController.class)
class LabelExceptionHandler {
    @ExceptionHandler(LabelService.Failure.class)
    ResponseEntity<?> failure(LabelService.Failure e){return ResponseEntity.status(e.status).body(Map.of("code",e.code,"message",e.getMessage()));}
    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    ResponseEntity<?> busy(){return ResponseEntity.status(409).body(Map.of("code","CARTON_LABEL_BUSY","message","相关记录正在操作，请稍后重试"));}
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(){return ResponseEntity.badRequest().body(Map.of("code","CARTON_LABEL_INVALID","message","请填写模板、品名、客户编码与采购人，并检查字段长度、字号和行高范围"));}
}
