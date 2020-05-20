package io.micronaut.http.client.convert

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class GenericsJacksonSerdeSpec extends Specification {
    @Shared @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared @AutoCleanup RxHttpClient client = embeddedServer.applicationContext
                                                        .createBean(RxHttpClient, embeddedServer.getURL())

    void "test ser/deser body with generics"() {

        when:
        def response = client.exchange(HttpRequest.POST("/generics-test", new WrappedData<Token>("1", new Token("test"))), Token)
                .blockingFirst()

        then:
        response.body().value == 'test'
    }

    void "test deser body controller with generics - Issue #3202"() {

                when:
                def response = client.exchange(HttpRequest.POST("/generics-inherited/body", new Demo("value")), Demo)
                        .blockingFirst()

                then:
                response.body().name == 'value'
     }

    @Controller("/generics-test")
    static final class GenericsController {
        @Post(consumes = "application/json", produces = "application/json")
        Token create(@Body WrappedData<Token> value) {
            return value.getData()
        }
    }

    @Introspected
    static final class WrappedData<T> {
        private final String id
        private final T data

        @JsonCreator
        WrappedData(@JsonProperty("id") String id, @JsonProperty("data") T data) {
            this.id = id
            this.data = data
        }

        String getId() {
            return id
        }

        T getData() {
            return data
        }
    }

    static final class Token {
        private final String value

        Token(String value) {
            if (value.length() < 3) {
                throw new IllegalArgumentException("Some invalid condition: " + value)
            }
            this.value = value
        }

        @JsonValue
        String getValue() {
            return value
        }
    }

    static abstract class AbstractOperations<T> {

        @Post("/body")
        T base(@Body T entity) {
            return entity
        }

    }

    @Controller("/generics-inherited")
    static class DemoController extends AbstractOperations<Demo> {

        @Get("/hello")
        String hello() {
            return "Hello world!"
        }

    }

    @Introspected
    static class Demo {
        private String name

        public Demo() {

        }

        public Demo(String name) {
            this.name = name;
        }

        public String getName() {
            return name
        }

        public void setName(String name) {
            this.name = name
        }
    }
}
