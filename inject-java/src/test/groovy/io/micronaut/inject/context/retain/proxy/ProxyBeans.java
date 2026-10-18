package io.micronaut.inject.context.retain.proxy;

import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.Introduction;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.scope.AbstractConcurrentCustomScope;
import io.micronaut.context.scope.CreatedBean;
import io.micronaut.inject.BeanIdentifier;
import io.micronaut.runtime.context.scope.Refreshable;
import io.micronaut.runtime.context.scope.ScopedProxy;
import io.micronaut.runtime.http.scope.RequestScope;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Scope;
import jakarta.inject.Singleton;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every kind of proxy a retained bean can hold or be held by, and interceptors of every scope, for
 * {@code RetainedProxySpec}.
 */
public final class ProxyBeans {

    /**
     * The beans whose {@code @PreDestroy} ran, by class.
     */
    public static final Set<Class<?>> DESTROYED = ConcurrentHashMap.newKeySet();

    private ProxyBeans() {
    }

    // ---- interceptor bindings

    /**
     * Intercepted by a singleton interceptor holding nothing.
     */
    @Around
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Counted {
    }

    /**
     * Intercepted by a singleton interceptor, through a proxy with a separate target.
     */
    @Around(proxyTarget = true)
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface CountedTarget {
    }

    /**
     * Intercepted by a prototype interceptor, one per proxy.
     */
    @Around
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface PerProxy {
    }

    /**
     * Intercepted by a singleton interceptor that holds the context.
     */
    @Around
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Contextual {
    }

    /**
     * Implemented by an introduction interceptor.
     */
    @Introduction
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    public @interface Stub {
    }

    /**
     * A custom scope behind a scoped proxy.
     */
    @ScopedProxy
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    public @interface Tenant {
    }

    /**
     * A lazy proxy that caches the target it resolved.
     */
    @Around(lazy = true, proxyTarget = true, cacheableLazyTarget = true)
    @ScopedProxy
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    public @interface CachedLazy {
    }

