package com.avaje.jdk.realworld.models.responses;

import com.avaje.jdk.realworld.models.Profile;
import io.avaje.jsonb.Json;

@Json
public record ProfileResponse(Profile profile) {}
