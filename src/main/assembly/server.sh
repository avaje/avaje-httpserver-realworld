DIR=$(dirname "$0")
java --module-path $DIR/../lib \
  --add-modules dev.mccue.jdk.httpserver.realworld \
  --module dev.mccue.jdk.httpserver.realworld/dev.mccue.jdk.httpserver.realworld.Main "$@"