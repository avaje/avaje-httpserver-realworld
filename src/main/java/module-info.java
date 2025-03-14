import io.avaje.inject.spi.InjectExtension;
import io.avaje.jsonb.spi.JsonbExtension;
import io.avaje.validation.spi.ValidationExtension;
import io.ebean.config.DatabaseConfigProvider;
import io.ebean.config.EntityClassRegister;
import io.ebean.config.LookupProvider;

module avaje.realworld {
  /// jwt validation
  requires com.auth0.jwt;
  /// JDBC extensions
  requires dev.mccue.jdbc;
  /// Database connection pooling
  requires io.ebean.core;
  requires io.ebean.querybean;
  /// Logging
  requires io.jstach.rainbowgum.slf4j;
  /// HTTP server
  requires jdk.httpserver;
  /// Postgres Driver
  requires org.postgresql.jdbc;
  /// Turns text into url safe slug
  requires slugify;
  /// Configuration
  requires io.avaje.config;
  /// Dependency Injection
  requires io.avaje.inject;
  /// JAX-Style controller generation
  requires io.avaje.http.api;
  /// Json
  requires io.avaje.jsonb;
  /// Jspecify
  requires org.jspecify;
  /// Validation
  requires io.avaje.validation;
  requires io.avaje.validation.contraints;
  requires io.avaje.validation.http;
  /// Json DI plugin
  requires io.avaje.jsonb.plugin;
  /// jdk.httpserver wrapper
  requires io.avaje.jex;
  requires static io.avaje.spi;

  provides EntityClassRegister with
      com.avaje.jdk.realworld.models.entities.EbeanEntityRegister;
  provides DatabaseConfigProvider with
      com.avaje.jdk.realworld.security.Encryptor;
  provides LookupProvider with
      avaje.realworld.lookup.EbeanMethodLookup;

  /// generated DI Classes
  provides InjectExtension with
      com.avaje.jdk.realworld.RealworldModule;

  /// generated Json Classes
  provides JsonbExtension with
      com.avaje.jdk.realworld.jsonb.GeneratedJsonComponent;
  provides ValidationExtension with
      com.avaje.jdk.realworld.models.request.valid.GeneratedValidatorComponent;
}
