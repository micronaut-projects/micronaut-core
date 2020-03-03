package io.micronaut.tracing.jaeger

import io.jaegertracing.Configuration
import io.micronaut.context.ApplicationContext
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
}
