FROM amazoncorretto:23 AS builder

RUN dnf install -y binutils

RUN jlink \
    #when it comes to jlink, one bad apple spoils the bunch.
    #if postgres was modular, then I could have let jlink take care of finding the JDK modules
    #but since it's not, I have to determine and specify all the modules I need
    --add-modules java.base,java.instrument,java.logging,java.management,java.naming,java.net.http,java.sql,jdk.httpserver \
    --strip-debug \
    --no-header-files \
    --no-man-pages \
    --output /jre

    # If all my dependencies were modular, I would have done this
    # COPY /target/modules /modules
    # RUN jlink -p /modules \
    # --add-modules avaje.realworld \
    # --strip-debug \
    # --no-header-files \
    # --no-man-pages \
    # --output /jre

FROM gcr.io/distroless/cc-debian12

COPY --from=builder /jre /jre
# COPY native lib for java
COPY --from=builder /usr/lib64/libz.so.1 /lib/x86_64-linux-gnu/libz.so.1
# for ARM
# COPY --from=builder /usr/lib64/libz.so.1 /lib/aarch64-linux-gnu/libz.so.1

COPY /target/modules /modules

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Duser.timezone=\"America/New_York\""

ENTRYPOINT ["/jre/bin/java","-p", "/modules", "-m", "avaje.realworld"]

# If all my dependencies were modular, I would have done this
# ENTRYPOINT ["/jre/bin/java", "-m", "avaje.realworld"]