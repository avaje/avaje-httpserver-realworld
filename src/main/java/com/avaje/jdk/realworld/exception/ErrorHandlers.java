package com.avaje.jdk.realworld.exception;

import com.auth0.jwt.exceptions.JWTVerificationException;
import io.avaje.http.api.Controller;
import io.avaje.http.api.ExceptionHandler;
import io.avaje.http.api.ValidationException;
import jakarta.persistence.PersistenceException;

@Controller
public class ErrorHandlers {

  @ExceptionHandler(statusCode = 500)
  AppError sql(PersistenceException sql) {
    sql.printStackTrace();
    return new AppError("Failed to query DB");
  }

  @ExceptionHandler(statusCode = 422)
  AppError argument(IllegalArgumentException iae) {

    return new AppError(iae.getMessage());
  }

  @ExceptionHandler(statusCode = 422)
  AppError validation(ValidationException ve) {

    return new AppError(
        ve.getErrors().stream().map(v -> v.getPath() + " has error: " + v.getMessage()).toList());
  }

  @ExceptionHandler(statusCode = 403)
  AppError jwtError(JWTVerificationException ve) {

    return new AppError(ve.getMessage());
  }
}
