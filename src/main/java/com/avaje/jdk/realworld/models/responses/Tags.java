package com.avaje.jdk.realworld.models.responses;

import io.avaje.jsonb.Json;
import java.util.List;

@Json
public record Tags(Tag tags) {

  public Tags(List<String> list) {
    this(new Tag(list));
  }

  public record Tag(List<String> tags) {}
}
