package io.micronaut.inject.visitor.beans

import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.inject.visitor.beans.builder.Book
import io.micronaut.inject.visitor.beans.builder.BookIntrospected
import spock.lang.Specification

import java.util.function.Predicate

class FindIntrospectedTypeSpec extends Specification {

    void "an introspected type is found by its name without Class.forName"() {
        given:
        def introspector = BeanIntrospector.forClassLoader(FindIntrospectedTypeSpec.classLoader)

        expect:
        introspector.findIntrospectedType(Book.name).get() == Book
        !introspector.findIntrospectedType('test.Missing').isPresent()
        !introspector.findIntrospectedType(FindIntrospectedTypeSpec.name).isPresent()

        and: 'the type a class introspects is found, not the class declaring it'
        !introspector.findIntrospectedType(BookIntrospected.name).isPresent()

        and: 'the view without fallbacks answers the same'
        introspector.withoutFallbacks().findIntrospectedType(Book.name).get() == Book
    }

    void "the default implementation answers through the filter"() {
        given:
        def introspector = BeanIntrospector.forClassLoader(FindIntrospectedTypeSpec.classLoader)
        def delegating = new BeanIntrospector() {
            Collection findIntrospections(Predicate filter) { introspector.findIntrospections(filter) }
            Collection<Class<?>> findIntrospectedTypes(Predicate filter) { introspector.findIntrospectedTypes(filter) }
            Optional findIntrospection(Class beanType) { introspector.findIntrospection(beanType) }
        }

        expect:
        delegating.findIntrospectedType(Book.name).get() == Book
        !delegating.findIntrospectedType(FindIntrospectedTypeSpec.name).isPresent()
    }
}
