package com.avaje.jdk.realworld.models.request;

import io.avaje.jsonb.Json;
import io.avaje.validation.constraints.Valid;
import org.jspecify.annotations.NullMarked;

@Json
@Valid
@NullMarked
public record LoginRequest(@Valid LoginUser user) {

  public record LoginUser(String email, String password) {}

  public String email() {
    return user.email;
  }

  public String password() {
    return user.password;
  }
}
