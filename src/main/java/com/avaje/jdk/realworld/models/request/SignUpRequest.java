package com.avaje.jdk.realworld.models.request;

import io.avaje.jsonb.Json;
import io.avaje.validation.constraints.Email;
import io.avaje.validation.constraints.Length;
import io.avaje.validation.constraints.Valid;
import org.jspecify.annotations.NullMarked;

@Json
@Valid
@NullMarked
public record SignUpRequest(@Valid SignUpUser user) {

  public record SignUpUser(
      @Length(min = 5) String username, @Email String email, @Length(min = 5) String password) {}

  public String username() {
    return user.username;
  }

  public String email() {
    return user.email;
  }

  public String password() {
    return user.password;
  }
}
