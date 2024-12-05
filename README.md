# ![RealWorld Example App](logo.png)

> ### JDK HTTP Server codebase containing real world examples (CRUD, auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld) spec and API.

This codebase was created to demonstrate a fully fledged fullstack application built with the JDK HTTP Server including CRUD operations, authentication, routing, pagination, and more.

For more information on how to this works with other frontends/backends, head over to the [RealWorld](https://github.com/gothinkster/realworld) repo.

This is deployed [here](https://jdk-httpserver-realworld.onrender.com/)

# How it works

This is built up of a few components. Primarily

* The [`jdk.httpserver`](https://docs.oracle.com/en/java/javase/22/docs/api/jdk.httpserver/module-summary.html) module which provides the API that is programmed against
* [Jetty](https://github.com/jetty/jetty.project) which provides the actual backing implementation for `jdk.httpserver`
* [Postgresql](https://postgresql.org) for the database
* [RainbowGum](https://github.com/jstachio/rainbowgum) for logging

Then, serving specific tasks:

* [dev.mccue.jdk.httpserver](https://github.com/bowbahdoe/jdk-httpserver) for providing a `Body` abstraction
* [dev.mccue.jdk.httpserver.regexrouter](https://github.com/bowbahdoe/jdk-httpserver-regexrouter) for basic request routing
* [dev.mccue.json](https://github.com/bowbahdoe/json) for reading and writing JSON
* [dev.mccue.jdk.httpserver.json](https://github.com/bowbahdoe/jdk-httpserver-json) for using JSON as a `Body` and reading it from `HttpExchange`s
* [dev.mccue.urlparameters](https://github.com/bowbahdoe/urlparameters) for parsing query params
* [dev.mccue.jdbc](https://github.com/bowbahdoe/jdbc) for `UncheckedSQLException` and `SQLFragment`
* [io.github.cdimascio.dotenv.java](https://github.com/cdimascio/dotenv-java) for local development `.env` files
* [slugify](https://github.com/slugify/slugify) for turning text into a url sage slug
* [com.zaxxer.hikari](https://github.com/brettwooldridge/HikariCP) for connection pooling
* [bcrypt](https://github.com/patrickfav/bcrypt) for password salt and hashing
* [org.slf4j](https://github.com/qos-ch/slf4j) as a logging facade

Almost all the code is contained in the [`RealWorldAPI`](https://github.com/bowbahdoe/jdk-httpserver-realworld/blob/main/src/main/java/dev/mccue/jdk/httpserver/realworld/RealWorldAPI.java) class. If any of the choices made here offend your sensibilities
I encourage forking and showing the way you would prefer it be done. If you think something is done in a subpar way or
is otherwise objectively broken please open an issue.

Specifically, I would encourage folks to try and

* Split up the `RealWorldAPI` class. Where are the natural boundaries?
* Try using their database abstraction of choice. What would this look like with `Hibernate`, `JOOQ`, or `JDBI`? Would there be fewer or more round trips to the database?
* Try using their JSON library of choice. 
* Try to do the whole persistence/service/etc. split. Does that make things better?
* Add unit tests. For this exact thing there are already API tests I was able to just use, but how would testing look with JUnit?
* etc.

I personally see a lot of areas for improvement once string templates are real. Counting `?`s in big queries is maybe the biggest
remaining "raw" JDBC shortcoming.

# Getting started

## Prerequisites

* Java 22 or above
* SDKMan
* Docker

## Usage

First, start up postgres

```
$ docker compose up -d
```

Then install MyBatis Migrations. This is currently easiest to do with SDKMan.

```
$ sdk install mybatis
```

Apply the migrations to the database

```
$ cd migrations
$ migrate up
$ cd ..
```

Then to run the server either 

* open the project in your editor
* run it through maven (`./mvnw exec:java -Dexec.mainClass="dev.mccue.jdk.httpserver.realworld.Main"`)
* run it through docker

```
$ docker build -t realworld .
$ docker run realworld
```

To develop locally you only need `docker compose up -d` then to open the project in your editor.

The `.env` file for this project is committed to the repo. Note that in general this is a bad idea/practice, but the
only secrets here are for the local database connection so its fine.