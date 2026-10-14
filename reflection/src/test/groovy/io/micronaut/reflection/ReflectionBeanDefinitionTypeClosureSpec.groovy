package io.micronaut.reflection

import io.micronaut.core.type.GenericPlaceholder
import spock.lang.Specification

import java.util.function.Supplier

/**
 * A reflective definition answers {@code BeanDefinition#getTypeClosure()} from the generic signatures of the
 * classes, as a compiled definition answers it from what the compiler recorded.
 */
class ReflectionBeanDefinitionTypeClosureSpec extends Specification {

    void "the closure is the bean type, its super types with the arguments the type gives them, and Object"() {
        given:
        def closure = ReflectionBeanDefinition.of(Generic).typeClosure

        expect:
        closure*.type == [Generic, GenericBase, Supplier, Object]
        closure[1].typeParameters*.type == [String]
        closure[2].typeParameters*.type == [Integer]
        closure[0].typeParameters.length == 0
    }

    void "the variables the bean type declares are kept, as a compiled definition keeps them"() {
        given:
        def closure = ReflectionBeanDefinition.of(GenericBase).typeClosure

        expect:
        closure*.type == [GenericBase, Object]
        closure[0].typeParameters[0] instanceof GenericPlaceholder
        closure[0].typeParameters[0].variableName == 'T'
    }
}
