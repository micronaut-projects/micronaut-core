package io.micronaut.tracing.jaeger

import io.jaegertracing.internal.propagation.B3TextMapCodec
import io.jaegertracing.internal.propagation.BinaryCodec
import io.jaegertracing.internal.propagation.TextMapCodec
import io.jaegertracing.internal.propagation.TraceContextCodec
import io.jaegertracing.spi.Codec
import io.micronaut.context.ApplicationContext
import io.opentracing.propagation.Binary
import io.opentracing.propagation.Format
import io.opentracing.propagation.TextMap
import spock.lang.Specification

class JaegerConfigurationSpec extends Specification {

    void "test reporter configuration"() {
        given:
        def ctx = ApplicationContext.run(
                'tracing.jaeger.enabled':'true',
                'tracing.jaeger.sender.agentHost':'foo',
                'tracing.jaeger.sender.agentPort':9999
        )
        def config = ctx.getBean(JaegerConfiguration).configuration

        expect:
        config.reporter.senderConfiguration.agentHost == 'foo'
        config.reporter.senderConfiguration.agentPort == 9999

        cleanup:
        ctx.close()
    }

    /**
     * @author Burt Beckwith
     * @autohr Manuel Martin
     */
    void "test codec configuration W3C"() {
        given:
        def ctx = ApplicationContext.run(
                'tracing.jaeger.enabled':'true',
                'tracing.jaeger.codec.codecs':'W3C'
        )
        def config = ctx.getBean(JaegerConfiguration.JaegerCodecConfiguration).getCodecConfiguration()
        Map<Format<?>, List<Codec<TextMap>>> codecs = config.getCodecs()
        List<Codec<TextMap>> headerCodecs = codecs.get(Format.Builtin.HTTP_HEADERS)
        List<Codec<TextMap>> textMapCodecs = codecs.get(Format.Builtin.TEXT_MAP)

        expect:
        codecs.size() == 2
        headerCodecs.size() == 1
        textMapCodecs.size() == 1
        // CHECK THE ONLY WAY YOU CAN GET W3C
        headerCodecs.get(0) instanceof TraceContextCodec
        textMapCodecs.get(0) instanceof TraceContextCodec

        /*
        // package io.jaegertracing;
        public CodecConfiguration withPropagation(Propagation propagation) {
            switch (propagation) {
                case JAEGER:
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TextMapCodec(true));
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new TextMapCodec(false));
                    addBinaryCodec(binaryCodecs, Format.Builtin.BINARY, new BinaryCodec());
                    break;
                case B3:
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new B3TextMapCodec.Builder().build());
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new B3TextMapCodec.Builder().build());
                    break;
                case W3C: // CHECK THE ONLY WAY YOU CAN GET W3C
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TraceContextCodec.Builder().build());
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new TraceContextCodec.Builder().build());
                    break;
                default:
                    log.error("Unhandled propagation format '" + propagation + "'");
            }
            return this;
        }
        */

        cleanup:
        ctx.close()
    }

