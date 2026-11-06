package io.micronaut.inject.factory.proxytarget

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.refresh.RefreshScope

class FactoryFieldOfScopedProxySpec extends AbstractBeanDefinitionSpec {

    void "test a bean field of a factory behind a scoped proxy is read from the proxy's target"() {
        given:
        def context = buildContext('''
package factfieldproxy

import io.micronaut.context.annotation.*
import io.micronaut.runtime.context.scope.Refreshable
import jakarta.annotation.PostConstruct
import jakarta.inject.Named
import java.util.concurrent.atomic.AtomicInteger

@Factory
@Refreshable
class TestFactory {
    static final AtomicInteger CREATED = new AtomicInteger()

    @Bean
    @Prototype
    @Named("property")
    Reading reading

    @Bean
    @Prototype
    @Named("field")
    public Reading fieldReading

    @PostConstruct
    void created() {
        int count = CREATED.incrementAndGet()
        reading = new Reading("property " + count)
        fieldReading = new Reading("field " + count)
    }
}

class Reading {
    final String source

    Reading(String source) {
        this.source = source
    }
}
''', true)
        def readingType = context.classLoader.loadClass('factfieldproxy.Reading')

        expect: "the field is read from the factory instance the scope holds, not from its proxy"
        context.getBean(readingType, Qualifiers.byName('property')).source == 'property 1'
        context.getBean(readingType, Qualifiers.byName('field')).source == 'field 1'

        when:
        context.getBean(RefreshScope).refresh()

        then: "the field is read from the instance created after the refresh"
        context.getBean(readingType, Qualifiers.byName('property')).source == 'property 2'
        context.getBean(readingType, Qualifiers.byName('field')).source == 'field 2'

        cleanup:
        context?.close()
    }
}
