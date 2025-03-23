package com.avaje.jdk.realworld;

import io.avaje.config.Config;
import io.avaje.inject.BeanScope;
import io.avaje.jex.Jex;
import io.avaje.jex.Routing.HttpService;
import io.avaje.jex.core.json.JsonbJsonService;
import io.avaje.jex.staticcontent.ClassResourceLoader;
import io.avaje.jex.staticcontent.StaticContent;
import io.avaje.jsonb.Jsonb;

public final class AvajeRealWorldApplication {

  public static void main(String[] args) {
    var beans = BeanScope.builder().build();

    var staticContent =
        StaticContent.ofClassPath("index.html")
            .route("/")
            // needed to find resources on jlinked runtime
            .resourceLoader(ClassResourceLoader.fromClass(AvajeRealWorldApplication.class))
            .build();

    Jex.create()
        .jsonService(new JsonbJsonService(beans.get(Jsonb.class)))
        .routing(beans.list(HttpService.class))
        .options(
            "/*",
            ctx ->
                ctx.header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Headers", "*"))
        .plugin(staticContent)
        // render.com port ENV variable
        .port(Config.getInt("PORT", 8080))
        .start()
        .onShutdown(beans::close);
  }
}
