package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.reflect.ClassUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ValidatedBeanDefinition

/**
 * Regression for https://github.com/micronaut-projects/micronaut-core/issues/12847
 *
 * Constrained {@code @Value} constructor parameters must be validated without requiring
 * {@code @Introspected} on a plain {@code @Singleton}.
 */
class ValidatedValueConstructorSpec extends AbstractTypeElementSpec {

    private static final String SERVICE = '''
package test;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.Size;

@Singleton
class TestService {
    private final String test;

    TestService(@Size(min = 35, max = 255) @Value("${my.test}") String test) {
        this.test = test;
    }

    String getTest() {
        return test;
    }
}
'''

    void "singleton with validated @Value constructor param is ValidatedBeanDefinition without introspection"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.TestService', SERVICE)

        then:
        beanDefinition instanceof ValidatedBeanDefinition
        // No $Introspection generated — @Introspected is not required for this case
        ClassUtils.forName('test.$TestService$Introspection', beanDefinition.beanType.classLoader).isEmpty()
    }

    void "singleton with validated @Value constructor param starts without @Introspected"() {
        given:
        ApplicationContext context = buildContext('test.TestService', SERVICE, true, ['my.test': 'x' * 40])

        when:
        def bean = getBean(context, 'test.TestService')

        then:
        bean.getTest() == ('x' * 40)
        // Must not fail with: Cannot validate bean [...]. No bean introspection present.
        !ClassUtils.forName('test.$TestService$Introspection', context.classLoader).isPresent()

        cleanup:
        context.close()
    }

    void "singleton with validated @Value constructor param rejects invalid values"() {
        given:
        ApplicationContext context = buildContext('test.TestService', SERVICE, true, ['my.test': 'too-short'])

        when:
        getBean(context, 'test.TestService')

        then:
        def e = thrown(DependencyInjectionException)
        def full = (e.message + ' ' + (e.cause?.message ?: '')).toLowerCase()
        full.contains('size')
        !full.contains('no bean introspection present')

        cleanup:
        context.close()
    }
}
