package io.micronaut.jackson.databind

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class JacksonDatabindMapperSpec extends Specification {
    def 'parsing to JsonNode'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        expect:
        mapper.readValue('{}', Argument.of(JsonNode)) == JsonNode.createObjectNode([:])

        cleanup:
        ctx.close()
    }
}
