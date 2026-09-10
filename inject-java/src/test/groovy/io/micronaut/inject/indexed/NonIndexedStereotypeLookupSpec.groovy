package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.FilteringQualifier
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * {@code @Indexed} is only an optimisation hint. An annotation without it is still answered by filtering every
 * bean definition reference, which is what {@code getBeanDefinitions(Qualifier)} did for every annotation
 * before the index was consulted.
 */
class NonIndexedStereotypeLookupSpec extends AbstractTypeElementSpec {

    void "test a stereotype that is not indexed is still resolved by scanning"() {
        given:
        ApplicationContext context = buildContext('nidx.PlainOne', '''
package nidx;

import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@interface Plain {
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@Indexed(Marked.class)
@interface Marked {
}

@Plain
class PlainOne {
}

@Plain
class PlainTwo {
}

@Marked
class MarkedOne {
}

@Singleton
class Neither {
}
''')
        Class<?> plain = context.classLoader.loadClass('nidx.Plain')
        Class<?> marked = context.classLoader.loadClass('nidx.Marked')

        expect: 'the plain annotation reports no indexed type, so the qualifier is answered by scanning'
        ((FilteringQualifier) Qualifiers.byStereotype(plain)).getIndexedArgument() == null

        and: 'while the indexed one does take the index'
        ((FilteringQualifier) Qualifiers.byStereotype(marked)).getIndexedArgument().type == marked

        when: 'the plain stereotype is resolved the way it always was'
        Collection<BeanDefinition<?>> scanned = context.getBeanDefinitions(Qualifiers.byStereotype(plain))

        then: 'it finds exactly the beans that carry it'
        scanned*.beanType.simpleName.toSorted() == ['PlainOne', 'PlainTwo']

        and: 'which is what enumerating every bean definition answers'
        scanned*.beanType.name == context.getAllBeanDefinitions().findAll { it.hasStereotype(plain) }*.beanType.name

        and: 'the index holds nothing for it, so the scan is what did the work'
        context.getBeanDefinitions(plain).isEmpty()

        cleanup:
        context.close()
    }

    void "test a self-indexed stereotype resolved by name is answered by scanning and agrees with the index"() {
        given:
        ApplicationContext context = buildContext('nidx2.MarkedOne', '''
package nidx2;

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
class MarkedOne {
}

@Marked
class MarkedTwo {
}
''')
        Class<?> marked = context.classLoader.loadClass('nidx2.Marked')

        expect: 'the qualifier built from the annotation name keeps the scan, having no type to index by'
        ((FilteringQualifier) Qualifiers.byStereotype(marked.name)).getIndexedArgument() == null

        when: 'the same annotation is resolved by name and by type'
        Collection<BeanDefinition<?>> byName = context.getBeanDefinitions(Qualifiers.byStereotype(marked.name))
        Collection<BeanDefinition<?>> byType = context.getBeanDefinitions(Qualifiers.byStereotype(marked))

        then: 'the scan and the index answer the same beans, in the same order'
        byName*.beanType.simpleName.toSorted() == ['MarkedOne', 'MarkedTwo']
        byName*.beanType.name == byType*.beanType.name

        cleanup:
        context.close()
    }
}
