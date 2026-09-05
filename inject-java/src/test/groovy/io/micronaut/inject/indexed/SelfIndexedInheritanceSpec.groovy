package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * The compile-time index has to answer a self-indexed annotation exactly as scanning every bean definition
 * would, including for an annotation inherited from a supertype.
 */
class SelfIndexedInheritanceSpec extends AbstractTypeElementSpec {

    void "test a self-indexed annotation inherited from a supertype is answered by the index"() {
        given:
        ApplicationContext context = buildContext('inh.Base', '''
package inh;

import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@Indexed(Marked.class)
@interface Marked {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Inherited
@Singleton
@Indexed(InheritedMarked.class)
@interface InheritedMarked {
}

@Marked
class Base {
}

class Sub extends Base {
}

@InheritedMarked
class InheritedBase {
}

class InheritedSub extends InheritedBase {
}
''')
        Class<?> marked = context.classLoader.loadClass('inh.Marked')
        Class<?> inheritedMarked = context.classLoader.loadClass('inh.InheritedMarked')

        when: 'each annotation is answered by the index'
        Collection<BeanDefinition<?>> indexed = context.getBeanDefinitions(marked)
        Collection<BeanDefinition<?>> inheritedIndexed = context.getBeanDefinitions(inheritedMarked)

        and: 'and by scanning every bean definition, which is what the index replaces'
        Collection<BeanDefinition<?>> scanned = scanFor(context, marked)
        Collection<BeanDefinition<?>> inheritedScanned = scanFor(context, inheritedMarked)

        then: 'a plain annotation selects only the type it is declared on'
        indexed*.beanType.simpleName.toSorted() == ['Base']

        and: 'an @Inherited one also selects the subtype that inherits it'
        inheritedIndexed*.beanType.simpleName.toSorted() == ['InheritedBase', 'InheritedSub']

        and: 'the index agrees with the scan, in the same order, in both cases'
        indexed*.beanType.name == scanned*.beanType.name
        inheritedIndexed*.beanType.name == inheritedScanned*.beanType.name

        and: 'the stereotype qualifier answers the same, since it now takes the index'
        context.getBeanDefinitions(Qualifiers.byStereotype(marked))*.beanType.name == scanned*.beanType.name

        cleanup:
        context.close()
    }

    private static Collection<BeanDefinition<?>> scanFor(ApplicationContext context, Class<?> stereotype) {
        context.getAllBeanDefinitions().findAll { it.hasStereotype(stereotype) }
    }
}
