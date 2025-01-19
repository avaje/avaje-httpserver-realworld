package com.avaje.jdk.realworld;

import io.avaje.config.Config;
import io.avaje.inject.BeanScope;
import io.avaje.jex.Jex;
import io.avaje.jex.Routing.HttpService;
import io.avaje.jex.core.json.JsonbJsonService;
import io.avaje.jsonb.Jsonb;

public final class Main {

  public static void main(String[] args) {
    var beans = BeanScope.builder().build();

    Jex.create()
        .jsonService(new JsonbJsonService(beans.get(Jsonb.class)))
        .routing(beans.list(HttpService.class))
        .options(
            "/*",
            ctx ->
                ctx.header("Access-Control-Allow-Origin", "*")
                    .header("Access-Control-Allow-Headers", "*"))
        .port(Config.getInt("server.port"))
        .context("/api")
        .start()
        .onShutdown(beans::close);
  }
}
