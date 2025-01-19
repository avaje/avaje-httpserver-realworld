package com.avaje.jdk.realworld.models.request;

import io.avaje.jsonb.Json;
import io.avaje.validation.constraints.Valid;
import org.jspecify.annotations.NullMarked;

@Json
@Valid
@NullMarked
public record CommentRequest(@Valid CommentRequestBody comment) {

  public record CommentRequestBody(String body) {}
}
