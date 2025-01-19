package com.avaje.jdk.realworld.models.responses;

import com.avaje.jdk.realworld.models.User;
import io.avaje.jsonb.Json;

@Json
public record UserResponse(User user) {}
