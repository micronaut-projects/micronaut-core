package io.micronaut.context

import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.type.Argument
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.function.BiFunction

class RuntimeBeanDefinitionInjectionPointSpec extends Specification {

    private static MutableAnnotationMetadata nullableMetadata() {
        def metadata = new MutableAnnotationMetadata()
        metadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, [:])
        metadata
    }

    private static RuntimeBeanDefinition.Builder<Bar> barBuilder(
            BiFunction<BeanResolutionContext, RuntimeBeanDefinition.Injections, Bar> factory) {
        RuntimeBeanDefinition.builder(Bar, factory)
    }

    void 'test a declared injection point is resolved and passed to the creator'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).singleton(true).build(),
                        barBuilder((ctx, injections) -> new Bar(injections.get(0) as Foo))
                                .injectionPoint(Argument.of(Foo))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        Bar bar = context.getBean(Bar)

        then:
        bar.foo.name == "one"
        bar.foo.is(context.getBean(Foo))

        cleanup:
        context.close()
    }

    void 'test a declared injection point can be looked up by argument'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).singleton(true).build(),
                        barBuilder((ctx, injections) -> new Bar(injections.get(Argument.of(Foo))))
                                .injectionPoint(Argument.of(Foo))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Bar).foo.name == "one"

        cleanup:
        context.close()
    }

    void 'test a qualified injection point is resolved with its qualifier'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).named("one").singleton(true).build(),
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("two")).named("two").singleton(true).build(),
                        barBuilder((ctx, injections) ->
                                new Bar(injections.get(Argument.of(Foo), Qualifiers.byName("two"))))
                                .injectionPoint(Argument.of(Foo), Qualifiers.byName("two"))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Bar).foo.name == "two"

        cleanup:
        context.close()
    }

    void 'test injection points are resolved and handed over in declaration order'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).named("one").singleton(true).build(),
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("two")).named("two").singleton(true).build(),
                        barBuilder((ctx, injections) -> {
                            def created = new Bar(injections.get(0) as Foo)
                            created.second = injections.get(1) as Foo
                            created.size = injections.size()
                            created
                        })
                                .injectionPoint(Argument.of(Foo), Qualifiers.byName("two"))
                                .injectionPoint(Argument.of(Foo), Qualifiers.byName("one"))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        Bar bar = context.getBean(Bar)

        then:
        bar.size == 2
        bar.foo.name == "two"
        bar.second.name == "one"

        cleanup:
        context.close()
    }

    void 'test an unsatisfied injection point fails the creation'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        barBuilder((ctx, injections) -> new Bar(injections.get(0) as Foo))
                                .injectionPoint(Argument.of(Foo, "foo"))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        context.getBean(Bar)

        then:
        def e = thrown(DependencyInjectionException)
        e.message.contains("Failed to inject value for parameter [foo]")
        e.message.contains("No bean of type [" + Foo.name + "] exists")

        cleanup:
        context.close()
    }

    void 'test a nullable injection point resolves to null when unsatisfied'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        barBuilder((ctx, injections) -> new Bar(injections.get(0) as Foo))
                                .injectionPoint(Argument.of(Foo, "foo", nullableMetadata()))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Bar).foo == null

        cleanup:
        context.close()
    }

    void 'test the declared injection points describe the bean dependencies'() {
        given:
        RuntimeBeanDefinition<Bar> definition = barBuilder((ctx, injections) -> new Bar(injections.get(0) as Foo))
                .injectionPoint(Argument.of(Foo, "foo"), Qualifiers.byName("one"))
                .build()

        expect:
        definition.constructor.arguments.length == 1
        definition.constructor.arguments[0].name == "foo"
        definition.constructor.arguments[0].type == Foo
        definition.requiredComponents as List == [Foo]
    }

    void 'test a definition without declared injection points is unchanged'() {
        given:
        RuntimeBeanDefinition<Bar> definition = RuntimeBeanDefinition.builder(Bar, () -> new Bar(null)).build()

        expect:
        definition.constructor.arguments.length == 0
        definition.requiredComponents.isEmpty()
    }

    void 'test looking up an injection point that was not declared'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).singleton(true).build(),
                        barBuilder((ctx, injections) -> new Bar(injections.get(Argument.of(Foo), Qualifiers.byName("nope"))))
                                .injectionPoint(Argument.of(Foo))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        context.getBean(Bar)

        then:
        def e = thrown(Exception)
        def cause = e
        while (cause.cause != null) {
            cause = cause.cause
        }
        cause instanceof IllegalArgumentException
        cause.message.contains("No injection point declared")

        cleanup:
        context.close()
    }

    static class Foo {
        final String name

        Foo(String name) {
            this.name = name
        }
    }

    static class Bar {
        final Foo foo
        Foo second
        int size

        Bar(Foo foo) {
            this.foo = foo
        }
    }
}
