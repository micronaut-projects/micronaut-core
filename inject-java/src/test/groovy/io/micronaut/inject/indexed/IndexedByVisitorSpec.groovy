package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.annotation.AnnotationClassValue
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Indexed
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanDefinitionReference
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.visitor.VisitorContext

/**
 * A bean annotated with {@code @Indexed(Marker.class)} is enumerable via {@code getBeanDefinitions(Marker)}
 * even when it does not implement {@code Marker}, but it is never provided as a {@code Marker} instance.
 */
class IndexedByVisitorSpec extends AbstractTypeElementSpec {

    void "test a bean indexed by a visitor with a type it does not implement is enumerable by that type"() {
        given:
        ApplicationContext context = buildContext('idx.Test', '''
package idx;

import jakarta.inject.Singleton;

@Singleton
class Test {
}

interface Marker {
}
''')
        Class<?> test = context.classLoader.loadClass('idx.Test')
        Class<?> marker = context.classLoader.loadClass('idx.Marker')

        expect:
        !marker.isAssignableFrom(test)

        when:
        Collection<BeanDefinition<?>> definitions = context.getBeanDefinitions(marker)

        then:
        definitions*.beanType == [test]
        ((BeanDefinitionReference<?>) definitions[0]).indexes.toList() == [marker]
        !definitions[0].isCandidateBean(Argument.of(marker))
        definitions[0].isCandidateBean(Argument.of(test))

        and: 'the bean is indexed once by its own type'
        context.getBeanDefinitions(test)*.beanType == [test]
        context.getBean(test).is(context.getBean(test))

        and: 'the bean is not provided as an instance of the indexed type'
        !context.containsBean(marker)
        context.getBeansOfType(marker).isEmpty()
        context.findBeanDefinition(marker).isEmpty()

        when:
        context.getBean(marker)

        then:
        NoSuchBeanException e = thrown()
        e.message.contains('idx.Marker')

        cleanup:
        context.close()
    }

    void "test a bean indexed through a stereotype or directly is enumerable alongside real implementations"() {
        given:
        ApplicationContext context = buildContext('idx2.Test', '''
package idx2;

import io.micronaut.core.annotation.Indexed;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

@Singleton
@MarkerStereotype
class Test {
}

@Singleton
@Indexed(Marker.class)
class DirectlyIndexed {
}

@Singleton
class Impl implements Marker {
}

@Singleton
class Consumer {
    final List<Marker> markers;

    Consumer(List<Marker> markers) {
        this.markers = markers;
    }
}

interface Marker {
}

@Retention(RetentionPolicy.RUNTIME)
@Indexed(Marker.class)
@interface MarkerStereotype {
}
''')
        Class<?> test = context.classLoader.loadClass('idx2.Test')
        Class<?> directlyIndexed = context.classLoader.loadClass('idx2.DirectlyIndexed')
        Class<?> impl = context.classLoader.loadClass('idx2.Impl')
        Class<?> marker = context.classLoader.loadClass('idx2.Marker')
        Class<?> consumer = context.classLoader.loadClass('idx2.Consumer')

        expect:
        !marker.isAssignableFrom(test)
        !marker.isAssignableFrom(directlyIndexed)

        when:
        Collection<BeanDefinition<?>> definitions = context.getBeanDefinitions(marker)

        then: 'all indexed beans and the real implementation are enumerable'
        definitions*.beanType.toSet() == [test, directlyIndexed, impl].toSet()
        definitions.size() == 3

        and: 'only the real implementation is resolved as an instance'
        context.getBean(marker).getClass() == impl
        context.getBeansOfType(marker)*.getClass() == [impl]
        context.getBean(consumer).markers*.getClass() == [impl]
        context.findBeanDefinition(marker).get().beanType == impl
        context.containsBean(marker)

        and: 'the indexed beans are still available by their own types'
        context.getBean(test) != null
        context.getBean(directlyIndexed) != null

        cleanup:
        context.close()
    }

    void "test a runtime registered bean definition with an index is enumerable by the indexed type"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        RuntimeBeanDefinition<RuntimeBean> delegate = RuntimeBeanDefinition.builder(RuntimeBean, () -> new RuntimeBean())
                .singleton(true)
                .build()
        RuntimeBeanDefinition<RuntimeBean> definition = new IndexedRuntimeBeanDefinition(delegate: delegate)
        context.registerBeanDefinition(definition)

        expect:
        !RuntimeMarker.isAssignableFrom(RuntimeBean)
        context.getBeanDefinitions(RuntimeMarker)*.beanType == [RuntimeBean]
        context.getBeanDefinitions(RuntimeBean)*.beanType == [RuntimeBean]
        context.getBean(RuntimeBean) != null
        !context.containsBean(RuntimeMarker)
        context.getBeansOfType(RuntimeMarker).isEmpty()

        when:
        context.getBean(RuntimeMarker)

        then:
        thrown(NoSuchBeanException)

        cleanup:
        context.close()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        [new IndexingVisitor()]
    }

    static class IndexingVisitor implements TypeElementVisitor<Object, Object> {

        @Override
        void visitClass(ClassElement element, VisitorContext context) {
            if (element.name == 'idx.Test') {
                element.annotate(Indexed) { builder ->
                    builder.member("value", new AnnotationClassValue<>("idx.Marker"))
                }
            }
        }

        @Override
        VisitorKind getVisitorKind() {
            return VisitorKind.ISOLATING
        }
    }

    static class RuntimeBean {
    }

    static interface RuntimeMarker {
    }

    static class IndexedRuntimeBeanDefinition implements RuntimeBeanDefinition<RuntimeBean> {
        @Delegate(excludes = ["getTargetAnnotationMetadata"])
        RuntimeBeanDefinition<RuntimeBean> delegate

        @Override
        Class<?>[] getIndexes() {
            return [RuntimeMarker] as Class<?>[]
        }

        @Override
        AnnotationMetadata getTargetAnnotationMetadata() {
            return delegate.getTargetAnnotationMetadata()
        }
    }
}