    /**
     * @author Burt Beckwith
     * @autohr Manuel Martin
     */
    void "test codec configuration W3C B3"() {
        given:
        def ctx = ApplicationContext.run(
                'tracing.jaeger.enabled':'true',
                'tracing.jaeger.codec.codecs':'W3C,B3' // the order is important
        )
        def config = ctx.getBean(JaegerConfiguration.JaegerCodecConfiguration).getCodecConfiguration()
        Map<Format<?>, List<Codec<TextMap>>> codecs = config.getCodecs()
        List<Codec<TextMap>> headerCodecs = codecs.get(Format.Builtin.HTTP_HEADERS)
        List<Codec<TextMap>> textMapCodecs = codecs.get(Format.Builtin.TEXT_MAP)

        println("CODECS=" + codecs)
        println("HEADER CODECS=" + headerCodecs)
        println("TEXTMAP CODECS=" + textMapCodecs)

        expect:
        codecs.size() == 2 // HTTP_HEADERS & TEXT_MAP
        headerCodecs.size() == 2 // 'TraceContextCodec, B3TextMapCodec'
        textMapCodecs.size() == 2 // 'TraceContextCodec, B3TextMapCodec'
        headerCodecs.get(0) instanceof TraceContextCodec // W3C
        headerCodecs.get(1) instanceof B3TextMapCodec // B3
        textMapCodecs.get(0) instanceof TraceContextCodec // W3C
        textMapCodecs.get(1) instanceof B3TextMapCodec // B3

        /*
        // package io.jaegertracing;
        public CodecConfiguration withPropagation(Propagation propagation) {
            switch (propagation) {
                case JAEGER:
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TextMapCodec(true));
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new TextMapCodec(false));
                    addBinaryCodec(binaryCodecs, Format.Builtin.BINARY, new BinaryCodec());
                    break;
                case B3:
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new B3TextMapCodec.Builder().build());
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new B3TextMapCodec.Builder().build());
                    break;
                case W3C: // CHECK THE ONLY WAY YOU CAN GET W3C
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TraceContextCodec.Builder().build());
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new TraceContextCodec.Builder().build());
                    break;
                default:
                    log.error("Unhandled propagation format '" + propagation + "'");
            }
            return this;
        }
        */

        cleanup:
        ctx.close()
    }

    /**
     * @author Burt Beckwith
     * @autohr Manuel Martin
     */
    void "test codec configuration W3C B3 JAEGER"() {
        given:
        def ctx = ApplicationContext.run(
                'tracing.jaeger.enabled':'true',
                'tracing.jaeger.codec.codecs':'W3C,B3,JAEGER'
        )
        def config = ctx.getBean(JaegerConfiguration.JaegerCodecConfiguration).getCodecConfiguration()
        Map<Format<?>, List<Codec<TextMap>>> codecs = config.getCodecs()
        Map<Format<?>, List<Codec<Binary>>> binaryCodecs = config.getBinaryCodecs()

        List<Codec<TextMap>> headerCodecs = codecs.get(Format.Builtin.HTTP_HEADERS)
        List<Codec<TextMap>> textMapCodecs = codecs.get(Format.Builtin.TEXT_MAP)
        List<Codec<Binary>> jaegerCodecs = binaryCodecs.get(Format.Builtin.BINARY)

        expect:
        codecs.size() == 2 // HTTP_HEADERS & TEXT_MAP
        binaryCodecs.size() == 1 // BINARY

        headerCodecs.size() == 3 // 'TraceContextCodec, B3TextMapCodec, TextMapCodec'
        textMapCodecs.size() == 3 // 'TraceContextCodec, B3TextMapCodec, TextMapCodec'
        jaegerCodecs.size() == 1 // 'BinaryCodec'

        headerCodecs.get(0) instanceof TraceContextCodec // W3C
        headerCodecs.get(1) instanceof B3TextMapCodec // B3
        headerCodecs.get(2) instanceof TextMapCodec // JAEGER

        textMapCodecs.get(0) instanceof TraceContextCodec // W3C
        textMapCodecs.get(1) instanceof B3TextMapCodec // B3
        textMapCodecs.get(2) instanceof TextMapCodec // JAEGER

        jaegerCodecs.get(0) instanceof BinaryCodec // JAEGER

        /*
        // package io.jaegertracing;
        public CodecConfiguration withPropagation(Propagation propagation) {
            switch (propagation) {
                case JAEGER:
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TextMapCodec(true));
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new TextMapCodec(false));
                    addBinaryCodec(binaryCodecs, Format.Builtin.BINARY, new BinaryCodec());
                    break;
                case B3:
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new B3TextMapCodec.Builder().build());
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new B3TextMapCodec.Builder().build());
                    break;
                case W3C: // CHECK THE ONLY WAY YOU CAN GET W3C
                    addCodec(codecs, Format.Builtin.HTTP_HEADERS, new TraceContextCodec.Builder().build());
                    addCodec(codecs, Format.Builtin.TEXT_MAP, new TraceContextCodec.Builder().build());
                    break;
                default:
                    log.error("Unhandled propagation format '" + propagation + "'");
            }
            return this;
        }
        */

        cleanup:
        ctx.close()
    }

}
