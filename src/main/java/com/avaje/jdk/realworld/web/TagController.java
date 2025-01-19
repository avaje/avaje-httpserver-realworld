package com.avaje.jdk.realworld.web;

import com.avaje.jdk.realworld.models.responses.Tags;
import com.avaje.jdk.realworld.security.AppRole;
import com.avaje.jdk.realworld.security.Roles;
import io.avaje.http.api.Controller;
import io.avaje.http.api.Get;
import io.ebean.DB;

@Controller
public final class TagController {

  private static final String SELECT_TAGS = "SELECT name FROM realworld.tag";

  @Get("/tags")
  @Roles(AppRole.ANYONE)
  Tags getTagsHandler() {
    return new Tags(DB.sqlQuery(SELECT_TAGS).mapToScalar(String.class).findList());
  }
}
