FROM java:openjdk-8u111-alpine
RUN apk --no-cache add curl
CMD java ${JAVA_OPTS} -jar comments-all.jar
COPY build/libs/comments-all.jar .