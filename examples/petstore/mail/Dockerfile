FROM java:openjdk-8u111-alpine
RUN apk --no-cache add curl
CMD java ${JAVA_OPTS} -jar mail-all.jar
COPY build/libs/mail-all.jar .