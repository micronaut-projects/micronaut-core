package io.micronaut.inject.indexed

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.event.BeanDestroyedEventListener
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

import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
class Test {
}

interface Marker {
}

@Singleton
@Requires(missingBeans = Marker.class)
class MissingMarkerCondition {
}

@Singleton
@Requires(beans = Marker.class)
class PresentMarkerCondition {
}
''')
        Class<?> test = context.classLoader.loadClass('idx.Test')
        Class<?> marker = context.classLoader.loadClass('idx.Marker')
        Class<?> missingMarkerCondition = context.classLoader.loadClass('idx.MissingMarkerCondition')
        Class<?> presentMarkerCondition = context.classLoader.loadClass('idx.PresentMarkerCondition')

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
        context.containsBean(missingMarkerCondition)
        !context.containsBean(presentMarkerCondition)

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
import io.micronaut.context.BeanProvider;
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
    final BeanProvider<Marker> provider;

    Consumer(List<Marker> markers, BeanProvider<Marker> provider) {
        this.markers = markers;
        this.provider = provider;
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
        context.getBean(consumer).provider.present
        context.getBean(consumer).provider.unique
        context.getBean(consumer).provider.resolvable
        context.getBean(consumer).provider.get().getClass() == impl
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
        assert context.getBeanDefinitions(RuntimeMarker).isEmpty()
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

    void "test a bean indexed by a listener interface it does not implement is not treated as a listener"() {
        given: "a factory that is a listener, and so is indexed by the listener type its produced bean inherits"
        ApplicationContext context = buildContext('idx.Recorded', '''
package idx;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.NonNull;
import jakarta.inject.Singleton;

class Recorded {
}

@Factory
class RecordedFactory implements BeanDestroyedEventListener<Recorded> {

    static boolean destroyed;

    @Singleton
    Recorded recorded() {
        return new Recorded();
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Recorded> event) {
        destroyed = true;
    }
}
''')
        Class<?> recorded = context.classLoader.loadClass('idx.Recorded')
        Class<?> factory = context.classLoader.loadClass('idx.RecordedFactory')

        expect: "the produced bean is enumerable by the inherited index, though it is no listener"
        !BeanDestroyedEventListener.isAssignableFrom(recorded)

        when: "the produced bean is created, then destroyed with the context, which dispatches to the listeners"
        def bean = context.getBean(recorded)
        context.close()

        then: "the factory listener ran and the produced bean was never cast to one"
        bean != null
        factory.destroyed
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
