package io.micronaut.http.body

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.TypeConverterRegistrar
import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Issue
import spock.lang.Specification

@MicronautTest
@Property(name = "spec.name", value = "ConversionPrimitiveHandlerSpec")
@Issue('https://github.com/micronaut-projects/micronaut-core/issues/10698')
class MessageBodyReaderForPrimitivesSpec extends Specification {

    @Inject EmbeddedServer server
    @Inject MessageBodyHandlerRegistry messageBodyHandlerRegistry
    @Inject TestClient client

    @MockBean(bean = TypeConverterRegistrar, named = "NettyConverters")
    TypeConverterRegistrar nettyConverters() {
        // block loading the Netty converters, which is analogous to http-server-netty not being loaded
        Mock(TypeConverterRegistrar.class)
    }

    def 'test boolean output'() {
        expect:
        client.testBoolean()
    }

    def 'test boolean input and output'() {
        expect:
        !client.testBoolean(false)
        client.testBoolean(true)
    }

    def 'test long output'() {
        expect:
        client.testLong() == 1L
    }

    def 'test long input and output'() {
        expect:
        client.testLong(2L) == 2L
    }

    def 'test int output'() {
        expect:
        client.testInt() == 3
    }

    def 'test int input and output'() {
        expect:
        client.testInt(4) == 4
    }

    def 'test MessageBodyReader for boolean exists'() {
        when:
        def reader =
            messageBodyHandlerRegistry
                .findReader(Argument.of(boolean.class), [MediaType.APPLICATION_JSON_TYPE])

        then:
        reader.isPresent()
    }

    def 'test MessageBodyReader for long exists'() {
        when:
        def reader =
            messageBodyHandlerRegistry
                .findReader(Argument.of(long.class), [MediaType.APPLICATION_JSON_TYPE])

        then:
        reader.isPresent()
    }

    def 'test MessageBodyReader for int exists'() {
        when:
        def reader =
            messageBodyHandlerRegistry
                .findReader(Argument.of(int.class), [MediaType.APPLICATION_JSON_TYPE])

        then:
        reader.isPresent()
    }

    @Client("/primitives")
    @Requires(property = "spec.name", value = "ConversionPrimitiveHandlerSpec")
    static interface TestClient {

        @Get("boolean")
        boolean testBoolean();

        @Post("boolean")
        boolean testBoolean(@Body boolean value);

        @Get("long")
        long testLong();

        @Post("long")
        long testLong(@Body long value);

        @Get("int")
        int testInt();

        @Post("int")
        int testInt(@Body int value);

    }

    @Controller("/primitives")
    @Requires(property = "spec.name", value = "ConversionPrimitiveHandlerSpec")
    static class TestController {

        @Get("boolean")
        boolean testBoolean() {
            return true;
        }

        @Post("boolean")
        boolean testBoolean(@Body boolean value) {
            return value;
        }

        @Get("long")
        long testLong() {
            return 1L;
        }

        @Post("long")
        long testLong(@Body long value) {
            return value;
        }

        @Get("int")
        int testInt() {
            return 3;
        }

        @Post("int")
        int testInt(@Body int value) {
            return value;
        }

    }
}
