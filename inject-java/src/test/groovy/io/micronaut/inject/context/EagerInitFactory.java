package io.micronaut.inject.context;

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.exceptions.DisabledBeanException;
import io.micronaut.inject.InjectionPoint;
import io.micronaut.inject.annotation.ScopeOne;

@Factory
public class EagerInitFactory {

    @Bean
    @ScopeOne
    C createC(@Nullable InjectionPoint<?> i) {
        throw new DisabledBeanException("C is disabled");
    }

    public static class C {
    }
}
