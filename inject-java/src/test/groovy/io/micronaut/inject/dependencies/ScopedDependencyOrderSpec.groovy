package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import spock.lang.Unroll

class ScopedDependencyOrderSpec extends AbstractTypeElementSpec {
    @Unroll
    void "scope destruction respects static and late dependencies with per-bean locking #perBean"() {
        given:
        def ctx = buildContext('''
package test;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.scope.*;
import io.micronaut.inject.BeanIdentifier;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
@Scope @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@interface TestScoped {}
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }
@Singleton class TestScope extends AbstractConcurrentCustomScope<TestScoped> {
    final Map<BeanIdentifier, CreatedBean<?>> beans = new ConcurrentHashMap<>();
    TestScope() { super(TestScoped.class, PER_BEAN); }
    public boolean isRunning() { return true; }
    protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean create) { return beans; }
    public void close() { destroyScope(beans); }
}
@Bean @TestScoped class AResource {
    boolean closed;
    @PreDestroy void stop() { closed = true; Log.events.add("resource"); }
}
@Bean @TestScoped class MStaticOwner {
    final AResource resource;
    MStaticOwner(AResource resource) { this.resource = resource; }
    @PreDestroy void stop() { Log.events.add("static:" + resource.closed); }
}
@Bean @TestScoped class ZDynamicOwner {
    final BeanDependencyResolver resolver;
    AResource resource;
    ZDynamicOwner(BeanDependencyResolver resolver) { this.resolver = resolver; }
    void init() { resource = resolver.getBean(AResource.class); }
    @PreDestroy void stop() { Log.events.add("dynamic:" + resource.closed); }
}
'''.replace('PER_BEAN', perBean.toString()))
        ctx.getBean(ctx.classLoader.loadClass('test.MStaticOwner'))
        ctx.getBean(ctx.classLoader.loadClass('test.ZDynamicOwner')).init()
        def log = ctx.classLoader.loadClass('test.Log')
        def scope = ctx.getBean(ctx.classLoader.loadClass('test.TestScope'))

        when:
        scope.close()

        then:
        log.events == ['static:false', 'dynamic:false', 'resource']
        scope.beans.isEmpty()

        when:
        scope.close()

        then:
        log.events.size() == 3

        cleanup:
        ctx.close()

        where:
        perBean << [false, true]
    }
}
