package io.micronaut.inject.foreach.generic

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EachBeanGenericSpec extends Specification {

    void "test each bean also delegates generics 1"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanGenericSpec'
            ])
        when:
            def registrations = context.getBeanRegistrations(CoreReader1)
        then:
            registrations.size() == 2
            registrations.forEach {
                assert it.definition().getTypeArguments(CoreReader1).size() == 1
            }
            registrations.collect { it.definition().getTypeArguments(CoreReader1)[0].type }.toSet() == [String, Integer].toSet()
        cleanup:
            context.close()
    }

    void "test each bean also delegates generics 2"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanGenericSpec'
            ])
        when:
            def registrations = context.getBeanRegistrations(CoreReader2)
        then:
            registrations.size() == 2
            registrations.forEach {
                assert it.definition().getTypeArguments(CoreReader2).size() == 1
            }
            registrations.collect { it.definition().getTypeArguments(CoreReader2)[0].type }.toSet() == [String, Integer].toSet()
        cleanup:
            context.close()
    }

    void "test inject each bean also delegates generics"() {
        given:
            ApplicationContext context = ApplicationContext.run([
                    'spec': 'EachBeanGenericSpec'
            ])
        when:
            def coreReaders1 = context.getBean(CoreReaders1)
        then:
            coreReaders1.integerReader
            coreReaders1.stringReader
        when:
            def coreReaders2 = context.getBean(CoreReaders2)
        then:
            coreReaders2.integerReader
            coreReaders2.stringReader
        cleanup:
            context.close()
    }
}
