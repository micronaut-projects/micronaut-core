package io.micronaut.jackson.convert

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.value.ConvertibleValues
import io.micronaut.core.type.Argument
import io.micronaut.json.tree.JsonNode
import spock.lang.Specification

class JsonConverterRegistrarSpec extends Specification {
    def 'array node to collection'() {
        given:
        def ctx = ApplicationContext.run()
        def converter = ctx.getBean(ConversionService)

        expect:
        converter.convert(JsonNode.createArrayNode([JsonNode.createStringNode("foo")]), Argument.of(List, String)).get() == ['foo']
        converter.convert(JsonNode.createArrayNode([JsonNode.createStringNode("foo")]), Argument.of(Set, String)).get() == new HashSet(['foo'])
        converter.convert(JsonNode.createArrayNode([JsonNode.createStringNode("foo")]), Argument.of(SortedSet, String)).get() == new TreeSet(['foo'])
    }

    def 'json node to ConvertibleValues'() {
        given:
        def ctx = ApplicationContext.run()
        def converter = ctx.getBean(ConversionService)

        expect:
        !converter.convert(JsonNode.createArrayNode([JsonNode.createStringNode("foo")]), Argument.of(ConvertibleValues, String)).isPresent()
        converter.convert(JsonNode.createObjectNode(['bar': JsonNode.createStringNode("foo")]), Argument.of(ConvertibleValues, String)).get().asMap(String, String) == ['bar': 'foo']
        !converter.convert(JsonNode.createStringNode("foo"), Argument.of(ConvertibleValues, String)).isPresent()
    }
}
