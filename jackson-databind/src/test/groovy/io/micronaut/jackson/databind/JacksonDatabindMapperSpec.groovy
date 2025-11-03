package io.micronaut.jackson.databind

import tools.jackson.core.JsonParser
import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.module.SimpleModule
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class JacksonDatabindMapperSpec extends Specification {
    def "test default parsing to JsonNode"() {
        given:
        def mapper = JsonMapper.createDefault()

        expect:
        mapper.readValue('{}', Argument.of(JsonNode)) == JsonNode.createObjectNode([:])
    }

    def 'parsing to JsonNode'() {
        given:
        def ctx = ApplicationContext.run()
        def mapper = ctx.getBean(JsonMapper)

        expect:
        mapper.readValue('{}', Argument.of(JsonNode)) == JsonNode.createObjectNode([:])

        cleanup:
        ctx.close()
    }

    def 'parsing from JsonNode uses the right object codec'() {
        given:
        def objectMapper = new ObjectMapper()
        objectMapper = objectMapper.rebuild().addModule(new SimpleModule() {
            {
                addDeserializer(TestBean, new TestDeserializer())
            }
        }).build()
        def jsonMapper = new JacksonDatabindMapper(objectMapper)

        expect:
        jsonMapper.readValueFromTree(JsonNode.createNumberNode(42), TestBean).value == BigInteger.valueOf(42)

        when:
        def testBean = new TestBean()
        jsonMapper.updateValueFromTree(testBean, JsonNode.createNumberNode(42))
        then:
        testBean.value == BigInteger.valueOf(42)
    }

    private static final class TestBean {
        BigInteger value
    }

    private static final class TestDeserializer extends ValueDeserializer<TestBean> {
        @Override
        TestBean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
            return new TestBean(value: p.objectReadContext().readValue(p, BigInteger))
        }

        @Override
        TestBean deserialize(JsonParser p, DeserializationContext ctxt, TestBean intoValue) throws IOException {
            intoValue.value = p.objectReadContext().readValue(p, BigInteger)
            return intoValue
        }
    }
}
