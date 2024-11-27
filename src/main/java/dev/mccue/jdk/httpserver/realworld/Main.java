package dev.mccue.jdk.httpserver.realworld;

import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import dev.mccue.jdk.httpserver.HttpExchanges;
import dev.mccue.jdk.httpserver.json.JsonBody;
import dev.mccue.jdk.httpserver.regexrouter.RegexRouter;
import dev.mccue.json.Json;
import io.github.cdimascio.dotenv.Dotenv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

public final class Main {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(Main.class);

    private Main() {

    }

    public void start() throws Exception {
        var env = Dotenv.load();
        var db = new HikariDataSource();
        db.setDriverClassName(env.get("POSTGRES_DRIVER"));
        db.setJdbcUrl(env.get("POSTGRES_URL"));
        db.setUsername(env.get("POSTGRES_USERNAME"));
        db.setPassword(env.get("POSTGRES_PASSWORD"));

        int port;
        try {
            port = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException e) {
            port = 7585;
        }

        var server = HttpServer.create(
                new InetSocketAddress(port),
                0
        );

        var routerBuilder = RegexRouter.builder()
                .errorHandler((throwable, httpExchange) -> {
                    LOGGER.error(
                            "Unhandled exception while handling {} {}",
                            httpExchange.getRequestMethod(),
                            httpExchange.getRequestURI(),
                            throwable
                    );
                    HttpExchanges.sendResponse(httpExchange, 401, JsonBody.of(
                            Json.objectBuilder()
                                    .put("errors", Json.objectBuilder()
                                            .put("request", Json.arrayBuilder()
                                                    .add(Json.of("internal error"))))));
                    ;
                })
                .notFoundHandler(exchange ->
                        HttpExchanges.sendResponse(exchange, 404, JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add(Json.of("not found"))))
                        )));

        new RealWorldAPI(db).register(routerBuilder);

        var router = routerBuilder.build();

        server.createContext("/", exchange -> {
            LOGGER.info("{} {}", exchange.getRequestMethod(), exchange.getRequestURI());
            router.handle(exchange);
        });

        server.start();
    }

    public static void main(String[] args) throws Exception {
        new Main().start();
    }
}
