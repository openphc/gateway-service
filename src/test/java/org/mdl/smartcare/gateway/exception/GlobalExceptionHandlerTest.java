package org.mdl.smartcare.gateway.exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Verifies the standardized error envelope: business and runtime exceptions map to 500 with the
 * message surfaced, and bean-validation failures map to 400 with per-field detail. These guard the
 * spec's "no information leakage / standardized error response" requirement at the unit level.
 */
class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new GlobalExceptionHandler();
  }

  @Test
  void testHandleSmartcareBusinessException_ShouldReturn500WithMessage() {
    // Arrange
    SmartcareBusinessException ex = new SmartcareBusinessException("invalid auth header");

    // Act
    ResponseEntity<Map<String, Object>> response = handler.handleSmartcareBusinessException(ex);

    // Assert
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("invalid auth header", response.getBody().get("message"));
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getBody().get("status"));
  }

  @Test
  void testHandleRuntimeException_ShouldReturn500WithMessage() {
    // Arrange
    RuntimeException ex = new RuntimeException("unexpected");

    // Act
    ResponseEntity<Map<String, Object>> response = handler.handleRuntimeException(ex);

    // Assert
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("unexpected", response.getBody().get("message"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void testHandleValidationExceptions_ShouldReturn400WithFieldErrors() {
    // Arrange
    MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError = new FieldError("request", "permissionName", "must not be blank");
    when(ex.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors()).thenReturn(List.of(fieldError));

    // Act
    ResponseEntity<Map<String, Object>> response = handler.handleValidationExceptions(ex);

    // Assert
    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("Validation failed", response.getBody().get("message"));
    assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().get("status"));

    Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
    assertEquals("must not be blank", errors.get("permissionName"));
  }
}
