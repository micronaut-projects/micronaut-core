package io.micronaut.aop.scope

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * A bean produced by a factory method declares its scope on the produced bean, not on the factory. Resolving
 * the factory through that scope would re-enter it for a second bean while the first is still being created,
 * which a scope backed by {@code ConcurrentHashMap.computeIfAbsent} answers with "Recursive update" whenever
 * the two bean keys happen to share a bin.
 */
class CustomScopeReentrancySpec extends AbstractTypeElementSpec {

    void "test a prototype factory is not resolved through the scope of the bean it produces"() {
        given:
        ApplicationContext context = buildContext('test.Produced', '''
package test;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.scope.BeanCreationContext;
import io.micronaut.context.scope.CustomScope;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

@Scope
@Retention(RetentionPolicy.RUNTIME)
@interface CountingScope {
}

@Singleton
class CountingCustomScope implements CustomScope<CountingScope> {
    public static final AtomicInteger DEPTH = new AtomicInteger();
    public static final AtomicInteger MAX_DEPTH = new AtomicInteger();

    private final ConcurrentMap<BeanIdentifier, Object> beans = new ConcurrentHashMap<>();

    @Override
    public Class<CountingScope> annotationType() {
        return CountingScope.class;
    }

    @Override
    public <T> T getOrCreate(BeanCreationContext<T> creationContext) {
        int depth = DEPTH.incrementAndGet();
        MAX_DEPTH.accumulateAndGet(depth, Math::max);
        try {
            return (T) beans.computeIfAbsent(creationContext.id(), id -> creationContext.create().bean());
        } finally {
            DEPTH.decrementAndGet();
        }
    }

    @Override
    public <T> Optional<T> remove(BeanIdentifier identifier) {
        return Optional.ofNullable((T) beans.remove(identifier));
    }
}

@Factory
@Prototype
class ProducingFactory {
    @Bean
    @CountingScope
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
        Class<?> producedType = context.classLoader.loadClass('test.Produced')
        Class<?> scopeType = context.classLoader.loadClass('test.CountingCustomScope')

        when: 'the scoped bean is created through its prototype factory'
        def produced = context.getBean(producedType)

        then: 'it is created'
        produced.ping() == 'ok'

        and: 'the scope was entered once, not re-entered to resolve the factory itself'
        maxDepth(scopeType) == 1

        cleanup:
        context.close()
    }

    private static int maxDepth(Class<?> scopeType) {
        def field = scopeType.getDeclaredField('MAX_DEPTH')
        field.setAccessible(true)
        ((java.util.concurrent.atomic.AtomicInteger) field.get(null)).get()
    }
}
