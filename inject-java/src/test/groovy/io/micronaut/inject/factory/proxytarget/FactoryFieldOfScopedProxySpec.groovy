package io.micronaut.inject.factory.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.runtime.context.scope.refresh.RefreshScope

class FactoryFieldOfScopedProxySpec extends AbstractTypeElementSpec {

    void "test a bean field of a factory behind a scoped proxy is read from the proxy's target"() {
        given:
        def context = buildContext('factfieldproxy.TestFactory', '''
package factfieldproxy;

import io.micronaut.context.annotation.*;
import io.micronaut.runtime.context.scope.Refreshable;
import jakarta.annotation.PostConstruct;
import java.util.concurrent.atomic.AtomicInteger;

@Factory
@Refreshable
class TestFactory {
    static final AtomicInteger CREATED = new AtomicInteger();

    @Bean
    @Prototype
    Reading reading;

    @PostConstruct
    void created() {
        reading = new Reading("instance " + CREATED.incrementAndGet());
    }
}

class Reading {
    final String source;

    Reading(String source) {
        this.source = source;
    }
}
''', true)
        def readingType = context.classLoader.loadClass('factfieldproxy.Reading')

        expect: "the field is read from the factory instance the scope holds, not from its proxy"
        context.getBean(readingType).source == 'instance 1'

        when:
        context.getBean(RefreshScope).refresh()

        then: "the field is read from the instance created after the refresh"
        context.getBean(readingType).source == 'instance 2'

        cleanup:
        context?.close()
    }
}
