package io.micronaut.context

import io.micronaut.core.annotation.Nullable
import spock.lang.Specification

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.util.function.BiFunction
import java.util.function.Consumer

class AnnotationReflectionUtilsSpec extends Specification {

    void "test generic"() {
        given:
            Consumer<String> consumer = new Consumer<String>() {
                @Override
                void accept(String s) {
                }
            }

        when:
            def argument = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), Consumer)

        then:
            argument.type == Consumer
            argument.getTypeVariable("T").get().type == String
    }

    void "test generic 1 "() {
        given:
            Consumer<String> consumer = new @MyTypeUseAnnotation Consumer<@Nullable String>() {
                @Override
                void accept(String s) {
                }
            }

        when:
            def argument = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), Consumer)

        then:
            argument.type == Consumer
            argument.annotationMetadata.annotationNames.isEmpty()
            argument.getTypeVariable("T").get().type == String
            argument.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]
    }

    void "test generic 2"() {
        given:
            Consumer<String> consumer = new AbstractConsumer<@Nullable String>() {
            }

        when:
            def argumentAbstractConsumer = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), AbstractConsumer)

        then:
            argumentAbstractConsumer.type == AbstractConsumer
            argumentAbstractConsumer.annotationMetadata.annotationNames.isEmpty()
            argumentAbstractConsumer.getTypeVariable("T").get().type == String
            argumentAbstractConsumer.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]

        when:
            def argumentConsumer = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), Consumer)

        then:
            argumentConsumer.type == Consumer
            argumentConsumer.annotationMetadata.annotationNames.toList() == ["io.micronaut.context.MyTypeAnnotation"]
            argumentConsumer.getTypeVariable("T").get().type == String
            argumentConsumer.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]
    }

    void "test generic 3"() {
        given:
            Consumer<String> consumer = new AbstractConsumer2<@Nullable String>() {
            }

        when:
            def argumentConsumer = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), Consumer)
        then:
            argumentConsumer.type == Consumer
            argumentConsumer.annotationMetadata.annotationNames.toList() == ["io.micronaut.context.MyTypeAnnotation"]
            argumentConsumer.getTypeVariable("T").get().type == String
            argumentConsumer.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]

        when:
            def argumentAbstractConsumer = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), AbstractConsumer)
        then:
            argumentAbstractConsumer.type == AbstractConsumer
            argumentAbstractConsumer.annotationMetadata.annotationNames.toList() == ["io.micronaut.context.MyTypeAnnotation"]
            argumentAbstractConsumer.getTypeVariable("T").get().type == String
            argumentAbstractConsumer.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]

        when:
            def argumentAbstractConsumer2 = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), AbstractConsumer2)
        then:
            argumentAbstractConsumer2.type == AbstractConsumer2
            argumentAbstractConsumer2.annotationMetadata.annotationNames.isEmpty()
            argumentAbstractConsumer2.getTypeVariable("T").get().type == String
            argumentAbstractConsumer2.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]
    }

    void "test generic 4"() {
        given:
            BiFunction<String, Integer, Long> consumer = new BiFunction<@Nullable String, @MyTypeUseAnnotation Integer, Long>() {
                @Override
                Long apply(String s, Integer integer) {
                    return null
                }
            }

        when:
            def argument = AnnotationReflectionUtils.findImplementationAsArgument(consumer.getClass(), BiFunction)

        then:
            argument.type == BiFunction
            argument.annotationMetadata.annotationNames.isEmpty()
            argument.getTypeVariable("T").get().type == String
            argument.getTypeVariable("T").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.core.annotation.Nullable"]
            argument.getTypeVariable("U").get().type == Integer
            argument.getTypeVariable("U").get().annotationMetadata.annotationNames.toList() == ["io.micronaut.context.MyTypeUseAnnotation"]
            argument.getTypeVariable("R").get().type == Long
            argument.getTypeVariable("R").get().annotationMetadata.annotationNames.isEmpty()
    }

}

@MyTypeAnnotation
abstract class AbstractConsumer<T> implements Consumer<T> {
    @Override
    void accept(T s) {
    }
}

@MyTypeAnnotation
abstract class AbstractConsumer2<T> extends AbstractConsumer<T> implements Consumer<T> {
    @Override
    void accept(T s) {
    }
}

@Target(ElementType.TYPE_USE)
@Retention(RetentionPolicy.RUNTIME)
@interface MyTypeUseAnnotation {
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface MyTypeAnnotation {
}
