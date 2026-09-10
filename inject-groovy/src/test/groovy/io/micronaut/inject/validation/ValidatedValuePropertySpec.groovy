package io.micronaut.inject.validation

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition

/**
 * A Groovy property annotated with {@code @Value} is injected through its setter, a Groovy
 * field through field injection. Both only carry injection-point constraints, so the bean is
 * compiled in injection-point-only mode and relies on {@code validateBeanArgument} being
 * invoked while the value is resolved.
 */
class ValidatedValuePropertySpec extends AbstractBeanDefinitionSpec {

    private static final String PROPERTY_BEAN = '''
package test

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull

@Singleton
class PropertyBean {
    @Max(20L)
    @NotNull
    @Value('${a.number}')
    Integer number
}
'''

    private static final String FIELD_BEAN = '''
package test

import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.NotNull

@Singleton
class FieldBean {
    @Max(20L)
    @NotNull
    @Value('${a.number}')
    private Integer number

    Integer getNumber() {
        return number
    }
}
'''

    void "singleton with validated @Value #description is a ValidatedBeanDefinition without introspection"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition(className, source)

        then:
        beanDefinition instanceof ValidatedBeanDefinition
        ClassUtils.forName(className.replace('test.', 'test.$') + '$Introspection', beanDefinition.beanType.classLoader).isEmpty()

        where:
        description | className          | source
        'property'  | 'test.PropertyBean' | PROPERTY_BEAN
        'field'     | 'test.FieldBean'    | FIELD_BEAN
    }

    void "singleton with validated @Value #description accepts a valid value"() {
        given:
        ApplicationContext context = buildContext(source, true, ['a.number': 10])

        when:
        def bean = context.getBean(context.classLoader.loadClass(className))

        then:
        bean.getNumber() == 10

        cleanup:
        context.close()

        where:
        description | className          | source
        'property'  | 'test.PropertyBean' | PROPERTY_BEAN
        'field'     | 'test.FieldBean'    | FIELD_BEAN
    }

    void "singleton with validated @Value #description rejects an invalid value"() {
        given:
        ApplicationContext context = buildContext(source, true, ['a.number': 40])

        when:
        context.getBean(context.classLoader.loadClass(className))

        then:
        BeanInstantiationException e = thrown()
        e.message.contains('must be less than or equal to 20')
        !e.message.toLowerCase().contains('no bean introspection present')

        cleanup:
        context.close()

        where:
        description | className          | source
        'property'  | 'test.PropertyBean' | PROPERTY_BEAN
        'field'     | 'test.FieldBean'    | FIELD_BEAN
    }
}
