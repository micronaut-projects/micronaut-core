package io.micronaut.context

import io.micronaut.context.generics.IntRepo
import io.micronaut.context.generics.Marker
import io.micronaut.context.generics.NumberRepo
import io.micronaut.context.generics.Plain
import io.micronaut.context.generics.Repo
import spock.lang.Specification

import java.util.function.Supplier

/**
 * A runtime definition records nothing, so it derives {@code getTypeArgumentKeys()} from the bean type's own
 * hierarchy. The answer has to be the one a build time definition of the same classes gives, which
 * {@code io.micronaut.inject.generics.IntermediateTypeArgumentSpec} holds as the reference.
 */
class RuntimeBeanDefinitionTypeArgumentKeysSpec extends Specification {

    private static <B> RuntimeBeanDefinition<B> definitionOf(Class<B> type) {
        RuntimeBeanDefinition.builder(type, { type.getDeclaredConstructor().newInstance() } as Supplier)
                .singleton(true)
                .build()
    }

    void 'the keys name the hierarchy the compiled definition records for the same classes'() {
        given:
        def definition = definitionOf(IntRepo)

        expect: 'the bean type, the intermediate super class and the interface, as the compiled definition names them'
        definition.typeArgumentKeys.toSet() == [IntRepo.name, NumberRepo.name, Repo.name].toSet()

        and: 'each key answers the argument resolved through the intermediate super type'
        definition.getTypeArguments(Repo.name)*.type == [Integer]
        definition.getTypeArguments(NumberRepo.name)*.type == [Integer]
        definition.getTypeArguments(IntRepo.name).isEmpty()
    }

    void 'the name lookup answers what the class lookup answers'() {
        given:
        def definition = definitionOf(IntRepo)

        expect:
        definition.typeArgumentKeys.every {
            definition.getTypeArguments(Class.forName(it)) == definition.getTypeArguments(it)
        }

        and: 'a name that is not a super type of the bean answers nothing'
        definition.getTypeArguments(Marker.name).isEmpty()
        definition.getTypeArguments((String) null).isEmpty()
    }

    void 'a bean with no type argument anywhere in its hierarchy answers no keys'() {
        expect:
        definitionOf(Plain).typeArgumentKeys.isEmpty()
    }
}
