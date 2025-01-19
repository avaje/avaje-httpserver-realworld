package com.avaje.jdk.realworld.security;

import io.avaje.jex.security.Role;

public enum AppRole implements Role {
  ANYONE,
  JWT;
}
