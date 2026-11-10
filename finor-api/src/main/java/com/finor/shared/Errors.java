package com.finor.shared;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
  ResponseEntity<ProblemDetail> business(
      org.springframework.web.server.ResponseStatusException exception) {
    return ResponseEntity.status(exception.getStatusCode()).body(exception.getBody());
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> conflict() {
    return ResponseEntity.status(409)
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Dados em conflito com um registro existente."));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    java.time.DateTimeException.class,
    ArithmeticException.class,
    jakarta.validation.ConstraintViolationException.class,
    MethodArgumentNotValidException.class
  })
  ResponseEntity<ProblemDetail> invalid() {
    return ResponseEntity.badRequest()
        .body(
            ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Confira os campos informados."));
  }
}
