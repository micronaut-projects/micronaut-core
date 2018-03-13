FROM java:openjdk-8u111-alpine
RUN apk --no-cache add curl
CMD java ${JAVA_OPTS} -jar storefront-all.jar
COPY build/libs/storefront-all.jar .