package io.micronaut.core.bind

import io.micronaut.core.type.Argument
import io.micronaut.core.type.Executable
import spock.lang.Specification

class TestValueAnnotationBinderSpec extends Specification {


    void "test valid TestValueAnnotation binder"() {
        given:
        Executable testValue = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo")] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }
        }

        TestValueAnnotationBinder binder = new TestValueAnnotationBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return { Optional.of(args[1].get(args[0].argument.name)) } as ArgumentBinder.BindingResult
        } )

        registry.findArgumentBinder(_,_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(testValue, registry, [foo:"bar"])

        then:
        bound != null
        bound.invoke(this) == 'bar'
    }
}
