package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition

/**
 * A bean that implements an {@code @Indexed} interface but restricts its exposed types with
 * {@code @Bean(typed = ...)} must not be enumerated by that interface.
 */
class TypedBeanIndexedLookupSpec extends AbstractTypeElementSpec {

    void "test a bean hidden from an indexed interface by @Bean(typed) is not enumerated by it"() {
        given:
        ApplicationContext context = buildContext('tidx.Default', '''
package tidx;

import io.micronaut.context.annotation.Bean;
import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;

@Indexed(Handler.class)
interface Handler {
}

@Singleton
class Default implements Handler {
}

@Singleton
@Bean(typed = Restricted.class)
class Restricted implements Handler {
}
''')
        Class<?> handler = context.classLoader.loadClass('tidx.Handler')
        Class<?> restricted = context.classLoader.loadClass('tidx.Restricted')

        when:
        Collection<BeanDefinition<?>> definitions = context.getBeanDefinitions(handler)

        then:
        definitions*.beanType.name == ['tidx.Default']
        context.getBeansOfType(handler)*.class.name == ['tidx.Default']
        context.containsBean(restricted)

        cleanup:
        context.close()
    }
}
