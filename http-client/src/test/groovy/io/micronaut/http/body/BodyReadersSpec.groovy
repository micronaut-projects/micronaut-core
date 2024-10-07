package io.micronaut.http.body

import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.core.type.Argument
import io.micronaut.http.MediaType
import io.micronaut.http.client.netty.DefaultHttpClient
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

class BodyReadersSpec extends Specification {

    @AutoCleanup
    DefaultHttpClient httpClient = DefaultHttpClient.builder().build()

    @Unroll
    void "test type handlers"() {
        when:
            def reader = httpClient.getHandlerRegistry()
                    .findReader(Argument.of(type), MediaType.TEXT_PLAIN_TYPE)

        then:
            reader.isPresent()

        when:
            def writer = httpClient.getHandlerRegistry()
                    .findWriter(Argument.of(type), MediaType.TEXT_PLAIN_TYPE)

        then:
            writer.isPresent()

        where:
            type << [BigDecimal, String, CharSequence, Double, MyBean]
    }

    @Unroll
    void "test json type handlers"() {
        when:
            def reader = httpClient.getHandlerRegistry()
                    .findReader(Argument.of(type), MediaType.APPLICATION_JSON_TYPE)

        then:
            reader.isPresent()

        when:
            def writer = httpClient.getHandlerRegistry()
                    .findWriter(Argument.of(type), MediaType.APPLICATION_JSON_TYPE)

        then:
            writer.isPresent()

        where:
            type << [BigDecimal, String, CharSequence, Double, MyBean]
    }

    @Unroll
    void "test byte type handlers"() {
        when:
            def reader = httpClient.getHandlerRegistry()
                    .findReader(Argument.of(type))

        then:
            reader.isPresent()

        when:
            def writer = httpClient.getHandlerRegistry()
                    .findWriter(Argument.of(type))

        then:
            writer.isPresent()

        where:
            type << [byte[], ByteBuffer]
    }


    static class MyBean {}

}
