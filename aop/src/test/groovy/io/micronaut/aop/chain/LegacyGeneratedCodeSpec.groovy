package io.micronaut.aop.chain

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import spock.lang.Specification

class LegacyGeneratedCodeSpec extends Specification {

    void "the warning names the target and the generated class that called the entry point"() {
        given:
        Logger logger = (Logger) LoggerFactory.getLogger(LegacyGeneratedCode)
        ListAppender<ILoggingEvent> appender = new ListAppender<>()
        appender.start()
        logger.addAppender(appender)
        String target = LegacyGeneratedCode.describe(LegacyGeneratedCodeSpec, "someMethod")

        when:
        LegacyGeneratedCaller.call("InterceptorChain.resolveAroundInterceptors", target)
        LegacyGeneratedCaller.call("InterceptorChain.resolveAroundInterceptors", target)

        then:
        appender.list.size() == 1
        def message = appender.list[0].formattedMessage
        message.startsWith("InterceptorChain.resolveAroundInterceptors was called for io.micronaut.aop.chain.LegacyGeneratedCodeSpec#someMethod by io.micronaut.aop.chain.LegacyGeneratedCaller")
        message.contains("Recompile the module containing io.micronaut.aop.chain.LegacyGeneratedCaller with Micronaut 5.3 or later")

        cleanup:
        logger.detachAppender(appender)
    }
}
