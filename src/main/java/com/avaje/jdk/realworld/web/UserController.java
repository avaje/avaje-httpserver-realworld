package com.avaje.jdk.realworld.web;

import com.avaje.jdk.realworld.exception.AppError;
import com.avaje.jdk.realworld.models.User;
import com.avaje.jdk.realworld.models.entities.UserEntity;
import com.avaje.jdk.realworld.models.entities.query.QUserEntity;
import com.avaje.jdk.realworld.models.request.LoginRequest;
import com.avaje.jdk.realworld.models.request.SignUpRequest;
import com.avaje.jdk.realworld.models.request.SignUpRequest.SignUpUser;
import com.avaje.jdk.realworld.models.request.UpdateUserRequest;
import com.avaje.jdk.realworld.models.request.UpdateUserRequest.UpdateBody;
import com.avaje.jdk.realworld.models.responses.UserResponse;
import com.avaje.jdk.realworld.security.AppRole;
import com.avaje.jdk.realworld.security.Roles;
import com.avaje.jdk.realworld.web.service.TokenService;
import io.avaje.http.api.Controller;
import io.avaje.http.api.Delete;
import io.avaje.http.api.Get;
import io.avaje.http.api.Post;
import io.avaje.http.api.Put;
import io.avaje.http.api.Valid;
import io.avaje.jex.http.Context;
import io.avaje.jex.http.HttpResponseException;
import io.ebean.DB;
import io.ebean.DuplicateKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Valid
@Controller
@Roles(AppRole.JWT)
public final class UserController {

  private static final String USER_ID = "userId";

  private static final Logger LOG = LoggerFactory.getLogger(UserController.class);

  private final TokenService tokenService;

  UserController(TokenService tokenService) {

    this.tokenService = tokenService;
  }

  @Roles(AppRole.ANYONE)
  @Post("/users")
  UserResponse registerHandler(Context ctx, SignUpRequest body) {

    return switch (body) {
      case SignUpRequest(SignUpUser(String username, String email, String password)) -> {
        var entity =
            new UserEntity().bio("").username(username).email(email).passwordHash(password);

        try {
          entity.insert();
        } catch (DuplicateKeyException e) {
          determineWhy(body);
        }

        yield new UserResponse(
            new User(username, email, "", null, tokenService.getJWT(entity.id())));
      }
    };
  }

  private void determineWhy(SignUpRequest body) {
    LOG.warn("Matching user found. Determining why");
    var row =
        DB.sqlQuery(
                """
                SELECT
                    (
                        SELECT COUNT(realworld.user.id)
                        FROM realworld.user
                        WHERE username = ?
                    ) as matching_username,
                    (
                        SELECT COUNT(realworld.user.id)
                        FROM realworld.user
                        WHERE email = ?
                    ) as matching_email
                """)
            .setParameter(1, body.username())
            .setParameter(2, body.email().toLowerCase(Locale.US))
            .findOne();

    var errors = new ArrayList<String>();

    if (Optional.ofNullable(row.getInteger("matching_username")).orElse(0) > 0) {
      LOG.warn("Duplicate username. {}", body.username());
      errors.add("username already taken");
    }

    if (Optional.ofNullable(row.getInteger("matching_email")).orElse(0) > 0) {
      LOG.warn("Duplicate email. {}", body.email());
      errors.add("email already taken");
    }

    throw new HttpResponseException(422, new AppError(errors));
  }

  @Roles(AppRole.ANYONE)
  @Post("/users/login")
  UserResponse loginHandler(LoginRequest body) {
    var columns = QUserEntity.alias();
    var rs =
        new QUserEntity()
            .select(
                columns.id,
                //   columns.username,
                columns.email,
                columns.bio,
                columns.image,
                columns.passwordHash)
            .email
            .eq(body.email())
            .findOne();

    if (rs == null) {

      throw new HttpResponseException(404, new AppError(List.of("no matching user")));
    }

    UUID userId;
    String passwordHash;

    userId = rs.id();
    passwordHash = rs.passwordHash();

    if (!body.password().equals(passwordHash)) {

      throw new HttpResponseException(400, new AppError("password does not match"));
    }

    return new UserResponse(
        new User(rs.username(), rs.email(), rs.bio(), rs.image(), tokenService.getJWT(userId)));
  }

  @Get("/user")
  UserResponse getCurrentUserHandler(Context ctx) {

    UUID userId = ctx.attribute(USER_ID);
    var result = new QUserEntity().select("username, email, bio, image").id.eq(userId).findOne();

    return new UserResponse(
        new User(result.username(), result.email(), result.bio(), result.image(), null));
  }

  @Put("/user")
  UserResponse updateUserHandler(Context ctx, UpdateUserRequest request) {

    UUID userId = ctx.attribute(USER_ID);

    var userEnt = DB.reference(UserEntity.class, userId);

    switch (request) {
      case UpdateUserRequest(
          UpdateBody(
              Optional<String> email,
              Optional<String> username,
              Optional<String> password,
              Optional<String> image,
              Optional<String> bio)) -> {
        email.ifPresent(userEnt::email);
        username.ifPresent(userEnt::username);
        password.ifPresent(userEnt::passwordHash);
        image.ifPresent(userEnt::image);
        bio.ifPresent(userEnt::bio);
        userEnt.save();
      }
    }
    return new UserResponse(
        new User(userEnt.username(), userEnt.email(), userEnt.bio(), userEnt.image(), null));
  }

  @Delete("/user")
  void deleteUser(Context ctx) {

    UUID userId = ctx.attribute(USER_ID);
    new UserEntity().id(userId).delete();
  }
}
