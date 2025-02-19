package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.annotation.Prototype
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Shared

import java.util.function.Supplier

class RuntimeBeanDefinitionSpec extends AbstractTypeElementSpec {

    @Shared
    BeanContext sharedContext = BeanContext.build()

    void "test runtime bean definition registered with builder"() {
        given:
        def foo = new Foo()
        def context = ApplicationContext.builder()
            .beanDefinitions(RuntimeBeanDefinition.of(foo))
            .build()
            .start()

        expect:
        context.getBeanDefinition(Foo)
        context.getBean(Foo).is(foo)
    }

    void 'test simple runtime bean definition'() {
        given:

        RuntimeBeanDefinition<Foo> bean = RuntimeBeanDefinition.of(new Foo())

        expect:
        bean.beanType == Foo
        bean.typeParameters.length == 0
        bean.typeArguments.size() == 0
        bean.annotationMetadata == AnnotationMetadata.EMPTY_METADATA
        bean.load().is(bean)
        bean.load(sharedContext).is(bean)
        bean.isEnabled(sharedContext)
        bean.declaredQualifier == null
        bean.isPresent()
        bean.singleton
    }

    void 'test simple runtime bean definition with qualifier'() {
        given:
        RuntimeBeanDefinition<?> bean = RuntimeBeanDefinition
                                    .builder(Argument.of(Supplier, String), () -> () -> "Foo")
                                    .qualifier(Qualifiers.byName("foo"))
                                    .scope(Prototype)
                                    .build()

        expect:
        bean.beanType == Supplier
        bean.scopeName.isPresent()
        bean.scope.isPresent()
        bean.scope.get() == Prototype
        bean.typeArguments.size() == 1
        bean.typeParameters.size() == 1
        bean.annotationMetadata == AnnotationMetadata.EMPTY_METADATA
        bean.load().is(bean)
        bean.load(sharedContext).is(bean)
        bean.isEnabled(sharedContext)
        bean.declaredQualifier == Qualifiers.byName("foo")
        bean.beanDefinitionName
        bean.isPresent()
        !bean.singleton
    }

    void 'test from supplier runtime bean definition with qualifier'() {
        given:
        RuntimeBeanDefinition<Foo> bean = RuntimeBeanDefinition.builder(Foo.class,() -> new Foo())
                                .qualifier(Qualifiers.byName("foo"))
                                .exposedTypes(IFoo)
                                .build()

        expect:
        bean.beanType == Foo
        bean.exposedTypes.size() == 1
        bean.exposedTypes.contains(IFoo)
        bean.annotationMetadata == AnnotationMetadata.EMPTY_METADATA
        bean.load().is(bean)
        bean.load(null).is(bean)
        bean.isEnabled(sharedContext)
        bean.declaredQualifier == Qualifiers.byName("foo")
        bean.beanDefinitionName
        bean.isPresent()
        !bean.scope.isPresent()
        !bean.singleton
    }

    void "test dynamic bean definition registration"() {
        given:
        ApplicationContext context = buildContext('''
package registerref;

import io.micronaut.context.BeanDefinitionRegistry;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.qualifiers.PrimaryQualifier;import io.micronaut.inject.qualifiers.Qualifiers;import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Collections;
import jakarta.inject.Singleton;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;

@Singleton
class Foo {
    @Inject @Named("test2") public Bazz bazz;
    @Inject public Bar bar;
    @Inject @Named("another") public Bar another;
}

@Context
@Order(-10)
class RegistrarA {
    static boolean executed = false;
    RegistrarA(BeanDefinitionRegistry registry) {
        executed = true;
        if(!RegistrarB.executed) {
            throw new IllegalStateException("RegistrarB should have been executed first");
        }
        if(RegistrarC.executed) {
            throw new IllegalStateException("RegistrarC should not have been executed yet");
        }
        registry.registerBeanDefinition(
          RuntimeBeanDefinition.builder(
                  Bar.class, () -> new Bar("primary")
          ).qualifier(PrimaryQualifier.instance())
          .build()
        );
    }
}

@Context
@Order(-15)
class RegistrarB {
    static boolean executed = false;
    RegistrarB(BeanDefinitionRegistry registry) {
        executed = true;
        if(RegistrarC.executed) {
            throw new IllegalStateException("RegistrarC should not have been executed yet");
        }
        if(RegistrarA.executed) {
            throw new IllegalStateException("RegistrarA should not have been executed yet");
        }

        registry.registerBeanDefinition(
          RuntimeBeanDefinition.builder(
                  Bar.class, () -> new Bar("another")
          ).qualifier(Qualifiers.byName("another"))
          .build()
        );
    }
}

@Context
class RegistrarC {
    static boolean executed = false;
    RegistrarC(BeanDefinitionRegistry registry) {
        if(!RegistrarB.executed) {
            throw new IllegalStateException("RegistrarB should have been executed first");
        }
        if(!RegistrarA.executed) {
            throw new IllegalStateException("RegistrarA should have been executed first");
        }
        executed = true;
        registry.registerBeanDefinition(
          RuntimeBeanDefinition.of(
                  new Stuff()
          )
        );
        registry.registerBeanDefinition(
          RuntimeBeanDefinition.builder(
                  Bazz.class,
                  () -> new BazzImpl(1)
          ).named("test").build()
        );
        registry.registerBeanDefinition(
          RuntimeBeanDefinition.builder(
                  Bazz.class,
                  () -> new BazzImpl(2)
          ).named("test2").build()
        );
    }
}

class Bar {

    final public String name;
    Bar(String name) {
        this.name = name;
    }
}

class Baz {}

class Stuff {}

interface Bazz {}
class BazzImpl implements Bazz  {
    public final int num;
    BazzImpl(int num) {
        this.num = num;
    }
}
''')
        def foo = getBean(context, 'registerref.Foo')
        expect:
        foo.bar != null
        foo.bazz != null
        foo.bazz.num == 2
        foo.bar.name == 'primary'
        foo.another.name == 'another'

        cleanup:
        context.close()
    }

    class Foo implements IFoo {}

    interface IFoo {}
}
