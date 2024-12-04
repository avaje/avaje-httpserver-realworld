package dev.mccue.jdk.httpserver.realworld;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.github.slugify.Slugify;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.mccue.jdbc.ResultSets;
import dev.mccue.jdbc.SQLFragment;
import dev.mccue.jdbc.UncheckedSQLException;
import dev.mccue.jdk.httpserver.Body;
import dev.mccue.jdk.httpserver.HttpExchanges;
import dev.mccue.jdk.httpserver.json.JsonBody;
import dev.mccue.jdk.httpserver.regexrouter.RegexRouter;
import dev.mccue.jdk.httpserver.regexrouter.RouteParams;
import dev.mccue.json.Json;
import dev.mccue.json.JsonArray;
import dev.mccue.json.JsonDecoder;
import dev.mccue.json.JsonObject;
import dev.mccue.urlparameters.UrlParameters;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.invoke.LambdaMetafactory;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static dev.mccue.json.JsonDecoder.*;

public final class RealWorldAPI {
    private static final Logger LOG
            = LoggerFactory.getLogger(RealWorldAPI.class);

    private final DataSource db;

    RealWorldAPI(DataSource db) {
        this.db = db;
    }

    UUID getUserId(HttpExchange exchange) {
        var authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Token ")) {
            var token = authHeader.substring("Token ".length());
            try (var conn = db.getConnection();
                 var stmt = conn.prepareStatement("""
                             SELECT user_id
                             FROM realworld.api_key
                             WHERE value = ? AND invalidated_at IS NULL
                             """)) {
                stmt.setString(1, token);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    var id = rs.getObject("user_id", UUID.class);
                    LOG.info("Extracted info for user {}", id);
                    return id;
                } else {
                    LOG.info("No api key");
                    return null;
                }
            } catch (SQLException e) {
                throw new UncheckedSQLException(e);
            }
        }
        else {
            LOG.info("No auth header");
            return null;
        }
    }

    String getToken(Connection conn, UUID userId) throws SQLException {
        var apiKey = UUID.randomUUID();
        try (var stmt = conn.prepareStatement("""
                    INSERT INTO realworld.api_key(user_id, value)
                    VALUES (?, ?)
                    """)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, apiKey.toString());
            stmt.execute();
        }
        return apiKey.toString();
    }

    void unauthenticated(HttpExchange exchange) throws IOException {
        HttpExchanges.sendResponse(
                exchange,
                401,
                JsonBody.of(
                        Json.objectBuilder()
                                .put("errors", Json.objectBuilder()
                                        .put("body", Json.arrayBuilder()
                                                .add("unauthenticated")))
                )
        );
    }

    record RegisterRequest(
            String username,
            String email,
            char[] password
    ) {
        static RegisterRequest fromJson(Json json) {
            return field(json, "user", user -> new RegisterRequest(
                    field(user, "username", string()),
                    field(user, "email", string()),
                    field(user, "password", string().map(String::toCharArray))
            ));
        }
    }

    @Route(methods = "POST", pattern = "/api/users")
    void registerHandler(HttpExchange exchange) throws IOException {
        var body = JsonBody.read(exchange, RegisterRequest::fromJson);
        try (var conn = db.getConnection()) {
            UUID userId = null;
            JsonObject.Builder userJson = Json.objectBuilder();
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO realworld."user"(username, email, password_hash)
                    VALUES (?, ?, ?)
                    ON CONFLICT (username)
                        DO UPDATE SET id = EXCLUDED.id
                    RETURNING id, email, username, bio, image
                    """)) {

                stmt.setObject(1, body.username);
                stmt.setObject(2, body.email.toLowerCase(Locale.US));
                stmt.setObject(3, BCrypt.withDefaults().hashToString(12, body.password));
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    userId = rs.getObject("id", UUID.class);
                    userJson
                            .put("email", rs.getString("email"))
                            .put("username", rs.getString("username"))
                            .put("bio", rs.getString("bio"))
                            .put("image", rs.getString("image"));
                }
            }

            if (userId != null) {
                userJson.put("token", getToken(conn, userId));
                HttpExchanges.sendResponse(
                        exchange,
                        200,
                        JsonBody.of(Json.objectBuilder().put("user", userJson))
                );
            }
            else {
                LOG.warn("Matching user found. Determining why");
                try (var stmt = conn.prepareStatement("""
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
                    """)) {
                    stmt.setObject(1, body.username);
                    stmt.setObject(2, body.email);
                    var rs = stmt.executeQuery();
                    rs.next();

                    var errors = new ArrayList<Json>();
                    if (ResultSets.getIntegerNotNull(rs, "matching_username") > 0) {
                        LOG.warn("Duplicate username. {}", body.username);
                        errors.add(Json.of("username already taken"));
                    }

                    if (ResultSets.getIntegerNotNull(rs, "matching_email") > 0) {
                        LOG.warn("Duplicate email. {}", body.email);
                        errors.add(Json.of("email already taken"));
                    }

                    HttpExchanges.sendResponse(
                            exchange,
                            422,
                            JsonBody.of(
                                    Json.objectBuilder()
                                            .put("errors", Json.objectBuilder()
                                                    .put("body", Json.of(errors)))
                            )
                    );
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    record LoginRequest(String email, char[] password) {
        static LoginRequest fromJson(Json json) {
            return field(json, "user", user -> new LoginRequest(
                    field(user, "email", string()),
                    field(user, "password", string().map(String::toCharArray))
            ));
        }
    }

    @Route(methods = "POST", pattern = "/api/users/login")
    void loginHandler(HttpExchange exchange) throws IOException {
        var body = JsonBody.read(exchange, LoginRequest::fromJson);

        try (var conn = db.getConnection()) {
            UUID userId;
            String passwordHash;
            JsonObject.Builder userJson = Json.objectBuilder();
            try (var stmt = conn.prepareStatement("""
                    SELECT id, email, username, bio, image, password_hash
                    FROM realworld."user"
                    WHERE email = ?
                    """)) {
                stmt.setObject(1, body.email);
                var rs = stmt.executeQuery();
                if (!rs.next()) {
                    HttpExchanges.sendResponse(
                            exchange,
                            422,
                            JsonBody.of(
                                    Json.objectBuilder()
                                            .put("errors", Json.objectBuilder()
                                                    .put("body", Json.arrayBuilder()
                                                            .add("no matching user")))
                            )
                    );
                    return;
                }

                userId = rs.getObject("id", UUID.class);
                passwordHash = rs.getString("password_hash");

                userJson
                        .put("email", rs.getString("email"))
                        .put("username", rs.getString("username"))
                        .put("bio", rs.getString("bio"))
                        .put("image", rs.getString("image"));
            }

            if (!BCrypt.verifyer().verify(body.password, passwordHash).verified) {
                HttpExchanges.sendResponse(
                        exchange,
                        422,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("password does not match")))
                        )
                );
                return;
            }

            userJson.put("token", getToken(conn, userId));

            HttpExchanges.sendResponse(exchange, 200, JsonBody.of(Json.objectBuilder().put("user", userJson)));
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/user")
    void getCurrentUserHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        try (var conn = db.getConnection()) {
            JsonObject.Builder userJson = Json.objectBuilder();
            try (var stmt = conn.prepareStatement("""
                     SELECT email, username, bio, image
                     FROM realworld."user"
                     WHERE id = ?
                     """)) {
                stmt.setObject(1, userId);
                var rs = stmt.executeQuery();
                rs.next();
                userJson
                        .put("email", rs.getString("email"))
                        .put("username", rs.getString("username"))
                        .put("bio", rs.getString("bio"))
                        .put("image", rs.getString("image"));
            }

            userJson.put("token", getToken(conn, userId));

            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(
                            Json.objectBuilder()
                                    .put("user", userJson)
                    )
            );
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    record UpdateUserRequest(
            Optional<String> email,
            Optional<String> username,
            Optional<String> password,
            Optional<String> image,
            Optional<String> bio
    ) {
        static UpdateUserRequest fromJson(Json json) {
            return field(json, "user", user -> new UpdateUserRequest(
                    optionalField(user, "email", string()),
                    optionalField(user, "username", string()),
                    optionalField(user, "password", string()),
                    optionalField(user, "image", string()),
                    optionalField(user, "bio", string())
            ));
        }
    }

    @Route(methods = "PUT", pattern = "/api/user")
    void updateUserHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }
        var request = JsonBody.read(exchange, UpdateUserRequest::fromJson);

        var setFragments = new ArrayList<SQLFragment>();
        request.email.ifPresent(email -> {
            setFragments.add(SQLFragment.of("email = ?", List.of(email)));
        });

        request.username.ifPresent(username -> {
            setFragments.add(SQLFragment.of("username = ?", List.of(username)));
        });

        request.password.ifPresent(password -> {
            setFragments.add(SQLFragment.of("password = ?", List.of(password)));
        });

        request.image.ifPresent(image -> {
            setFragments.add(SQLFragment.of("image = ?", List.of(image)));
        });

        request.bio.ifPresent(bio -> {
            setFragments.add(SQLFragment.of("bio = ?", List.of(bio)));
        });

        try (var conn = db.getConnection()) {
            if (!setFragments.isEmpty()) {
                SQLFragment sql = SQLFragment.of("""
                        UPDATE realworld."user"
                        SET\s
                        """);

                for (int i = 0; i < setFragments.size(); i++) {
                    sql = sql.concat(setFragments.get(i));
                    if (i != setFragments.size() - 1) {
                        sql = sql.concat(SQLFragment.of(", "));
                    }
                }

                sql = sql.concat(SQLFragment.of("""
                        
                        WHERE id = ?
                        """, List.of(userId)));

                try (var stmt = sql.prepareStatement(conn)) {
                    stmt.execute();
                }
            }

            JsonObject.Builder userJson = Json.objectBuilder();
            try (var stmt = conn.prepareStatement("""
                    SELECT email, username, bio, image, password_hash
                    FROM realworld."user"
                    WHERE id = ?
                    """)) {
                stmt.setObject(1, userId);
                var rs = stmt.executeQuery();
                rs.next();

                userJson
                        .put("email", rs.getString("email"))
                        .put("username", rs.getString("username"))
                        .put("bio", rs.getString("bio"))
                        .put("image", rs.getString("image"));
            }

            userJson.put("token", getToken(conn, userId));

            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(Json.objectBuilder().put("user", userJson))
            );
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/profiles/(?<username>[a-zA-Z0-9-_]+)")
    void getProfileHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        var username = RouteParams.get(exchange)
                .param("username")
                .orElseThrow();

        try (var conn = db.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    SELECT
                        username,
                        bio,
                        image,
                        EXISTS(
                            SELECT id
                            FROM realworld.follow
                            WHERE from_user_id = ? AND to_user_id = realworld."user".id
                        ) as following
                    FROM realworld."user"
                    WHERE username = ?
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, username);
                var rs = stmt.executeQuery();
                rs.next();
                HttpExchanges.sendResponse(
                        exchange,
                        200,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("profile", Json.objectBuilder()
                                                .put("username", rs.getString("username"))
                                                .put("bio", rs.getString("bio"))
                                                .put("image", rs.getString("image"))
                                                .put("following", ResultSets.getBooleanNotNull(rs, "following")))
                        )
                );
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "POST", pattern = "/api/profiles/(?<username>[a-zA-Z0-9-_]+)/follow")
    void followUserHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }
        var username = RouteParams.get(exchange)
                .param("username")
                .orElseThrow();

        try (var conn = db.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO realworld.follow(from_user_id, to_user_id)
                    VALUES (?, (
                        SELECT id
                        FROM realworld."user"
                        WHERE username = ?
                    ))
                    ON CONFLICT DO NOTHING
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, username);
                stmt.execute();
            }

            try (var stmt = conn.prepareStatement("""
                    SELECT
                        username,
                        bio,
                        image,
                        EXISTS(
                            SELECT id
                            FROM realworld.follow
                            WHERE from_user_id = ? AND to_user_id = realworld."user".id
                        ) as following
                    FROM realworld."user"
                    WHERE username = ?
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, username);
                var rs = stmt.executeQuery();
                rs.next();
                HttpExchanges.sendResponse(
                        exchange,
                        200,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("profile", Json.objectBuilder()
                                                .put("username", rs.getString("username"))
                                                .put("bio", rs.getString("bio"))
                                                .put("image", rs.getString("image"))
                                                .put("following", ResultSets.getBooleanNotNull(rs, "following")))
                        )
                );
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "DELETE", pattern = "/api/profiles/(?<username>[a-zA-Z0-9-_]+)/follow")
    void unfollowUserHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        var username = RouteParams.get(exchange)
                .param("username")
                .orElseThrow();

        try (var conn = db.getConnection()) {
            try (var stmt = conn.prepareStatement("""
                    DELETE FROM realworld.follow
                    WHERE from_user_id = ? AND to_user_id = (
                        SELECT id
                        FROM realworld."user"
                        WHERE username = ?
                    )
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, username);
                stmt.execute();
            }

            try (var stmt = conn.prepareStatement("""
                    SELECT
                        username,
                        bio,
                        image,
                        EXISTS(
                            SELECT id
                            FROM realworld.follow
                            WHERE from_user_id = ? AND to_user_id = realworld."user".id
                        ) as following
                    FROM realworld."user"
                    WHERE username = ?
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, username);
                var rs = stmt.executeQuery();
                rs.next();
                HttpExchanges.sendResponse(
                        exchange,
                        200,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("profile", Json.objectBuilder()
                                                .put("username", rs.getString("username"))
                                                .put("bio", rs.getString("bio"))
                                                .put("image", rs.getString("image"))
                                                .put("following", ResultSets.getBooleanNotNull(rs, "following")))
                        )
                );
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/articles")
    void listArticlesHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        var urlParameters = UrlParameters.parse(exchange.getRequestURI());

        var query = new ArrayList<SQLFragment>();
        query.add(SQLFragment.of("""
                WITH
                    articles AS (
                        SELECT jsonb_build_object(
                            'slug', realworld.article.slug,
                            'title', realworld.article.title,
                            'description', realworld.article.description,
                            'tagList', array(
                                SELECT realworld.tag.name
                                FROM realworld.article_tag
                                LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                WHERE realworld.article_tag.article_id = realworld.article.id
                                ORDER BY realworld.tag.name
                            ),
                            'createdAt', realworld.article.created_at,
                            'updatedAt', realworld.article.updated_at,
                            'favorited', exists(
                                SELECT id
                                FROM realworld.favorite
                                WHERE article_id = realworld.article.id AND user_id = ?
                            ),
                            'favoritesCount', (
                                SELECT count(id)
                                FROM realworld.favorite
                                WHERE article_id = realworld.article.id
                            ),
                            'author', (
                                SELECT jsonb_build_object(
                                    'username', realworld."user".username,
                                    'bio', realworld."user".bio,
                                    'image', realworld."user".image,
                                    'following', exists(
                                        SELECT id
                                        FROM realworld.follow
                                        WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                    )
                                )
                                FROM realworld."user"
                                WHERE realworld."user".id = realworld.article.user_id
                            )
                        )
                        FROM realworld.article
                        WHERE deleted = false
                """, Arrays.asList(userId, userId)));

        urlParameters.firstValue("tag").ifPresent(tag -> {
            query.add(SQLFragment.of("""
                                AND EXISTS(
                                    SELECT id
                                    FROM realworld.article_tag
                                    WHERE realworld.article_tag.article_id = realworld.article.id
                                        AND ? = (
                                            SELECT name
                                            FROM realworld.tag
                                            WHERE realworld.tag.id = realworld.article_tag.tag_id
                                        )
                                )
                    """, List.of(tag)));
        });

        urlParameters.firstValue("favorited").ifPresent(favorited -> {
            query.add(SQLFragment.of("""
                                AND exists(
                                    SELECT id
                                    FROM realworld.favorite
                                    WHERE article_id = realworld.article.id AND user_id = (
                                        SELECT id
                                        FROM realworld."user"
                                        WHERE username = ?
                                    )
                                )
                    """, List.of(favorited)));
        });

        urlParameters.firstValue("author").ifPresent(author -> {
            query.add(SQLFragment.of("""
                                AND realworld.article.user_id IN (
                                    SELECT id
                                    FROM realworld."user"
                                    WHERE realworld."user".username = ?
                                )
                    """, List.of(author)));
        });

        query.add(SQLFragment.of("""
                        ORDER BY realworld.article.created_at DESC
                """));

        String limitString = urlParameters.firstValue("limit").orElse(null);
        if (limitString != null) {
            int limit;
            try {
                limit = Integer.parseInt(limitString);
            } catch (NumberFormatException _) {
                HttpExchanges.sendResponse(
                        exchange,
                        422,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("limit must be an int")))
                        )
                );
                return;
            }

            query.add(SQLFragment.of("""
                            LIMIT ?
                    """, List.of(limit)));
        }

        String offsetString = urlParameters.firstValue("offset").orElse(null);
        if (offsetString != null) {
            int offset;
            try {
                offset = Integer.parseInt(offsetString);
            } catch (NumberFormatException _) {
                HttpExchanges.sendResponse(
                        exchange,
                        422,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("offset must be an int")))
                        )
                );
                return;
            }

            query.add(SQLFragment.of("""
                            OFFSET ?
                    """, List.of(offset)));
        }

        query.add(SQLFragment.of("""
                    )
                SELECT jsonb_build_object(
                    'articles', array(
                        SELECT * FROM articles
                    ),
                    'articlesCount', (
                        SELECT count(*) FROM articles
                    )
                )
                """));


        try (var conn = db.getConnection();
             var stmt = SQLFragment.join("", query).prepareStatement(conn)) {

            var rs = stmt.executeQuery();
            rs.next();

            System.out.println(exchange.getRequestURI());
            System.out.println(Json.read(rs.getObject(1).toString()));
            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(Json.read(rs.getObject(1).toString()))
            );

        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/articles/feed")
    void feedArticlesHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }
        var urlParameters = UrlParameters.parse(exchange.getRequestURI());

        var query = new ArrayList<SQLFragment>();
        query.add(SQLFragment.of("""
                WITH
                    articles AS (
                        SELECT jsonb_build_object(
                            'slug', realworld.article.slug,
                            'title', realworld.article.title,
                            'description', realworld.article.description,
                            'tagList', array(
                                SELECT realworld.tag.name
                                FROM realworld.article_tag
                                LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                WHERE realworld.article_tag.article_id = realworld.article.id
                                ORDER BY realworld.tag.name
                            ),
                            'createdAt', realworld.article.created_at,
                            'updatedAt', realworld.article.updated_at,
                            'favorited', exists(
                                SELECT id
                                FROM realworld.favorite
                                WHERE article_id = realworld.article.id AND user_id = ?
                            ),
                            'favoritesCount', (
                                SELECT count(id)
                                FROM realworld.favorite
                                WHERE article_id = realworld.article.id
                            ),
                            'author', (
                                SELECT jsonb_build_object(
                                    'username', realworld."user".username,
                                    'bio', realworld."user".bio,
                                    'image', realworld."user".image,
                                    'following', exists(
                                        SELECT id
                                        FROM realworld.follow
                                        WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                    )
                                )
                                FROM realworld."user"
                                WHERE realworld."user".id = realworld.article.user_id
                            )
                        )
                        FROM realworld.article
                        WHERE deleted = false AND user_id IN (
                            SELECT from_user_id
                            FROM realworld.follow
                            WHERE from_user_id = ? AND to_user_id = (
                                SELECT id
                                FROM realworld."user"
                                WHERE realworld."user".id = realworld.article.user_id
                            )
                        )
                        ORDER BY realworld.article.created_at DESC
                """, Arrays.asList(userId, userId, userId)));

        String limitString = urlParameters.firstValue("limit").orElse(null);
        if (limitString != null) {
            int limit;
            try {
                limit = Integer.parseInt(limitString);
            } catch (NumberFormatException _) {
                HttpExchanges.sendResponse(
                        exchange,
                        422,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("limit must be an int")))
                        )
                );
                return;
            }

            query.add(SQLFragment.of("""
                            LIMIT ?
                    """, List.of(limit)));
        }

        String offsetString = urlParameters.firstValue("offset").orElse(null);
        if (offsetString != null) {
            int offset;
            try {
                offset = Integer.parseInt(offsetString);
            } catch (NumberFormatException _) {
                HttpExchanges.sendResponse(
                        exchange,
                        422,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("offset must be an int")))
                        )
                );
                return;
            }

            query.add(SQLFragment.of("""
                            OFFSET ?
                    """, List.of(offset)));
        }

        query.add(SQLFragment.of("""
                    )
                SELECT jsonb_build_object(
                    'articles', array(
                        SELECT * FROM articles
                    ),
                    'articlesCount', (
                        SELECT count(*) FROM articles
                    )
                )
                """));


        try (var conn = db.getConnection();
             var stmt = SQLFragment.join("", query).prepareStatement(conn)) {

            var rs = stmt.executeQuery();
            rs.next();

            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(Json.read(rs.getObject(1).toString()))
            );

        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)")
    void getArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        var slug = RouteParams.get(exchange).param("slug").orElseThrow();
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT
                         jsonb_build_object(
                             'article', jsonb_build_object(
                                 'slug', realworld.article.slug,
                                 'title', realworld.article.title,
                                 'description', realworld.article.description,
                                 'body', realworld.article.body,
                                 'tagList', array(
                                     SELECT realworld.tag.name
                                     FROM realworld.article_tag
                                     LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                     WHERE realworld.article_tag.article_id = realworld.article.id
                                     ORDER BY realworld.tag.name
                                 ),
                                 'createdAt', realworld.article.created_at,
                                 'updatedAt', realworld.article.updated_at,
                                 'favorited', exists(
                                     SELECT id
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id AND user_id = ?
                                 ),
                                 'favoritesCount', (
                                     SELECT count(id)
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id
                                 ),
                                 'author', (
                                     SELECT jsonb_build_object(
                                         'username', realworld."user".username,
                                         'bio', realworld."user".bio,
                                         'image', realworld."user".image,
                                         'following', exists(
                                             SELECT id
                                             FROM realworld.follow
                                             WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                         )
                                     )
                                     FROM realworld."user"
                                     WHERE realworld."user".id = realworld.article.user_id
                             )
                         ))
                     FROM realworld.article
                     WHERE deleted = false AND slug = ?
                     """)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            stmt.setString(3, slug);

            var rs = stmt.executeQuery();
            if (!rs.next()) {
                System.out.println(exchange.getRequestURI());
                HttpExchanges.sendResponse(
                        exchange,
                        422,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("No matching article")))
                        )
                );
                return;
            }

            HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                    Json.read(rs.getObject(1).toString())
            ));


        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    record CreateArticleRequest(
            String title,
            String description,
            String body,
            Optional<List<String>> tagList
    ) {
        static CreateArticleRequest fromJson(Json json) {
            return field(json, "article", article -> new CreateArticleRequest(
                    field(article, "title", string()),
                    field(article, "description", string()),
                    field(article, "body", string()),
                    optionalField(article, "tagList", array(string()))
            ));
        }
    }

    String articleSlug(String title) {
        var sb = new StringBuilder(Slugify.builder().build().slugify(title));
        sb.append("-");
        for (int i = 0; i < 8; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    @Route(methods = "POST", pattern = "/api/articles")
    void createArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        var body = JsonBody.read(exchange, CreateArticleRequest::fromJson);

        try (var conn = db.getConnection()) {
            conn.setAutoCommit(false);

            var articleId = UUID.randomUUID();
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO realworld.article(id, user_id, title, slug, description, body)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                stmt.setObject(1, articleId);
                stmt.setObject(2, userId);
                stmt.setString(3, body.title);
                stmt.setString(4, articleSlug(body.title));
                stmt.setString(5, body.description);
                stmt.setString(6, body.body);
                stmt.execute();
            }


            var tagIds = new ArrayList<UUID>();

            var tagList = body.tagList.orElse(List.of());
            for (var tag : tagList) {
                try (var stmt = conn.prepareStatement("""
                            INSERT INTO realworld.tag(name)
                            VALUES (?)
                            ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name
                            RETURNING id
                            """)) {
                    stmt.setString(1, tag);
                    var rs = stmt.executeQuery();
                    rs.next();
                    tagIds.add(rs.getObject("id", UUID.class));
                }
            }


            try (var stmt = conn.prepareStatement("""
                    INSERT INTO realworld.article_tag(article_id, tag_id) 
                    VALUES (?, ?)
                    """)) {
                for (var tagId : tagIds) {
                    stmt.setObject(1, articleId);
                    stmt.setObject(2, tagId);
                    stmt.addBatch();
                    stmt.clearParameters();
                }

                stmt.executeBatch();
            }

            conn.commit();

            try (var stmt = conn.prepareStatement("""
                    SELECT
                        jsonb_build_object(
                            'article', jsonb_build_object(
                                'slug', realworld.article.slug,
                                'title', realworld.article.title,
                                'description', realworld.article.description,
                                'body', realworld.article.body,
                                'tagList', array(
                                    SELECT realworld.tag.name
                                    FROM realworld.article_tag
                                    LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                    WHERE realworld.article_tag.article_id = realworld.article.id
                                    ORDER BY realworld.tag.name
                                ),
                                'createdAt', realworld.article.created_at,
                                'updatedAt', realworld.article.updated_at,
                                'favorited', exists(
                                    SELECT id
                                    FROM realworld.favorite
                                    WHERE article_id = realworld.article.id AND user_id = ?
                                ),
                                'favoritesCount', (
                                    SELECT count(id)
                                    FROM realworld.favorite
                                    WHERE article_id = realworld.article.id
                                ),
                                'author', (
                                    SELECT jsonb_build_object(
                                        'username', realworld."user".username,
                                        'bio', realworld."user".bio,
                                        'image', realworld."user".image,
                                        'following', exists(
                                            SELECT id
                                            FROM realworld.follow
                                            WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                        )
                                    )
                                    FROM realworld."user"
                                    WHERE realworld."user".id = article.user_id
                            )
                        ))
                    FROM realworld.article
                    WHERE deleted = false AND id = ?
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, userId);
                stmt.setObject(3, articleId);

                var rs = stmt.executeQuery();
                rs.next();

                HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                        Json.read(rs.getObject(1).toString())
                ));
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    record UpdateArticleRequest(
            Optional<String> title,
            Optional<String> description,
            Optional<String> body
    ) {
        static UpdateArticleRequest fromJson(Json json) {
            return field(json, "article", article -> new UpdateArticleRequest(
                    optionalField(article, "title", string()),
                    optionalField(article, "description", string()),
                    optionalField(article, "body", string())
            ));
        }

        boolean anyUpdates() {
            return title.isPresent() || description.isPresent() || body.isPresent();
        }
    }

    @Route(methods = "PUT", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)")
    void updateArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        var slug = RouteParams.get(exchange).param("slug").orElseThrow();
        var body = JsonBody.read(exchange, UpdateArticleRequest::fromJson);

        try (var conn = db.getConnection()) {
            UUID articleId = null;
            if (body.anyUpdates()) {
                var sets = new ArrayList<SQLFragment>();

                body.title.ifPresent(title -> {
                    sets.add(SQLFragment.of("title = ?, slug = ?", List.of(title, articleSlug(title)))
                    );
                });

                body.description.ifPresent(description -> {
                    sets.add(SQLFragment.of("description = ?", List.of(description)));
                });

                body.body.ifPresent(description -> {
                    sets.add(SQLFragment.of("body = ?", List.of(description)));
                });

                var sql = SQLFragment.of(
                                """
                                     UPDATE realworld.article
                                     SET
                                     \s\s\s\s
                                     """
                        )
                        .concat(SQLFragment.join(", ", sets))
                        .concat(SQLFragment.of("""
                            
                            WHERE slug = ?
                            RETURNING id
                            """, List.of(slug)));

                try (var stmt = sql.prepareStatement(conn)) {
                    var rs = stmt.executeQuery();
                    if (rs.next()) {
                        articleId = rs.getObject("id", UUID.class);
                    }
                }
            }

            if (articleId == null) {
                HttpExchanges.sendResponse(
                        exchange,
                        401,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("No matching article")))
                        )
                );
                return;
            }

            try (var stmt = conn.prepareStatement("""
                     SELECT
                         jsonb_build_object(
                             'article', jsonb_build_object(
                                 'slug', realworld.article.slug,
                                 'title', realworld.article.title,
                                 'description', realworld.article.description,
                                 'body', realworld.article.body,
                                 'tagList', array(
                                     SELECT realworld.tag.name
                                     FROM realworld.article_tag
                                     LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                     WHERE realworld.article_tag.article_id = realworld.article.id
                                     ORDER BY realworld.tag.name
                                 ),
                                 'createdAt', realworld.article.created_at,
                                 'updatedAt', realworld.article.updated_at,
                                 'favorited', exists(
                                     SELECT id
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id AND user_id = ?
                                 ),
                                'favoritesCount', (
                                     SELECT count(id)
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id
                                 ),
                                 'author', (
                                     SELECT jsonb_build_object(
                                         'username', realworld."user".username,
                                         'bio', realworld."user".bio,
                                         'image', realworld."user".image,
                                         'following', exists(
                                             SELECT id
                                             FROM realworld.follow
                                             WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                         )
                                     )
                                     FROM realworld."user"
                                     WHERE realworld."user".id = realworld.article.user_id
                             )
                         ))
                     FROM realworld.article
                     WHERE deleted = false AND id = ?
                     """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, userId);
                stmt.setObject(3, articleId);

                var rs = stmt.executeQuery();
                rs.next();

                HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                        Json.read(rs.getObject(1).toString())
                ));
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "DELETE", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)")
    void deleteArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }
        var slug = RouteParams.get(exchange).param("slug").orElseThrow();

        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                   UPDATE realworld.article
                       SET deleted = true
                   WHERE user_id = ? AND slug = ?
                   """)) {
            stmt.setObject(1, userId);
            stmt.setObject(2, slug);
            if (stmt.executeUpdate() == 0) {
                HttpExchanges.sendResponse(
                        exchange,
                        401,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("could not delete article")))
                        )
                );
                return;
            }

            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(Json.ofNull())
            );
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "POST", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)/comments")
    void addCommentsToArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        var slug = RouteParams.get(exchange).param("slug").orElseThrow();

        var body = field(JsonBody.read(exchange), "comment", field("body", string()));

        try (var conn = db.getConnection()) {
            UUID articleId;
            try (var stmt = conn.prepareStatement("""
                   SELECT id
                   FROM realworld.article
                   WHERE slug = ?
                   """)) {
                stmt.setObject(1, slug);
                var rs = stmt.executeQuery();
                if (!rs.next()) {
                    HttpExchanges.sendResponse(
                            exchange,
                            401,
                            JsonBody.of(
                                    Json.objectBuilder()
                                            .put("errors", Json.objectBuilder()
                                                    .put("body", Json.arrayBuilder()
                                                            .add("No matching article")))
                            )
                    );
                    return;
                }

                articleId = rs.getObject("id", UUID.class);
            }

            var commentId = UUID.randomUUID();
            try (var stmt = conn.prepareStatement("""
                   INSERT INTO realworld.comment(id, article_id, user_id, body)
                   VALUES (?, ?, ?, ?)
                   RETURNING id
                   """)) {
                stmt.setObject(1, commentId);
                stmt.setObject(2, articleId);
                stmt.setObject(3, userId);
                stmt.setString(4, body);
                stmt.execute();
            }
            try (var stmt = conn.prepareStatement("""
                    SELECT
                        jsonb_build_object(
                            'comment', (
                                SELECT jsonb_build_object(
                                    'id', realworld.comment.id,
                                    'createdAt', realworld.comment.created_at,
                                    'updatedAt', realworld.comment.updated_at,
                                    'body', realworld.comment.body,
                                    'author', (
                                        SELECT jsonb_build_object(
                                            'username', realworld."user".username,
                                            'bio', realworld."user".bio,
                                            'image', realworld."user".image,
                                            'following', exists(
                                                 SELECT id
                                                 FROM realworld.follow
                                                 WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                             )
                                        )
                                        FROM realworld."user"
                                        WHERE realworld."user".id = realworld.comment.user_id
                                    )
                                )
                                FROM realworld.comment
                                WHERE realworld.comment.id = ?
                            )
                        )
                    """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, commentId);

                var rs = stmt.executeQuery();
                rs.next();

                HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                        Json.read(rs.getObject(1).toString())
                ));
            }

        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)/comments")
    void getCommentsFromArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        var slug = RouteParams.get(exchange).param("slug").orElseThrow();

        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                SELECT
                    jsonb_build_object(
                        'comments', array(
                            SELECT jsonb_build_object(
                                'id', realworld.comment.id,
                                'createdAt', realworld.comment.created_at,
                                'updatedAt', realworld.comment.updated_at,
                                'body', realworld.comment.body,
                                'author', (
                                    SELECT jsonb_build_object(
                                        'username', realworld."user".username,
                                        'bio', realworld."user".bio,
                                        'image', realworld."user".image,
                                        'following', exists(
                                             SELECT id
                                             FROM realworld.follow
                                             WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                         )
                                    )
                                    FROM realworld."user"
                                    WHERE realworld."user".id = realworld.comment.user_id
                                )
                            )
                            FROM realworld.comment
                            WHERE realworld.comment.article_id = realworld.article.id
                        )
                    )
                FROM realworld.article WHERE realworld.article.slug = ?
                """)) {
            stmt.setObject(1, userId);
            stmt.setString(2, slug);

            var rs = stmt.executeQuery();

            if (!rs.next()) {
                HttpExchanges.sendResponse(
                        exchange,
                        401,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("No matching article")))
                        )
                );
                return;
            }

            HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                    Json.read(rs.getObject(1).toString())
            ));
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "DELETE", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)/comments/(?<commentId>[a-zA-Z0-9-_]+)")
    void deleteCommentHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }
        var routeParams = RouteParams.get(exchange);
        var slug = routeParams.param("slug").orElseThrow();
        var commentId = UUID.fromString(routeParams.param("commentId").orElseThrow());

        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                UPDATE realworld.comment
                    SET deleted = true
                WHERE
                    realworld.comment.id = ? AND
                    realworld.comment.user_id = ? AND
                    realworld.comment.article_id IN (
                        SELECT id
                        FROM realworld.article
                        WHERE realworld.article.slug = ?
                    )
                """)) {
            stmt.setObject(1, commentId);
            stmt.setObject(2, userId);
            stmt.setObject(3, slug);

            if (stmt.executeUpdate() == 0) {
                HttpExchanges.sendResponse(
                        exchange,
                        401,
                        JsonBody.of(
                                Json.objectBuilder()
                                        .put("errors", Json.objectBuilder()
                                                .put("body", Json.arrayBuilder()
                                                        .add("could not delete article")))
                        )
                );
                return;
            }

            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(Json.ofNull())
            );

        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "POST", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)/favorite")
    void favoriteArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        var slug = RouteParams.get(exchange).param("slug").orElseThrow();

        try (var conn = db.getConnection()) {
            UUID articleId;
            try (var stmt = conn.prepareStatement("""
                    SELECT id
                    FROM realworld.article
                    WHERE deleted = false AND slug = ?
                    """)) {
                stmt.setObject(1, slug);
                var rs = stmt.executeQuery();
                if (!rs.next()) {
                    HttpExchanges.sendResponse(
                            exchange,
                            401,
                            JsonBody.of(
                                    Json.objectBuilder()
                                            .put("errors", Json.objectBuilder()
                                                    .put("body", Json.arrayBuilder()
                                                            .add("No matching article")))
                            )
                    );
                    return;
                }

                articleId = rs.getObject("id", UUID.class);
            }

            try (var stmt = conn.prepareStatement("""
                   INSERT INTO realworld.favorite(article_id, user_id)
                   VALUES (?, ?)
                   """)) {
                stmt.setObject(1, articleId);
                stmt.setObject(2, userId);
                stmt.execute();
            }

            try (var stmt = conn.prepareStatement("""
                     SELECT
                         jsonb_build_object(
                             'article', jsonb_build_object(
                                 'slug', realworld.article.slug,
                                 'title', realworld.article.title,
                                 'description', realworld.article.description,
                                 'body', realworld.article.body,
                                 'tagList', array(
                                     SELECT realworld.tag.name
                                     FROM realworld.article_tag
                                     LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                     WHERE realworld.article_tag.article_id = realworld.article.id
                                     ORDER BY realworld.tag.name
                                 ),
                                 'createdAt', realworld.article.created_at,
                                 'updatedAt', realworld.article.updated_at,
                                 'favorited', exists(
                                     SELECT id
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id AND user_id = ?
                                 ),
                                'favoritesCount', (
                                     SELECT count(id)
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id
                                 ),
                                 'author', (
                                     SELECT jsonb_build_object(
                                         'username', realworld."user".username,
                                         'bio', realworld."user".bio,
                                         'image', realworld."user".image,
                                         'following', exists(
                                             SELECT id
                                             FROM realworld.follow
                                             WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                         )
                                     )
                                     FROM realworld."user"
                                     WHERE realworld."user".id = realworld.article.user_id
                             )
                         ))
                     FROM realworld.article
                     WHERE deleted = false AND id = ?
                     """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, userId);
                stmt.setObject(3, articleId);

                var rs = stmt.executeQuery();
                rs.next();

                HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                        Json.read(rs.getObject(1).toString())
                ));
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "DELETE", pattern = "/api/articles/(?<slug>[a-zA-Z0-9-_]+)/favorite")
    void unfavoriteArticleHandler(HttpExchange exchange) throws IOException {
        var userId = getUserId(exchange);
        if (userId == null) {
            unauthenticated(exchange);
            return;
        }

        var slug = RouteParams.get(exchange).param("slug").orElseThrow();

        try (var conn = db.getConnection()) {
            UUID articleId;
            try (var stmt = conn.prepareStatement("""
                    SELECT id
                    FROM realworld.article
                    WHERE deleted = false AND slug = ?
                    """)) {
                stmt.setObject(1, slug);
                var rs = stmt.executeQuery();
                if (!rs.next()) {
                    HttpExchanges.sendResponse(
                            exchange,
                            401,
                            JsonBody.of(
                                    Json.objectBuilder()
                                            .put("errors", Json.objectBuilder()
                                                    .put("body", Json.arrayBuilder()
                                                            .add("No matching article")))
                            )
                    );
                    return;
                }

                articleId = rs.getObject("id", UUID.class);
            }

            try (var stmt = conn.prepareStatement("""
                   DELETE FROM realworld.favorite
                   WHERE article_id = ? AND user_id = ?
                   """)) {
                stmt.setObject(1, articleId);
                stmt.setObject(2, userId);
                stmt.execute();
            }

            try (var stmt = conn.prepareStatement("""
                     SELECT
                         jsonb_build_object(
                             'article', jsonb_build_object(
                                 'slug', realworld.article.slug,
                                 'title', realworld.article.title,
                                 'description', realworld.article.description,
                                 'body', realworld.article.body,
                                 'tagList', array(
                                     SELECT realworld.tag.name
                                     FROM realworld.article_tag
                                     LEFT JOIN realworld.tag ON realworld.tag.id = realworld.article_tag.tag_id
                                     WHERE realworld.article_tag.article_id = realworld.article.id
                                     ORDER BY realworld.tag.name
                                 ),
                                 'createdAt', realworld.article.created_at,
                                 'updatedAt', realworld.article.updated_at,
                                 'favorited', exists(
                                     SELECT id
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id AND user_id = ?
                                 ),
                                'favoritesCount', (
                                     SELECT count(id)
                                     FROM realworld.favorite
                                     WHERE article_id = realworld.article.id
                                 ),
                                 'author', (
                                     SELECT jsonb_build_object(
                                         'username', realworld."user".username,
                                         'bio', realworld."user".bio,
                                         'image', realworld."user".image,
                                         'following', exists(
                                             SELECT id
                                             FROM realworld.follow
                                             WHERE from_user_id = ? AND to_user_id = realworld."user".id
                                         )
                                     )
                                     FROM realworld."user"
                                     WHERE realworld."user".id = realworld.article.user_id
                             )
                         ))
                     FROM realworld.article
                     WHERE deleted = false AND id = ?
                     """)) {
                stmt.setObject(1, userId);
                stmt.setObject(2, userId);
                stmt.setObject(3, articleId);

                var rs = stmt.executeQuery();
                rs.next();

                HttpExchanges.sendResponse(exchange, 200, JsonBody.of(
                        Json.read(rs.getObject(1).toString())
                ));
            }
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "GET", pattern = "/api/tags")
    void getTagsHandler(HttpExchange exchange) throws IOException {
        try (var conn = db.getConnection();
             var stmt = conn.prepareStatement("""
                     SELECT name FROM realworld.tag
                     """)) {
            var rs = stmt.executeQuery();

            HttpExchanges.sendResponse(
                    exchange,
                    200,
                    JsonBody.of(
                            Json.objectBuilder()
                                    .put("tags",
                                            ResultSets.getList(
                                                    rs,
                                                    r -> Json.of(r.getString("name"))
                                            )
                                    )
                    )
            );
        } catch (SQLException e) {
            throw new UncheckedSQLException(e);
        }
    }

    @Route(methods = "OPTIONS", pattern = "/api/.+")
    void corsHandler(HttpExchange exchange) throws IOException {
        var headers = exchange.getResponseHeaders();
        headers.put("Access-Control-Allow-Origin", List.of("*"));
        headers.put("Access-Control-Allow-Headers", List.of("*"));
        HttpExchanges.sendResponse(
                exchange,
                200,
                Body.empty()
        );
    }

    public void register(RegexRouter.Builder builder) {
        for (var method : this.getClass().getDeclaredMethods()) {
            var route = method.getAnnotation(Route.class);
            if (route != null) {
                HttpHandler handler = exchange -> {
                    try {
                        method.invoke(this, exchange);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException(e);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                };
                builder.route(
                        Arrays.asList(route.methods()),
                        Pattern.compile(route.pattern()),
                        handler
                );
            }
        }
    }
}
