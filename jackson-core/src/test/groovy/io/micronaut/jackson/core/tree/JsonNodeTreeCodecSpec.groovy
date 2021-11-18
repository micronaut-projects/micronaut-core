package io.micronaut.jackson.core.tree

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.core.json.JsonWriteFeature
import io.micronaut.json.JsonStreamConfig
import spock.lang.Specification

class JsonNodeTreeCodecSpec extends Specification {
    def roundtrip() {
        given:
        def treeCodec = JsonNodeTreeCodec.getInstance()
        def factory = new JsonFactory()

        def parsed = treeCodec.readTree(factory.createParser(json))

        def emitted = new StringWriter()
        def emittedGenerator = factory.createGenerator(emitted)
        treeCodec.writeTree(emittedGenerator, parsed)
        emittedGenerator.close()

        expect:
        json == emitted.toString()

        where:
        json << [
                'null',
                '"foo"',
                'true',
                'false',
                '42',
                '123.5',
                '12459561964195489518297537451',
                //'12459561964195489518297537451.5', bigdecimal
                '{"foo":"bar"}',
                '{"foo":{"bar":"baz"}}',
                '{"foo":["bar","baz"]}',
                '["foo","bar","baz"]',
        ]
    }

    def 'roundtrip bignum'() {
        given:
        def treeCodec = JsonNodeTreeCodec.getInstance().withConfig(JsonStreamConfig.DEFAULT
                .withUseBigIntegerForInts(true)
                .withUseBigDecimalForFloats(true))
        def factory = JsonFactory.builder().enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS).build()

        def parsed = treeCodec.readTree(factory.createParser(json))

        def emitted = new StringWriter()
        def emittedGenerator = factory.createGenerator(emitted)
        treeCodec.writeTree(emittedGenerator, parsed)
        emittedGenerator.close()

        expect:
        json == emitted.toString()

        parsed.numberValue instanceof BigInteger ||
                parsed.numberValue instanceof BigDecimal ||
                (parsed.numberValue instanceof Float && !Float.isFinite(parsed.numberValue))

        where:
        json << [
                '12459561964195489518297537451',
                '12459561964195489518297537451.5',
                'NaN', 'Infinity', '-Infinity'
        ]
    }
}
