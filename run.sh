#!/usr/bin/env bash
mvnd clean package && java -p ./target/modules -m avaje.realworld
