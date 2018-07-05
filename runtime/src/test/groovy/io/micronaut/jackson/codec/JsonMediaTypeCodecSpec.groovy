package io.micronaut.jackson.codec

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import spock.lang.Specification

class JsonMediaTypeCodecSpec extends Specification {

    void "test additional type configuration"() {
        given:
        ApplicationContext ctx = ApplicationContext.run([
                'micronaut.codec.json.additionalTypes': ['text/javascript']
        ])

        when:
        JsonMediaTypeCodec codec = ctx.getBean(JsonMediaTypeCodec)

        then:
        codec.mediaTypes.size() == 2
        codec.mediaTypes.contains(MediaType.of("text/javascript"))
        codec.mediaTypes.contains(MediaType.APPLICATION_JSON_TYPE)

        cleanup:
        ctx.close()
    }
}
