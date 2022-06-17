package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class RuntimeBeanDefinitionSpec extends AbstractTypeElementSpec {

    void "test dynamic bean definition registration"() {
        given:
        def context = buildContext('''
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
          RuntimeBeanDefinition.of(
                  Bar.class, () -> new Bar("primary"), PrimaryQualifier.instance()
          )
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
          RuntimeBeanDefinition.of(
                  Bar.class, () -> new Bar("another"), Qualifiers.byName("another")
          )
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
                  Stuff.class, () -> new Stuff()
          )
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
''')
        def foo = getBean(context, 'registerref.Foo')
        expect:
        foo.bar != null
        foo.bar.name == 'primary'
        foo.another.name == 'another'

        cleanup:
        context.close()
    }

}
