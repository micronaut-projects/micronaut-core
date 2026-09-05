package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition

/**
 * An annotation that is meta-annotated with {@code @Indexed} by its own type indexes every bean carrying it,
 * so a processor of that annotation can look the beans up by it instead of scanning every bean definition.
 */
class SelfIndexedAnnotationSpec extends AbstractTypeElementSpec {

    void "test beans carrying a self-indexed annotation are enumerable by the annotation type"() {
        given:
        ApplicationContext context = buildContext('sidx.One', '''
package sidx;

import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@Indexed(Marked.class)
@interface Marked {
}

@Marked
class One {
}

@Marked
class Two {
}

@Singleton
class NotMarked {
}

@Marked
class Base {
}

class Sub extends Base {
}
''')
        Class<?> marked = context.classLoader.loadClass('sidx.Marked')

        when: 'the beans are looked up through the compile-time index'
        Collection<BeanDefinition<?>> indexed = context.getBeanDefinitions(marked)

        and: 'and by scanning every bean definition, which is what the index replaces'
        Collection<BeanDefinition<?>> scanned = context.getAllBeanDefinitions().findAll { it.hasStereotype(marked) }

        then: 'both answer the same beans'
        indexed*.beanType.name.toSorted() == scanned*.beanType.name.toSorted()
        indexed*.beanType.name.toSorted() == ['sidx.Base', 'sidx.One', 'sidx.Two']

        cleanup:
        context.close()
    }
}
