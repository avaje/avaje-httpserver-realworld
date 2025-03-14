package com.avaje.jdk.realworld.security;

import com.avaje.jdk.realworld.exception.AppError;
import com.avaje.jdk.realworld.web.service.TokenService;
import io.avaje.http.api.Controller;
import io.avaje.http.api.Filter;
import io.avaje.jex.http.Context;
import io.avaje.jex.http.HttpFilter.FilterChain;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Controller
public class JWTAuthFilter {
  private static final Logger LOG = LoggerFactory.getLogger(JWTAuthFilter.class);

  private final TokenService tokenService;

  JWTAuthFilter(TokenService tokenService) {

    this.tokenService = tokenService;
  }

  @Filter
  void authFilter(Context ctx, FilterChain chain) {

    if (!ctx.routeRoles().contains(AppRole.JWT)) {

      chain.proceed();
      return;
    }

    var userId = getUserId(ctx);

    if (userId == null) {
      ctx.status(401).json(new AppError("Unauthenticated"));
    } else {
      ctx.attribute("userId", userId);
      chain.proceed();
    }
  }

  UUID getUserId(Context ctx) {
    var authHeader = ctx.header("Authorization");
    if (authHeader != null && authHeader.startsWith("Token ")) {
      var token = authHeader.substring("Token ".length());
      return tokenService.authenticateToken(token);
    } else {
      LOG.info("No auth header");
      return null;
    }
  }
}
