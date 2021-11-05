package io.micronaut.jackson.codec

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.http.codec.MediaTypeCodec

class JacksonMediaTypeCodecSpec extends AbstractTypeElementSpec {

    def 'no legacy media type codecs registered'() {
        given:
        def context = buildContext('', '', true)

        expect:
        // old codecs should be disabled
        !context.getBeansOfType(MediaTypeCodec).any { it instanceof JacksonMediaTypeCodec }
        // new codecs should be present
        context.getBeansOfType(MediaTypeCodec).any { it instanceof io.micronaut.json.codec.JsonMediaTypeCodec }
        context.getBeansOfType(MediaTypeCodec).any { it instanceof io.micronaut.json.codec.JsonStreamMediaTypeCodec }
        // old codecs can still be requested explicitly
        context.getBean(JsonMediaTypeCodec).class == JsonMediaTypeCodec
        context.getBean(JsonStreamMediaTypeCodec).class == JsonStreamMediaTypeCodec

        cleanup:
        context.close()
    }

    void "test deserialization"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()
        JsonMediaTypeCodec codec = ctx.getBean(JsonMediaTypeCodec)

        when:
        def test = codec.decode(Argument.of(Test), '{"name":"x","label":"y"}')

        then:
        noExceptionThrown()
        test.label == "X"

        cleanup:
        ctx.close()
    }

    @Introspected
    static class Test {

        private final String name

        @Creator
        Test(String name) {
            this.name = name
        }

        String getLabel() {
            name.toUpperCase()
        }
    }
}
