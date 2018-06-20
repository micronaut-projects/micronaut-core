package io.micronaut.core.bind

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.bind.exceptions.UnsatisfiedArgumentException
import io.micronaut.core.type.Argument
import io.micronaut.core.type.Executable
import spock.lang.Specification

import javax.annotation.Nullable

class ExecutableBinderSpec extends Specification {

    void "test valid executable binder"() {
        given:
        Executable executable = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo")] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }
        }

        ExecutableBinder binder = new DefaultExecutableBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return { Optional.of(args[1].get(args[0].argument.name)) } as ArgumentBinder.BindingResult
        } )

        registry.findArgumentBinder(_,_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(executable, registry, [foo:"bar"])

        then:
        bound != null
        bound.invoke(this) == 'bar'
    }

    void "test nullable binder"() {
        given:

        AnnotationMetadata annotationMetadata = Mock(AnnotationMetadata)
        annotationMetadata.hasAnnotation(Nullable) >> true
        annotationMetadata.getAnnotationTypeByStereotype(_) >> Optional.empty()

        Executable executable = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo", annotationMetadata)] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }
        }

        ExecutableBinder binder = new DefaultExecutableBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return ArgumentBinder.BindingResult.UNSATISFIED
        } )

        registry.findArgumentBinder(_,_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(executable, registry, [foo:"bar"])

        then:
        bound != null
        bound.invoke(this) == null
    }

    void "test invalid executable binder"() {
        given:
        Executable executable = new Executable() {
            @Override
            Argument[] getArguments() {
                [Argument.of(String, "foo")] as Argument[]
            }

            @Override
            Object invoke(Object instance, Object... arguments) {
                return arguments[0]
            }
        }

        ExecutableBinder binder = new DefaultExecutableBinder()

        ArgumentBinderRegistry registry = Mock(ArgumentBinderRegistry)
        ArgumentBinder argumentBinder = Mock(ArgumentBinder)

        argumentBinder.bind(_, _) >> ({ args ->
            return ArgumentBinder.BindingResult.UNSATISFIED
        } )

        registry.findArgumentBinder(_,_) >> Optional.of( argumentBinder)

        when:
        def bound = binder.bind(executable, registry, [not:"there"])

        then:
        def e = thrown(UnsatisfiedArgumentException)
        e.message == 'Required argument [String foo] not specified'
    }
}
