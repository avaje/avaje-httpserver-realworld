package com.avaje.jdk.realworld.models.request;

import io.avaje.jsonb.Json;
import java.util.Optional;

@Json
public record UpdateUserRequest(UpdateBody user) {

  public record UpdateBody(
      Optional<String> email,
      Optional<String> username,
      Optional<String> password,
      Optional<String> image,
      Optional<String> bio) {}
}
