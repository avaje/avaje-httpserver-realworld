package com.avaje.jdk.realworld.web;

import com.avaje.jdk.realworld.models.Profile;
import com.avaje.jdk.realworld.models.responses.ProfileResponse;
import com.avaje.jdk.realworld.security.AppRole;
import com.avaje.jdk.realworld.security.Roles;
import io.avaje.http.api.Controller;
import io.avaje.http.api.Delete;
import io.avaje.http.api.Get;
import io.avaje.http.api.Post;
import io.avaje.jex.Context;
import io.ebean.DB;

@Roles(AppRole.JWT)
@Controller("/profiles")
public final class ProfilesController {

  private static final String PROFILE_SQL =
      """
      SELECT
          username,
          bio,
          image,
          EXISTS(
              SELECT id
              FROM realworld.follow
              WHERE from_user_id = ? AND to_user_id = realworld.user.id
          ) as following
      FROM realworld.user
      WHERE username = ?
      """;
  private static final String USER_ID = "userId";

  @Get("/{username}")
  ProfileResponse getProfileHandler(Context ctx, String username) {

    return new ProfileResponse(getProfile(ctx, username));
  }

  private Profile getProfile(Context ctx, String username) {
    return DB.findDto(Profile.class, PROFILE_SQL)
        .setParameter(ctx.attribute(USER_ID))
        .setParameter(username)
        .findOne();
  }

  @Post("/{username}/follow")
  ProfileResponse followUserHandler(Context ctx, String username) {

    try (var txn = DB.beginTransaction()) {

      var userId = ctx.attribute(USER_ID);
      DB.sqlUpdate(
              """
            INSERT INTO realworld.follow(from_user_id, to_user_id)
            VALUES (?, (
                SELECT id
                FROM realworld.user
                WHERE username = ?
            ))
            ON CONFLICT DO NOTHING
            """)
          .setParameter(userId)
          .setParameter(username)
          .execute();
    }

    return new ProfileResponse(getProfile(ctx, username));
  }

  @Delete("/{username}/follow")
  ProfileResponse unfollowUserHandler(Context ctx, String username) {

    var userId = ctx.attribute(USER_ID);
    DB.sqlUpdate(
            """
            DELETE FROM realworld.follow
            WHERE from_user_id = ? AND to_user_id = (
                SELECT id
                FROM realworld.user
                WHERE username = ?
            )
            """)
        .setParameter(userId)
        .setParameter(username)
        .execute();

    return new ProfileResponse(getProfile(ctx, username));
  }
}
