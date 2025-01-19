package com.avaje.jdk.realworld.models.request;

import io.avaje.jsonb.Json;
import io.avaje.validation.constraints.Valid;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.NullMarked;

@Json
@Valid
@NullMarked
public record CreateArticleRequest(CreateContent article) {
  public record CreateContent(
      String title, String description, String body, Optional<List<String>> tagList) {}
}
