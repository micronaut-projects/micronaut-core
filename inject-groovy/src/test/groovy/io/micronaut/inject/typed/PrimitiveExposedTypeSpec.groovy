package io.micronaut.inject.typed

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec

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
}
