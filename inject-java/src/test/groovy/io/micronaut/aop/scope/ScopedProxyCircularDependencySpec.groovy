package io.micronaut.aop.scope

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.BeanResolutionCustomizer
import io.micronaut.inject.BeanDefinition

class ScopedProxyCircularDependencySpec extends AbstractTypeElementSpec {

    @Override
    protected void configureContext(ApplicationContextBuilder builder) {
        builder.beanResolutionCustomizer(new BeanResolutionCustomizer() {
            @Override
            boolean shouldInitializeBean(BeanResolutionContext resolutionContext, BeanDefinition<?> beanDefinition, Object bean) {
                return !(beanDefinition.isProxy() && beanDefinition.annotationMetadata.hasStereotype("test.TestScope"))
            }

            @Override
            boolean shouldPreserveLazyProxyTargetResolutionPath(BeanResolutionContext resolutionContext, BeanDefinition<?> proxyBeanDefinition) {
                return !proxyBeanDefinition.annotationMetadata.hasStereotype("test.TestScope")
            }
        })
    }

    void "lazy scoped proxy can resolve circular field injection when proxy initialization is customized"() {
        given:
        ApplicationContext context = buildCircularContext()
        Class<?> pigType = context.classLoader.loadClass("test.Pig")
        Class<?> foodType = context.classLoader.loadClass("test.Food")

        when:
        def pig = context.getBean(pigType)
        def food = context.getBean(foodType)

        then:
        pig.getNameOfFood() == food.getName()
        food.getNameOfPig() == pig.getName()

        cleanup:
        context.close()
    }

    void "lazy scoped proxy uses default constructor without resolving target constructor dependencies"() {
        given:
        ApplicationContext context = buildCircularContext()
        Class<?> birdType = context.classLoader.loadClass("test.Bird")

        when:
        def bird = context.getBean(birdType)

        then:
        birdType.defaultConstructorCalls == 1
        birdType.injectedConstructorCalls == 0

        when:
        boolean hasAir = bird.hasAir()

        then:
        hasAir
        birdType.defaultConstructorCalls == 1
        birdType.injectedConstructorCalls == 1

        cleanup:
        context.close()
    }

    void "lazy scoped proxy can discard stale injection path before resolving target"() {
        given:
        ApplicationContext context = buildCircularContext()
        Class<?> consumerType = context.classLoader.loadClass("test.SelfConsumingConsumer")

        expect:
        context.getBean(consumerType).ping() == "ok"

        cleanup:
        context.close()
    }

    private ApplicationContext buildCircularContext() {
        buildContext("test.Pig", '''
package test;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.ScopedProxy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Scope
@ScopedProxy
@Around(proxyTarget = true, lazy = true)
@Retention(RetentionPolicy.RUNTIME)
@interface TestScope {
}

@Singleton
class TestCustomScope implements CustomScope<TestScope> {
    private final ConcurrentMap<BeanIdentifier, Object> beans = new ConcurrentHashMap<>();

    @Override
    public Class<TestScope> annotationType() {
        return TestScope.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        return (T) beans.computeIfAbsent(creationContext.id(), id -> creationContext.create().bean());
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.ofNullable((T) beans.remove(identifier));
    }
}

@TestScope
class Pig {
    @Inject
    Food food;

    public String getName() {
        return "pig";
    }

    public String getNameOfFood() {
        return food.getName();
    }
}

@TestScope
class Food {
    @Inject
    Pig pig;

    public String getName() {
        return "food";
    }

    public String getNameOfPig() {
        return pig.getName();
    }
}

@TestScope
class Bird {
    static int defaultConstructorCalls;
    static int injectedConstructorCalls;
    private final Air air;

    Bird() {
        defaultConstructorCalls++;
        this.air = null;
    }

    @Inject
    Bird(Air air) {
        injectedConstructorCalls++;
        this.air = air;
    }

    public boolean hasAir() {
        return air != null;
    }
}

@TestScope
class Air {
    Air() {
    }

    @Inject
    Air(Bird bird) {
    }
}

@Singleton
class SelfConsumingConsumer {
    private final SelfConsumingFactory factory;

    SelfConsumingConsumer(SelfConsumingFactory factory) {
        this.factory = factory;
    }

    public String ping() {
        return factory.ping();
    }
}

@Factory
@Prototype
class SelfConsumingFactory {
    @Inject
    @Named("self")
    Produced produced;

    public String ping() {
        return produced.ping();
    }

    @Bean
    @TestScope
    @Named("self")
    Produced produce() {
        return new Produced();
    }
}

class Produced {
    public String ping() {
        return "ok";
    }
}
''')
    }
}
