package io.micronaut.kotlin.processing.beans.factory.beanproperty

import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext

class FactoryFieldOfScopedProxySpec extends Specification {

    void "test a bean property of a factory behind a scoped proxy is read from the proxy's target"() {
        given:
        def context = buildContext('''
package factfieldproxy

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype
import io.micronaut.runtime.context.scope.Refreshable
import jakarta.annotation.PostConstruct
import jakarta.inject.Named
import java.util.concurrent.atomic.AtomicInteger

@Factory
@Refreshable
open class TestFactory {
    companion object {
        @JvmField
        val CREATED = AtomicInteger()
    }

    @Bean
    @Prototype
    @Named("property")
    var reading: Reading? = null

    @Bean
    @Prototype
    @Named("field")
    @JvmField
    var fieldReading: Reading? = null

    @PostConstruct
    open fun created() {
        val count = CREATED.incrementAndGet()
        reading = Reading("property " + count)
        fieldReading = Reading("field " + count)
    }
}

class Reading(val source: String)
''', true)
        def readingType = context.classLoader.loadClass('factfieldproxy.Reading')

        expect: "the property is read from the factory instance the scope holds, not from its proxy"
        context.getBean(readingType, Qualifiers.byName('property')).source == 'property 1'
        context.getBean(readingType, Qualifiers.byName('field')).source == 'field 1'

        when:
        context.getBean(RefreshScope).refresh()

        then: "the property is read from the instance created after the refresh"
        context.getBean(readingType, Qualifiers.byName('property')).source == 'property 2'
        context.getBean(readingType, Qualifiers.byName('field')).source == 'field 2'

        cleanup:
        context?.close()
    }
}
