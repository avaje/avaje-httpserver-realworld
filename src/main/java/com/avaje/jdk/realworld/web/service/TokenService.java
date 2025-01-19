package com.avaje.jdk.realworld.web.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.avaje.jdk.realworld.models.entities.query.QUserEntity;
import io.avaje.config.Config;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Singleton
public class TokenService {

  private static final String USER_ID = "userId";
  private static final long VALIDITY = Config.getLong("jwt.validity", 18000);
  private static final String ISSUER = Config.get("jwt.issuer", "memes");
  private static final Algorithm algorithm =
      Config.get("jwt.secret.key", "secretkey").transform(Algorithm::HMAC256);

  private final JWTVerifier verifier =
      JWT.require(algorithm).withIssuer(ISSUER).withClaim(USER_ID, (c, d) -> true).build();

  public String getJWT(UUID request) {

    return JWT.create()
        .withIssuedAt(Instant.now())
        .withExpiresAt(Instant.now().plus(VALIDITY, ChronoUnit.SECONDS))
        .withClaim(USER_ID, request.toString())
        .withIssuer(ISSUER)
        .sign(algorithm);
  }

  public UUID authenticateToken(String token) {
    var userId = UUID.fromString(verifier.verify(token).getClaim(USER_ID).asString());

    if (new QUserEntity().id.eq(userId).exists()) {
      // LOG.info("Extracted info for user {}", userId);
    } else {
      return null;
    }

    return userId;
  }
}
