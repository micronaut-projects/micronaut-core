package io.micronaut.jackson.databind

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
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
        objectMapper.registerModule(new SimpleModule() {
            {
                addDeserializer(TestBean, new TestDeserializer())
            }
        })
        def jsonMapper = new JacksonDatabindMapper(objectMapper)

        expect:
        jsonMapper.readValueFromTree(JsonNode.createNumberNode(42), TestBean).value == BigInteger.valueOf(42)

        when:
        def testBean = new TestBean()
        jsonMapper.updateValueFromTree(testBean, JsonNode.createNumberNode(42))
        then:
        testBean.value == BigInteger.valueOf(42)
    }

    private static class TestBean {
        BigInteger value
    }

    private static class TestDeserializer extends JsonDeserializer<TestBean> {
        @Override
        TestBean deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            return new TestBean(value: p.codec.readValue(p, BigInteger))
        }

        @Override
        TestBean deserialize(JsonParser p, DeserializationContext ctxt, TestBean intoValue) throws IOException {
            intoValue.value = p.codec.readValue(p, BigInteger)
            return intoValue
        }
    }
}
