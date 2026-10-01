package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.chain.MethodInterceptorChain
import io.micronaut.context.BeanResolutionContext
import io.micronaut.context.DefaultBeanResolutionContext

class LifecycleInterceptorCandidatesSpec extends AbstractTypeElementSpec {

    void 'pre-destroy-only advice is acquired with its owner and released after it - factory #factory'() {
        given:
        def source = '''
package candidates;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.PreDestroy;
import java.lang.annotation.*;
import java.util.*;
class Events { static final List<String> values = new ArrayList<>(); }
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Finish {}
@Prototype
@InterceptorBean(Finish.class)
class Advice implements MethodInterceptor<Object, Object> {
    Advice() { Events.values.add("advice-created"); }
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.values.add("advice");
        return context.proceed();
    }
    @PreDestroy void close() { Events.values.add("advice-destroyed"); }
}
TARGET_ANNOTATIONS
class TargetBean {
    TargetBean() { Events.values.add("target-created"); }
    @PreDestroy void close() { Events.values.add("target-destroyed"); }
}
FACTORY
'''.replace('TARGET_ANNOTATIONS', factory ? '' : '@Prototype @Finish')
            .replace('FACTORY', factory ? '''
@Factory class Products {
    @Bean(preDestroy = "close") @Prototype @Finish TargetBean target() { return new TargetBean(); }
}
''' : '')
        def ctx = buildContext(source)
        def type = ctx.classLoader.loadClass('candidates.TargetBean')
        def events = ctx.classLoader.loadClass('candidates.Events')

        when:
        def registration = ctx.getBeanRegistration(type, null)

        then:
        events.values == ['target-created', 'advice-created']

        when:
        ctx.destroyBean(registration)

        then:
        events.values == ['target-created', 'advice-created', 'advice', 'target-destroyed', 'advice-destroyed']

        cleanup:
        ctx.close()

        where:
        factory << [false, true]
    }

    void 'distinct lifecycle bindings are retained before initialization - failure #failure'() {
        given:
        def ctx = buildContext('''
package candidates.phases;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.annotation.*;
import java.lang.annotation.*;
import java.util.*;
class Events { static final List<String> values = new ArrayList<>(); }
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@interface Start {}
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Finish {}
@Prototype @InterceptorBean(Start.class)
class StartAdvice implements MethodInterceptor<Object, Object> {
    StartAdvice() { Events.values.add("start-created"); }
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.values.add("start");
        return context.proceed();
    }
    @PreDestroy void close() { Events.values.add("start-destroyed"); }
}
@Prototype @InterceptorBean(Finish.class)
class FinishAdvice implements MethodInterceptor<Object, Object> {
    FinishAdvice() { Events.values.add("finish-created"); }
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.values.add("finish");
        return context.proceed();
    }
    @PreDestroy void close() { Events.values.add("finish-destroyed"); }
}
@Prototype @Start @Finish class TargetBean {
    @PostConstruct void init() {
        Events.values.add("init");
        FAILURE
    }
    @PreDestroy void close() { Events.values.add("close"); }
}
'''.replace('FAILURE', failure ? 'throw new IllegalStateException("initialization failed");' : ''))
        def type = ctx.classLoader.loadClass('candidates.phases.TargetBean')
        def events = ctx.classLoader.loadClass('candidates.phases.Events')

        when:
        def registration
        Throwable creationFailure
        try {
            registration = ctx.getBeanRegistration(type, null)
        } catch (Throwable e) {
            creationFailure = e
        }

        then:
        (creationFailure != null) == failure
        !failure || creationFailure.message.contains('initialization failed')
        events.values.take(2).toSet() == ['start-created', 'finish-created'].toSet()
        events.values[2..3] == ['start', 'init']

        when:
        if (registration != null) {
            ctx.destroyBean(registration)
        }

        then:
        events.values.count('start-created') == 1
        events.values.count('finish-created') == 1
        events.values.count('start-destroyed') == 1
        events.values.count('finish-destroyed') == 1
        failure || events.values[4..5] == ['finish', 'close']
        !failure || !events.values.contains('finish')
        !failure || !events.values.contains('close')

        cleanup:
        ctx.close()

        where:
        failure << [false, true]
    }

    void 'an authoritative empty candidate set does not fall back to lookup - #phase shared #shared'() {
        given:
        def ctx = buildContext('''
package candidates.empty;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;
class Events { static final List<String> values = new ArrayList<>(); }
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Tracked {}
@Singleton @InterceptorBean(Tracked.class)
class Advice implements MethodInterceptor<Object, Object> {
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        Events.values.add("advice");
        return context.proceed();
    }
}
@Singleton @Tracked class TargetBean {
    @Executable public TargetBean event() {
        Events.values.add("body");
        return this;
    }
}
''')
        def type = ctx.classLoader.loadClass('candidates.empty.TargetBean')
        def bean = ctx.getBean(type)
        def definition = ctx.getBeanDefinition(type)
        def method = definition.getRequiredMethod('event')
        def events = ctx.classLoader.loadClass('candidates.empty.Events')
        def resolution = new DefaultBeanResolutionContext(ctx, definition)
        resolution.setBeanInterceptors(definition, [])
        events.values.clear()

        when:
        def result = phase == 'initialize'
            ? MethodInterceptorChain.initialize(resolution, ctx, definition, method, bean, shared)
            : MethodInterceptorChain.dispose(resolution, ctx, definition, method, bean, shared)

        then:
        result.is(bean)
        events.values == ['body']

        when: 'a legacy caller with no recorded set still resolves advice'
        resolution.removeAttribute(BeanResolutionContext.INTERCEPTOR_REGISTRATIONS)
        events.values.clear()
        if (phase == 'initialize') {
            MethodInterceptorChain.initialize(resolution, ctx, definition, method, bean, shared)
        } else {
            MethodInterceptorChain.dispose(resolution, ctx, definition, method, bean, shared)
        }

        then:
        events.values == ['advice', 'body']

        cleanup:
        resolution.close()
        ctx.close()

        where:
        [phase, shared] << [['initialize', 'dispose'], [null, []]].combinations()
    }
}
