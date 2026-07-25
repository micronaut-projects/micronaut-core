open module io.micronaut.testsuite.jpms {
    requires io.micronaut.context;
    requires io.micronaut.http;
    requires io.micronaut.http.client;
    requires io.micronaut.http.client.jdk;
    requires io.micronaut.http.server.netty;
    requires io.micronaut.jackson.databind;

    requires org.junit.jupiter.api;
}
