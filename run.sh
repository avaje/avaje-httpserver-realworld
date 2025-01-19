#!/usr/bin/env bash
mvn clean package && \ 
java -p ./target/modules -m avaje.realworld
