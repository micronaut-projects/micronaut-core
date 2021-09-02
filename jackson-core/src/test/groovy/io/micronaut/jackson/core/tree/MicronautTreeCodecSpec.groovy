package io.micronaut.jackson.core.tree

import com.fasterxml.jackson.core.JsonFactory
import io.micronaut.json.JsonStreamConfig
import spock.lang.Specification

class MicronautTreeCodecSpec extends Specification {
    def roundtrip() {
        given:
        def treeCodec = MicronautTreeCodec.getInstance()
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
        def treeCodec = MicronautTreeCodec.getInstance().withConfig(JsonStreamConfig.DEFAULT
                .withUseBigIntegerForInts(true)
                .withUseBigDecimalForFloats(true))
        def factory = new JsonFactory()

        def parsed = treeCodec.readTree(factory.createParser(json))

        def emitted = new StringWriter()
        def emittedGenerator = factory.createGenerator(emitted)
        treeCodec.writeTree(emittedGenerator, parsed)
        emittedGenerator.close()

        expect:
        json == emitted.toString()

        parsed.numberValue instanceof BigInteger || parsed.numberValue instanceof BigDecimal

        where:
        json << [
                '12459561964195489518297537451',
                '12459561964195489518297537451.5'
        ]
    }
}
