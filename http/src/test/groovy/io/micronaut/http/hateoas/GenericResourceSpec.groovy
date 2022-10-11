package io.micronaut.http.hateoas

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GenericResourceSpec extends Specification {
    def 'deserialization'() {
        given:
        JsonMapper mapper = ApplicationContext.run().getBean(JsonMapper)
        JsonError original = new JsonError('foo')
                .path('/p')
                .embedded('bar', new JsonError('baz').path('/q'))
        String json = new String(mapper.writeValueAsBytes(original), StandardCharsets.UTF_8)

        when:
        Resource parsed = mapper.readValue(json, Argument.of(Resource))
        then:
        parsed.additionalProperties == ['path': '/p', 'message': 'foo']
        parsed.embedded.size() == 1
        parsed.embedded.collect() == ['bar']
        parsed.embedded.get('bar').get()[0].additionalProperties == ['path': '/q', 'message': 'baz']

        when:
        String reserialized = new String(mapper.writeValueAsBytes(parsed), StandardCharsets.UTF_8)
        Resource reparsed = mapper.readValue(reserialized, Argument.of(Resource))

        then:
        reparsed == parsed
    }
}
