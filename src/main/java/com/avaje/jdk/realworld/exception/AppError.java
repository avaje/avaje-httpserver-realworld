package com.avaje.jdk.realworld.exception;

import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record AppError(Errors errors) {

  public AppError(String string) {
    this(new Errors(string));
  }

  public AppError(List<String> body) {
    this(new Errors(body));
  }

  public record Errors(List<String> body) {

    public Errors(String string) {
      this(List.of(string));
    }
  }
}
