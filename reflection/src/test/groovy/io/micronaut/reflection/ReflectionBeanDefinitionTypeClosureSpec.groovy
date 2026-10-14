package io.micronaut.reflection

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
}
