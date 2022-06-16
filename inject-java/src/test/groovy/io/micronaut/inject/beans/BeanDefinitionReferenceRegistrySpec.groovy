package io.micronaut.inject.beans

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class BeanDefinitionReferenceRegistrySpec extends AbstractTypeElementSpec {

    void "test dynamic bean definition registration"() {
        given:
        def context = buildContext('''
package registerref;

import io.micronaut.context.BeanDefinitionReferenceRegistry;
import io.micronaut.context.RuntimeBeanDefinition;
import io.micronaut.context.annotation.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
class Foo {
    @Inject public Bar bar;
}

@Context
class Registrar {
    Registrar(BeanDefinitionReferenceRegistry registry) {
        registry.registerBeanReference(
          RuntimeBeanDefinition.of(
                  Bar.class, () -> new Bar()
          )
        );
    }
}

class Bar {}

''')
        def foo = getBean(context, 'registerref.Foo')
        expect:
        foo.bar != null

        cleanup:
        context.close()
    }

}
