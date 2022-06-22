package io.micronaut.inject.lifecycle.beancreationeventlistener

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class BeanCreatedListenerWarningSpec extends Specification {

    void "test a warning is logged when an event listener won't be executed due to the bean being injected by another bean created event listener"() {
        def oldOut = System.out
        def out = new ByteArrayOutputStream()
        System.out = new PrintStream(out)

        when:
        BeanContext context = new DefaultBeanContext().start()
        String output = out.toString("UTF-8")
        out.close()

        then:
        output.contains("The bean created event listener io.micronaut.inject.lifecycle.beancreationeventlistener.ACreatedListener will not be executed because one or more other bean created event listeners inject io.micronaut.inject.lifecycle.beancreationeventlistener.A:")
        output.contains("    io.micronaut.inject.lifecycle.beancreationeventlistener.OffendingFieldListener --> io.micronaut.inject.lifecycle.beancreationeventlistener.A")
        output.contains("    io.micronaut.inject.lifecycle.beancreationeventlistener.OffendingMethodListener --> io.micronaut.inject.lifecycle.beancreationeventlistener.A")
        output.contains("    io.micronaut.inject.lifecycle.beancreationeventlistener.OffendingConstructorListener --> io.micronaut.inject.lifecycle.beancreationeventlistener.A")
        output.contains("    io.micronaut.inject.lifecycle.beancreationeventlistener.OffendingInterfaceListener --> io.micronaut.inject.lifecycle.beancreationeventlistener.AInterface")
        output.contains("    io.micronaut.inject.lifecycle.beancreationeventlistener.OffendingChainListener --> io.micronaut.inject.lifecycle.beancreationeventlistener.D --> io.micronaut.inject.lifecycle.beancreationeventlistener.E --> io.micronaut.inject.lifecycle.beancreationeventlistener.A")
        !output.contains("The bean created event listener io.micronaut.inject.lifecycle.beancreationeventlistener.CCreatedListener will not be executed")
        !output.contains("NotOffendingChainListener") //because F injects a provider of G

        cleanup:
        System.out = oldOut
        context.close()
    }
}
