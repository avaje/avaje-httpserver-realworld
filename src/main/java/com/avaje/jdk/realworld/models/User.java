package com.avaje.jdk.realworld.models;

import io.avaje.jsonb.Json;

@Json
public record User(String username, String email, String bio, String image, String token) {}
