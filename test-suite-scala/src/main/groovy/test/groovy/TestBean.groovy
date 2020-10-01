package test.groovy;

import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class TestBean {
    @Value("server.host")
    String host
}