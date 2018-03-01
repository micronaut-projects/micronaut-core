FROM java:openjdk-8u111-alpine
RUN apk --no-cache add curl
CMD java ${JAVA_OPTS} -jar comments-all.jar
HEALTHCHECK --start-period=30s --interval=5s CMD curl http://localhost:8920/health || exit 1
COPY build/libs/comments-all.jar .