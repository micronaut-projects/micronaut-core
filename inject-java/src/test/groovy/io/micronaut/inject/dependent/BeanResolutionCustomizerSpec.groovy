package io.micronaut.inject.dependent

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition

class BeanResolutionCustomizerSpec extends AbstractTypeElementSpec {

    void "customizer can destroy dependent beans after the current resolution completes"() {
        given:
        def context = buildContext('test.Owner', '''
package test;

import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
@interface DestroyAfterResolution {
}

@Prototype
class Helper {
    static final List<String> DESTROYED = new ArrayList<>();
    String owner;

    @PreDestroy
    void close() {
        DESTROYED.add(owner);
    }
}

@Singleton
class Owner {
    @Inject
    Owner(@DestroyAfterResolution Helper transientHelper, Helper normalHelper) {
        transientHelper.owner = "ctor-transient";
        normalHelper.owner = "ctor-normal";
    }

    @Inject
    void init(@DestroyAfterResolution Helper transientHelper, Helper normalHelper) {
        transientHelper.owner = "init-transient";
        normalHelper.owner = "init-normal";
    }
}
''')
        def owner = getBean(context, 'test.Owner')
        def helperClass = context.classLoader.loadClass('test.Helper')

        expect:
        helperClass.DESTROYED == ['ctor-transient', 'init-transient']

        when:
        context.destroyBean(owner)

        then:
        helperClass.DESTROYED.containsAll(['ctor-normal', 'init-normal'])

        cleanup:
        context.close()
    }

    void "containsBean checks candidate presence for resolved lookup argument"() {
        given:
        def context = buildContext('test.LookupBean', '''
package test;

import jakarta.inject.Singleton;

interface LookupView {
}

@Singleton
class LookupBean {
}
''')
        Class<?> lookupView = context.classLoader.loadClass('test.LookupView')

        expect:
        context.containsBean(lookupView)

        cleanup:
        context.close()
    }

    void "resolved lookup argument can resolve matching bean"() {
        given:
        def context = buildContext('test.LookupBean', '''
package test;

import jakarta.inject.Singleton;

interface LookupView {
}

@Singleton
class LookupBean {
}
''')
        Class<?> lookupView = context.classLoader.loadClass('test.LookupView')

        when:
        def bean = context.getBean(lookupView)
        def beans = context.getBeansOfType(lookupView)

        then:
        bean.class.name == 'test.LookupBean'
        beans*.class*.name == ['test.LookupBean']

        cleanup:
        context.close()
    }

    void "array-as-bean lookup falls back to beans of type when exact array bean is absent"() {
        given:
        def context = buildContext('test.ArrayConsumer', '''
package test;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

interface ArrayThing {
}

@Singleton
class FirstArrayThing implements ArrayThing {
}

@Singleton
class SecondArrayThing implements ArrayThing {
}

@Singleton
class ArrayConsumer {
    final ArrayThing[] things;

    @Inject
    ArrayConsumer(ArrayThing[] things) {
        this.things = things;
    }
}
''')

        when:
        def consumer = getBean(context, 'test.ArrayConsumer')

        then:
        consumer.things.length == 2
        consumer.things.collect { it.class.simpleName } as Set == ['FirstArrayThing', 'SecondArrayThing'] as Set

        cleanup:
        context.close()
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.beanResolutionCustomizer(new BeanResolutionCustomizer() {
            @Override
            Argument<?> resolveBeanLookupArgument(Argument<?> beanType) {
                if (beanType.type.name == 'test.LookupView') {
                    return Argument.of(Class.forName('test.LookupBean', false, beanType.type.classLoader))
                }
                return beanType
            }

            @Override
            boolean shouldResolveArrayAsBean(Argument<?> injectionPoint) {
                return injectionPoint.array && injectionPoint.type.componentType.name == 'test.ArrayThing'
            }

            @Override
            boolean shouldDestroyDependentBeanAfterResolution(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition) {
                return resolutionContext.path.currentSegment()
                    .map(segment -> segment.argument.annotationMetadata.hasAnnotation('test.DestroyAfterResolution'))
                    .orElse(false)
            }
        })
    }
}
