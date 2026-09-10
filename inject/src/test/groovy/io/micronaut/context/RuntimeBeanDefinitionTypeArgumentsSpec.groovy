package io.micronaut.context

import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.function.Supplier

/**
 * A runtime definition resolves its type arguments reflectively, and used to lose an argument that was bound at
 * an intermediate generic super type. The empty answer then made the bean match every type-argument qualifier,
 * while a lookup by a parameterized {@link Argument} missed it.
 */
class RuntimeBeanDefinitionTypeArgumentsSpec extends Specification {

    interface Repo<T> {}

    static abstract class NumberRepo<X extends Number> implements Repo<X> {}

    static class IntRepo extends NumberRepo<Integer> {}

    static class DirectRepo implements Repo<Integer> {}

    static class StringRepo implements Repo<String> {}

    static class RawRepo implements Repo {}

    static class OpenRepo<T> implements Repo<T> {}

    private static <B> RuntimeBeanDefinition<B> definitionOf(Class<B> type) {
        RuntimeBeanDefinition.builder(type, { type.getDeclaredConstructor().newInstance() } as Supplier)
                .exposedTypes(Repo)
                .singleton(true)
                .build()
    }

    void 'an argument bound one level up is answered like one bound directly'() {
        given:
        def intermediate = definitionOf(IntRepo)
        def direct = definitionOf(DirectRepo)

        expect:
        intermediate.getTypeArguments(Repo)*.type == [Integer]
        direct.getTypeArguments(Repo)*.type == [Integer]
        and: 'the intermediate super class itself still answers'
        intermediate.getTypeArguments(NumberRepo)*.type == [Integer]
    }

    void 'a bean with no parameterization of its own answers nothing'() {
        expect:
        definitionOf(RawRepo).getTypeArguments(Repo).isEmpty()
        definitionOf(OpenRepo).getTypeArguments(Repo).isEmpty()
    }

    void 'a type-argument qualifier no longer matches a parameterization the bean does not have'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(definitionOf(IntRepo), definitionOf(DirectRepo), definitionOf(StringRepo))
                .build()
                .start()

        expect:
        context.getBeansOfType(Repo, Qualifiers.byTypeArguments(Integer))*.getClass().toSet() == [IntRepo, DirectRepo].toSet()
        context.getBeansOfType(Repo, Qualifiers.byTypeArguments(String))*.getClass() == [StringRepo]
        context.getBeansOfType(Repo, Qualifiers.byTypeArguments(Number)).isEmpty()

        cleanup:
        context.close()
    }

    void 'a genuinely raw bean still matches any parameterization'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(definitionOf(RawRepo))
                .build()
                .start()

        expect:
        context.getBeansOfType(Repo, Qualifiers.byTypeArguments(Integer))*.getClass() == [RawRepo]
        context.getBeansOfType(Repo, Qualifiers.byTypeArguments(String))*.getClass() == [RawRepo]

        cleanup:
        context.close()
    }

    void 'a lookup by a parameterized argument finds the bean bound one level up'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(definitionOf(IntRepo), definitionOf(DirectRepo), definitionOf(StringRepo))
                .build()
                .start()

        expect:
        context.getBeansOfType(Argument.of(Repo, Integer))*.getClass().toSet() == [IntRepo, DirectRepo].toSet()
        context.getBeansOfType(Argument.of(Repo, String))*.getClass() == [StringRepo]

        cleanup:
        context.close()
    }

    void 'the closest-match qualifier picks both beans that bind the argument exactly'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(definitionOf(IntRepo), definitionOf(DirectRepo), definitionOf(StringRepo))
                .build()
                .start()

        expect:
        context.getBeansOfType(Repo, Qualifiers.byTypeArgumentsClosest(Integer))*.getClass().toSet() == [IntRepo, DirectRepo].toSet()
        context.getBeansOfType(Repo, Qualifiers.byTypeArgumentsClosest(String))*.getClass() == [StringRepo]

        cleanup:
        context.close()
    }
}