    /**
     * A lazy proxy that resolves its target on every call.
     */
    @Around(lazy = true, proxyTarget = true)
    @ScopedProxy
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.METHOD})
    @Scope
    public @interface UncachedLazy {
    }

    // ---- interceptors

    @Singleton
    @InterceptorBean({Counted.class, CountedTarget.class})
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class CountingInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed();
        }
    }

    @Prototype
    @InterceptorBean(PerProxy.class)
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class PrototypeInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed();
        }
    }

    @Singleton
    @InterceptorBean(Contextual.class)
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class ContextualInterceptor implements MethodInterceptor<Object, Object> {
        public final BeanContext context;

        ContextualInterceptor(BeanContext context) {
            this.context = context;
        }

        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return context.proceed();
        }
    }

    @Singleton
    @InterceptorBean(Stub.class)
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class StubInterceptor implements MethodInterceptor<Object, Object> {
        @Override
        public Object intercept(MethodInvocationContext<Object, Object> context) {
            return "stub";
        }
    }

    // ---- the custom scope

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TenantScope extends AbstractConcurrentCustomScope<Tenant> {
        private final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();

        public TenantScope() {
            super(Tenant.class);
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            return beans;
        }

        @Override
        public void close() {
            destroyScope(beans);
        }
    }

    // ---- what is retained, or held by a proxy

    /**
     * Holds nothing: retained whatever holds it.
     */
    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class Plain {
        @PreDestroy
        void close() {
            DESTROYED.add(Plain.class);
        }
    }

    // ---- proxied beans

    @Singleton
    @Counted
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class AroundService {
        public String call() {
            return "around";
        }
    }

    @Singleton
    @CountedTarget
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TargetService {
        public String call() {
            return "target";
        }

        @PreDestroy
        void close() {
            DESTROYED.add(TargetService.class);
        }
    }

    @Singleton
    @PerProxy
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class PrototypeInterceptedService {
        public String call() {
            return "prototype";
        }
    }

    @Singleton
    @Contextual
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class ContextInterceptedService {
        public String call() {
            return "contextual";
        }
    }

    /**
     * An introduction proxy.
     */
    @Stub
    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public interface Greeter {
        String greet();
    }

    @Refreshable
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class RefreshableService {
        public String call() {
            return "refreshable";
        }
    }

    @RequestScope
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class RequestService {
        public String call() {
            return "request";
        }
    }

    @io.micronaut.runtime.context.scope.ThreadLocal
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class ThreadService {
        public String call() {
            return "thread";
        }
    }

    @Tenant
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TenantService {
        public String call() {
            return "tenant";
        }
    }

    @CachedLazy
    @Prototype
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class CachedLazyService {
        public String call() {
            return "cached";
        }
    }

    @UncachedLazy
    @Prototype
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class UncachedLazyService {
        public String call() {
            return "uncached";
        }
    }

    // ---- singletons that hold a proxy, each a candidate for retention

    /**
     * A singleton holding one proxy.
     */
    public abstract static class Holder {
        public final Object proxy;

        protected Holder(Object proxy) {
            this.proxy = proxy;
        }

        @PreDestroy
        void close() {
            DESTROYED.add(getClass());
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class AroundHolder extends Holder {
        AroundHolder(AroundService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TargetHolder extends Holder {
        TargetHolder(TargetService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class PrototypeInterceptedHolder extends Holder {
        PrototypeInterceptedHolder(PrototypeInterceptedService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class ContextInterceptedHolder extends Holder {
        ContextInterceptedHolder(ContextInterceptedService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class IntroductionHolder extends Holder {
        IntroductionHolder(Greeter proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class RefreshableHolder extends Holder {
        RefreshableHolder(RefreshableService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class RequestHolder extends Holder {
        RequestHolder(RequestService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class ThreadHolder extends Holder {
        ThreadHolder(ThreadService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TenantHolder extends Holder {
        TenantHolder(TenantService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class CachedLazyHolder extends Holder {
        CachedLazyHolder(CachedLazyService proxy) {
            super(proxy);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class UncachedLazyHolder extends Holder {
        UncachedLazyHolder(UncachedLazyService proxy) {
            super(proxy);
        }
    }

    /**
     * Holds a singleton that holds a proxy: refused with it.
     */
    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class Outer {
        public final AroundHolder inner;
        public final Plain plain;

        Outer(AroundHolder inner, Plain plain) {
            this.inner = inner;
            this.plain = plain;
        }
    }

    // ---- proxies that hold a retained bean

    @Singleton
    @Counted
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class AroundUser {
        public final Plain plain;

        AroundUser(Plain plain) {
            this.plain = plain;
        }

        public Plain plain() {
            return plain;
        }
    }

    @Singleton
    @CountedTarget
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TargetUser {
        public final Plain plain;

        TargetUser(Plain plain) {
            this.plain = plain;
        }

        public Plain plain() {
            return plain;
        }
    }

    @Refreshable
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class RefreshableUser {
        private final Plain plain;

        RefreshableUser(Plain plain) {
            this.plain = plain;
        }

        public Plain plain() {
            return plain;
        }
    }

    @Tenant
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class TenantUser {
        private final Plain plain;

        TenantUser(Plain plain) {
            this.plain = plain;
        }

        public Plain plain() {
            return plain;
        }
    }

    @CachedLazy
    @Prototype
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class CachedLazyUser {
        private final Plain plain;

        CachedLazyUser(Plain plain) {
            this.plain = plain;
        }

        public Plain plain() {
            return plain;
        }
    }

    @Stub
    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public abstract static class PlainGreeter {
        @jakarta.inject.Inject
        Plain plain;

        public Plain plain() {
            return plain;
        }

        public abstract String greet();
    }

    /**
     * Holds proxies and the retained bean through them.
     */
    @Singleton
    @Requires(property = "spec.name", value = "RetainedProxySpec")
    public static class ProxyUsers {
        public final AroundUser around;
        public final TargetUser target;
        public final RefreshableUser refreshable;
        public final TenantUser tenant;
        public final CachedLazyUser cachedLazy;
        public final PlainGreeter greeter;

        ProxyUsers(AroundUser around, TargetUser target, RefreshableUser refreshable, TenantUser tenant,
                   CachedLazyUser cachedLazy, PlainGreeter greeter) {
            this.around = around;
            this.target = target;
            this.refreshable = refreshable;
            this.tenant = tenant;
            this.cachedLazy = cachedLazy;
            this.greeter = greeter;
        }
    }
}
