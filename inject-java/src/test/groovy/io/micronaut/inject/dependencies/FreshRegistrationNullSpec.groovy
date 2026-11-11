package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition

class FreshRegistrationNullSpec extends AbstractTypeElementSpec {
    private boolean reject

    void "fresh creation applies null customization and retains dependent cleanup - grouped #grouped reject #rejectNull"() {
        given:
        reject = rejectNull
        def context = buildContext('''
package freshnull;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Singleton;
import jakarta.annotation.PreDestroy;
@Prototype class Resource {
    static int destroyed;
    @PreDestroy void stop() { destroyed++; }
}
class Product { }
@Factory class Products {
    @Singleton @Nullable Product product(Resource resource) { return null; }
}
''')
        def product = context.classLoader.loadClass('freshnull.Product')
        def resource = context.classLoader.loadClass('freshnull.Resource')
        def definition = context.getBeanDefinition(product)
        def group = context.createDependencyGroup()
        def registration
        Throwable failure

        when:
        try {
            registration = grouped ? group.createBeanRegistration(definition) : context.createBeanRegistration(definition)
        } catch (IllegalArgumentException e) {
            failure = e
        }

        then:
        if (rejectNull) {
            assert failure?.message == 'null product'
            assert resource.destroyed == 1
        } else {
            assert failure == null
            assert product.isInstance(registration.bean())
            assert resource.destroyed == 0
        }

        when:
        group.close()
        registration?.close()

        then:
        resource.destroyed == 1

        cleanup:
        context.close()

        where:
        grouped | rejectNull
        false   | false
        true    | false
        false   | true
        true    | true
    }

    @Override
    protected void configureContext(ApplicationContextBuilder builder) {
        builder.beanResolutionCustomizer(new BeanResolutionCustomizer() {
            @Override
            Optional<?> resolveNullBean(Argument<?> requested, Argument<?> resolved, BeanDefinition<?> definition) {
                if (definition.beanType.name != 'freshnull.Product') {
                    return Optional.empty()
                }
                if (reject) {
                    throw new IllegalArgumentException('null product')
                }
                def constructor = definition.beanType.getDeclaredConstructor()
                constructor.accessible = true
                return Optional.of(constructor.newInstance())
            }
        })
    }
}
