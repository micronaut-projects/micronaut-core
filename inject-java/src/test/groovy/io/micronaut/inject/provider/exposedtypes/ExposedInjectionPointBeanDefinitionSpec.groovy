package io.micronaut.inject.provider.exposedtypes

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultBeanDefinitionsProvider
import io.micronaut.context.exceptions.BeanDestructionException
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.DisposableBeanDefinition
import io.micronaut.inject.provider.injectionpoint.InjectedAtDefinition
import spock.lang.AutoCleanup
import spock.lang.Specification

class ExposedInjectionPointBeanDefinitionSpec extends Specification {

    @AutoCleanup
    ApplicationContext context = ApplicationContext.builder()
            .properties("spec": getClass().getSimpleName())
            .beanDefinitionsProvider { ClassLoader classLoader ->
                new DefaultBeanDefinitionsProvider().provide(classLoader) + [new HandleDefinition(), new MaybeDefinition()]
            }
            .start()

    void "the bean is injected as each of the generic types it is exposed as"() {
        when:
        HandleConsumer consumer = context.getBean(HandleConsumer)

        then:
        consumer.lookup.injectedAt() == "lookup"
        consumer.source instanceof Handle
        ((Handle) consumer.source).injectedAt() == "source"
        consumer.handle.injectedAt() == "handle"
    }

    void "a bean looked up by an exposed generic type is built for the lookup"() {
        expect:
        context.getBean(Argument.of(Lookup, UUID)) instanceof Handle
        context.getBean(Argument.of(Source, UUID)) instanceof Handle
    }

    void "every exposed type has the type arguments of the bean type, unless the definition says otherwise"() {
        given:
        BeanDefinition<?> definition = context.getBeanDefinition(Argument.of(Lookup, String))

        expect:
        definition instanceof HandleDefinition
        definition.typeArguments*.name == ["T"]
        definition.getTypeArguments(Handle)*.name == ["T"]
        definition.getTypeArguments(Lookup)*.name == ["T"]
        definition.getTypeArguments(Source)*.name == ["T"]
        definition.getTypeArguments(AutoCloseable).isEmpty()
        definition.getTypeArguments(Comparable).isEmpty()
        definition.getTypeArguments(Lookup.name)*.name == ["T"]
        definition.getTypeArguments(AutoCloseable.name).isEmpty()
        definition.getTypeArguments(Comparable.name).isEmpty()
        definition.getTypeArguments((String) null).isEmpty()
    }

    void "a disposable definition closes what it built when the bean it was injected into is destroyed"() {
        given:
        HandleConsumer consumer = context.getBean(HandleConsumer)

        expect:
        context.getBeanDefinition(Argument.of(Lookup, String)) instanceof DisposableBeanDefinition
        !consumer.lookup.isClosed()
        !consumer.source.isClosed()
        !consumer.handle.isClosed()

        when:
        context.destroyBean(HandleConsumer)

        then:
        consumer.lookup.isClosed()
        consumer.source.isClosed()
        consumer.handle.isClosed()
    }

    void "a definition that is not disposable does not dispose of what it built"() {
        expect:
        !(new InjectedAtDefinition() instanceof DisposableBeanDefinition)
        !(new MaybeDefinition() instanceof DisposableBeanDefinition)
        new InjectedAtDefinition().annotationMetadata.is(AnnotationMetadata.EMPTY_METADATA)
    }

    void "a definition supplies its own annotation metadata, here declaring that it may build nothing"() {
        when:
        MaybeConsumer consumer = context.getBean(MaybeConsumer)

        then:
        new MaybeDefinition().annotationMetadata.hasDeclaredAnnotation("jakarta.annotation.Nullable")
        consumer.present.injectedAt() == "present"
        consumer.absent == null
    }

    void "a disposable definition given its annotation metadata disposes of a bean handed to it"() {
        given:
        AnnotationMetadata metadata = new MaybeDefinition().annotationMetadata
        AnyObjectDefinition definition = new AnyObjectDefinition(metadata)
        Object plain = new Object()
        Handle<Object> handle = new Handle<>("handed")

        expect:
        definition.annotationMetadata.is(metadata)
        definition.dispose(context, plain).is(plain)
        definition.dispose(context, handle).is(handle)
        handle.isClosed()
    }

    void "a bean that fails to close fails its disposal"() {
        given:
        AnyObjectDefinition definition = new AnyObjectDefinition(AnnotationMetadata.EMPTY_METADATA)
        AutoCloseable failing = { throw new IOException("cannot close") } as AutoCloseable

        when:
        definition.dispose(context, failing)

        then:
        BeanDestructionException e = thrown()
        e.cause instanceof IOException
        e.cause.message == "cannot close"
    }
}
