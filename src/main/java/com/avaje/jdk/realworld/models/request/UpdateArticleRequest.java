package com.avaje.jdk.realworld.models.request;

import io.avaje.jsonb.Json;
import io.avaje.validation.constraints.Valid;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

@Json
@Valid
public record UpdateArticleRequest(@NonNull UpdateArticleBody article) {

  public boolean anyUpdates() {
    return article.title.isPresent() || article.description.isPresent() || article.body.isPresent();
  }

  public record UpdateArticleBody(
      Optional<String> title, Optional<String> description, Optional<String> body) {

    public boolean anyUpdates() {
      return title.isPresent() || description.isPresent() || body.isPresent();
    }
  }
}
