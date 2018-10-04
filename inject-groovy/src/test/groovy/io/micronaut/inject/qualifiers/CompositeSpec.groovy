package io.micronaut.inject.qualifiers

import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.Qualifier
import spock.lang.Specification

import javax.inject.Named
import javax.inject.Singleton

class CompositeSpec extends Specification {

    void 'test using a composite qualifier'() {
        given:
        DefaultBeanContext context = new DefaultBeanContext()
        context.start()

        when:
        Qualifier qualifier = Qualifiers.byQualifiers(Qualifiers.byType(Runnable), Qualifiers.byName('thread'))

        then:
        context.getBeanDefinitions(qualifier).size() == 1

        cleanup:
        context.close()
    }


    @Singleton
    @Named("thread")
    static class Runner implements Runnable {
        @Override
        void run() {

        }
    }
}

