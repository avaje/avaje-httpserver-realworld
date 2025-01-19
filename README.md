# ![RealWorld Example App](logo.png)

> ### Avaje + Ebean + JDK HTTP Server codebase containing real world examples (CRUD, JWT auth, advanced patterns, etc) that adheres to the [RealWorld](https://github.com/gothinkster/realworld) spec and API.

This codebase was created to demonstrate a lightweight fully modular backend application built with the JDK HTTP Server including CRUD operations, authentication, routing, pagination, and more.

For more information on how this works with other frontends/backends, head to the [RealWorld](https://github.com/gothinkster/realworld) repo.

# How it works

This is built up of a few components. Primarily

* The [`jdk.httpserver`](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html) module as the HTTP Server
* [Ebean ORM](https://ebean.io) as the ORM
* [Postgresql](https://postgresql.org) for the database
* [RainbowGum](https://github.com/jstachio/rainbowgum) for logging

Then, serving specific tasks:

* [Avaje Jex](https://avaje.io/jex) for providing routing abstractions over the `jdk.httpserver`.
* [Avaje Jsonb](https://avaje.io/jsonb) for reading and writing JSON
* [Avaje HTTP Server](https://avaje.io/http-server) for generating routing code for Jex from Jax-RS style controllers
* [Avaje Inject](https://avaje.io/inject) for Dependency Injection
* [Avaje Config](https://avaje.io/config) for reading configuration files
* [Avaje Validation](https://avaje.io/validator) for bean validation
* [java-jwt](https://github.com/auth0/java-jwt) for JWT token validation
* [slugify](https://github.com/slugify/slugify) for turning text into a url sage slug
* [org.slf4j](https://github.com/qos-ch/slf4j) as a logging facade


# Getting started

## Prerequisites

* Java 21 or above
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
* run it through terminal (`./run.sh"`)
* run it through docker

```
$ docker build -t real .
$ docker run real
```

The `.env` file for this project is committed to the repo. Note that in general this is a bad idea/practice, but the
only secrets here are for the local database connection so it's fine.
