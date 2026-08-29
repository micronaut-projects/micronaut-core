package io.micronaut.inject.typed

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.exceptions.NoSuchBeanException

class PrimitiveExposedTypeSpec extends AbstractBeanDefinitionSpec {

    void 'test a factory method can expose a primitive type and its wrapper'() {
        given:
        def context = buildContext('''
package primitivetypes

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype

@Factory
class Numbers {
    @Bean(typed = [double.class, Double.class])
    @Prototype
    double max() {
        10.5d
    }
}
''')
        def definition = context.getBeanDefinition(double.class)

        expect:
        definition.exposedTypes == [double.class, Double.class] as Set
        context.getBean(double.class) == 10.5d
        context.getBean(Double.class) == 10.5d

        cleanup:
        context.close()
    }

    void 'test a primitive exposed type unrelated to the bean type fails at runtime, not compilation'() {
        given:"a bean that exposes a primitive it does not produce"
        def context = buildContext('''
package primitivetypes

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Prototype

@Factory
class Numbers {
    @Bean(typed = int.class)
    @Prototype
    double max() {
        10.5d
    }
}
''')

        expect:"compilation to succeed, the exposed type to be taken at its word"
        context.getBeanDefinition(int.class).exposedTypes == [int.class] as Set

        and:"the bean to no longer be resolvable by the type it actually produces"
        !context.containsBean(double.class)

        when:
        context.getBean(double.class)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }
}
