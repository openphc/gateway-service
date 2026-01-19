package org.mdl.smartcare.gateway.exception;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> handleValidationExceptions(
      MethodArgumentNotValidException ex) {
    logger.error("Validation exception occurred", ex);

    Map<String, Object> response = new HashMap<>();
    Map<String, String> errors = new HashMap<>();

    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            (error) -> {
              String fieldName = ((FieldError) error).getField();
              String errorMessage = error.getDefaultMessage();
              errors.put(fieldName, errorMessage);
            });

    response.put("message", "Validation failed");
    response.put("errors", errors);
    response.put("status", HttpStatus.BAD_REQUEST.value());

    return ResponseEntity.badRequest().body(response);
  }

  @ExceptionHandler(SmartcareBusinessException.class)
  public ResponseEntity<Map<String, Object>> handleSmartcareBusinessException(
      SmartcareBusinessException ex) {
    logger.error("Smartcare business exception occurred", ex);

    Map<String, Object> response = new HashMap<>();
    response.put("message", ex.getMessage());
    response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

    return ResponseEntity.internalServerError().body(response);
  }

  @ExceptionHandler(RuntimeException.class)
  public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex) {
    logger.error("Runtime exception occurred", ex);

    Map<String, Object> response = new HashMap<>();
    response.put("message", ex.getMessage());
    response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
  }
}
