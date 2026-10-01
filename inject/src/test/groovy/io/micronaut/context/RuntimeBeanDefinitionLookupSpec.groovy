package io.micronaut.context

import io.micronaut.context.exceptions.CircularDependencyException
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

import java.util.function.BiConsumer
import java.util.function.Function

class RuntimeBeanDefinitionLookupSpec extends Specification {

    private static RuntimeBeanDefinition.Builder<Bar> barBuilder(
            Function<RuntimeBeanDefinition.CreationContext, Bar> factory) {
        RuntimeBeanDefinition.builder(Bar, factory)
    }

    private static RuntimeBeanDefinition.Builder<Foo> fooBuilder(
            Function<RuntimeBeanDefinition.CreationContext, Foo> factory) {
        RuntimeBeanDefinition.builder(Foo, factory)
    }

    void 'test the creator can look up a bean that was not declared as an injection point'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).singleton(true).build(),
                        barBuilder(ctx -> new Bar(ctx.getBean(Argument.of(Foo))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Bar).foo.is(context.getBean(Foo))

        cleanup:
        context.close()
    }

    void 'test the creator can look up a qualified bean'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("red"))
                                .named("red").singleton(true).build(),
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("green"))
                                .named("green").singleton(true).build(),
                        barBuilder(ctx -> new Bar(ctx.getBean(Argument.of(Foo), Qualifiers.byName("green"))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Bar).foo.name == "green"

        cleanup:
        context.close()
    }

    void 'test an unsatisfied lookup fails the creation'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        barBuilder(ctx -> new Bar(ctx.getBean(Argument.of(Foo))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        context.getBean(Bar)

        then:
        def e = thrown(DependencyInjectionException)
        e.message.contains(Foo.name)

        cleanup:
        context.close()
    }

    void 'test a lookup that cannot be satisfied can be found instead'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        barBuilder(ctx -> new Bar(ctx.findBean(Argument.of(Foo)).orElse(null)))
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

    void 'test the creator can look up all the beans of a type'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).named("one").singleton(true).build(),
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("two")).named("two").singleton(true).build(),
                        barBuilder(ctx -> new Bar(null, ctx.getBeansOfType(Argument.of(Foo))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        expect:
        context.getBean(Bar).all*.name.toSorted() == ["one", "two"]

        cleanup:
        context.close()
    }

    void 'test a circular lookup between two runtime bean definitions is detected'() {
        given:
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        fooBuilder(ctx -> new Foo(ctx.getBean(Argument.of(Bar)).toString()))
                                .singleton(true)
                                .build(),
                        barBuilder(ctx -> new Bar(ctx.getBean(Argument.of(Foo))))
                                .singleton(true)
                                .build()
                )
                .build()
                .start()

        when:
        context.getBean(Bar)

        then:
        thrown(CircularDependencyException)

        cleanup:
        context.close()
    }

    void 'test the disposer can look up a bean'() {
        given:
        def disposed = []
        def context = ApplicationContext.builder()
                .beanDefinitions(
                        RuntimeBeanDefinition.builder(Foo, () -> new Foo("one")).singleton(true).build(),
                        barBuilder(ctx -> new Bar(null))
                                .singleton(true)
                                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, Bar>) { ctx, bean ->
                                    disposed << ctx.getBean(Argument.of(Foo))
                                })
                                .build()
                )
                .build()
                .start()

        when:
        context.destroyBean(context.getBean(Bar))

        then:
        disposed.size() == 1
        disposed[0].is(context.getBean(Foo))

        cleanup:
        context.close()
    }

    void 'test setting one disposer form clears the other'() {
        given:
        def calls = []
        def builder = RuntimeBeanDefinition.builder(Foo, () -> new Foo("one"))
                .singleton(true)
                .injectedDisposer((BiConsumer<RuntimeBeanDefinition.DisposalContext, Foo>) { ctx, bean ->
                    calls << "injected"
                })
                .disposer((BiConsumer<BeanContext, Foo>) { ctx, bean -> calls << "plain" })
        def context = ApplicationContext.builder()
                .beanDefinitions(builder.build())
                .build()
                .start()

        when: 'the disposer set last is the one that runs'
        context.destroyBean(context.getBean(Foo))

        then:
        calls == ["plain"]

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
        final Collection<Foo> all

        Bar(Foo foo, Collection<Foo> all = Collections.emptyList()) {
            this.foo = foo
            this.all = all
        }
    }
}
