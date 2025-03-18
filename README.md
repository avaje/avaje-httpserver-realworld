# ![RealWorld Example App](logo.png)

> ### Avaje + Ebean + JDK HTTP Server codebase containing real world examples (CRUD, JWT auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld) spec and API.

This codebase was created to demonstrate a lightweight fully modular backend application built with the JDK HTTP Server including CRUD operations, authentication, routing, pagination, and more.

For more information on how this works with other frontends/backends, head to the [RealWorld](https://github.com/gothinkster/realworld) repo.

It is deployed to https://avaje-httpserver-realworld.onrender.com

# How it works

This is built up of a few components. Primarily:

* The [`jdk.httpserver`](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html) module as the HTTP Server implementation.
* [Avaje Jex](https://avaje.io/jex) to configure and provide routing abstraction over the `jdk.httpserver`. This is the API programmed against
* [Ebean ORM](https://ebean.io) for the Connection Pooling and ORM
* [Postgresql](https://postgresql.org) as the database
* [RainbowGum](https://github.com/jstachio/rainbowgum) for logging

Then, serving specific tasks:

* [Avaje Jsonb](https://avaje.io/jsonb) for reading and writing JSON
* [Avaje HTTP Server](https://avaje.io/http-server) for generating routing code for Jex from Jax-RS style controllers
* [Avaje Inject](https://avaje.io/inject) for Dependency Injection
* [Avaje Config](https://avaje.io/config) for reading configuration files
* [Avaje Validation](https://avaje.io/validator) for bean validation
* [dev.mccue.jdbc](https://github.com/bowbahdoe/jdbc) for `SQLFragment`
* [java-jwt](https://github.com/auth0/java-jwt) for JWT token validation
* [org.slf4j](https://github.com/qos-ch/slf4j) as a logging facade
* [slugify](https://github.com/slugify/slugify) for turning text into a url sage slug

# Getting started

## Prerequisites

* Java 24 or above
* MyBatis (can be installed with [SDKMAN](https://sdkman.io/sdks#mybatis))
* Docker

## Usage

### 1. Start up postgres container

```
$ docker compose up -d
```

### 2. Apply the migrations to the database

```
$ cd migrations
$ migrate up
$ cd ..
```
### 3. Run the server

You can either 

* open the project in your editor
* run it through terminal (`./run.sh"`)
* run it through docker

```
$ docker build -t real .
$ docker run real
```

You can then use the [provided postman collection](https://github.com/avaje/avaje-httpserver-realworld/blob/main/Conduit.postman_collection.json) to send requests.

The `.env` file for this project is committed to the repo. Note that in general this is a bad idea/practice, but the
only secrets here are for the local database connection so it's fine.


