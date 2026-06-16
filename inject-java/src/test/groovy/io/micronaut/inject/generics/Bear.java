package io.micronaut.inject.generics;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

public interface Bear<T> {
    String name();
}

interface Habitat {
}

class Arctic implements Habitat {
}

class Forest implements Habitat {
}

@Requires(property = "spec.name", value = "GenericInjectionSpec")
@Singleton
@InterceptedBear
class WhiteBear implements Bear<Arctic> {
    @Override
    public String name() {
        return "white";
    }
}

@Requires(property = "spec.name", value = "GenericInjectionSpec")
@Singleton
class BrownBear implements Bear<Forest> {
    @Override
    public String name() {
        return "brown";
    }
}

@Requires(property = "spec.name", value = "GenericInjectionSpec")
@Singleton
class ForestDen {
    private final Bear<Forest> bear;

    @Inject
    Bear<Forest> fieldBear;

    @Inject
    List<Bear<Forest>> bears;

    @Inject
    Provider<Bear<Forest>> bearProvider;

    ForestDen(Bear<Forest> bear) {
        this.bear = bear;
    }

    Bear<Forest> getBear() {
        return bear;
    }
}

@Around
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@interface InterceptedBear {
}

@Requires(property = "spec.name", value = "GenericInjectionSpec")
@Singleton
@InterceptorBean(InterceptedBear.class)
class InterceptedBearInterceptor implements MethodInterceptor<Object, Object> {

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed();
    }
}
