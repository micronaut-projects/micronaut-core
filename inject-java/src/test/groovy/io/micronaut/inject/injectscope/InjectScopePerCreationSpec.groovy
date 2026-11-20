package io.micronaut.inject.injectscope

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import java.util.concurrent.TimeUnit

class InjectScopePerCreationSpec extends AbstractTypeElementSpec {

    void "finishing a nested creation does not destroy the inject scoped arguments of the outer creation"() {
        given:
        def context = buildContext('''
package injectscopenested;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

@Bean
class A {
    public boolean destroyed;

    @PreDestroy
    void close() {
        destroyed = true;
    }
}

@Bean
class C {
    public boolean destroyed;

    @PreDestroy
    void close() {
        destroyed = true;
    }
}

@Bean
class B {
    public final C c;
    public final boolean cDestroyedInConstructor;

    B(@InjectScope C c) {
        this.c = c;
        this.cDestroyedInConstructor = c.destroyed;
    }
}

@Prototype
class X {
    public final A a;
    public final boolean aDestroyedInConstructor;
    public A methodA;
    public boolean methodADestroyedInMethod;

    X(@InjectScope A a, B b) {
        this.a = a;
        this.aDestroyedInConstructor = a.destroyed;
    }

    @Inject
    void init(@InjectScope A methodA, B b) {
        this.methodA = methodA;
        this.methodADestroyedInMethod = methodA.destroyed;
    }
}
''')

        when:
        def x = getBean(context, 'injectscopenested.X')

        then: 'the argument was alive while the constructor and the method ran'
        !x.aDestroyedInConstructor
        !x.methodADestroyedInMethod

        and: 'it was destroyed once the creation ended'
        x.a.destroyed
        x.methodA.destroyed

        cleanup:
        context.close()
    }

    void "a creation on another thread does not destroy the inject scoped arguments of a creation in progress"() {
        given:
        def context = buildContext('''
package injectscopeconcurrent;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Prototype;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Bean
class A {
    public boolean destroyed;

    @PreDestroy
    void close() {
        destroyed = true;
    }
}

@Singleton
class Gate {
    public final CountDownLatch entered = new CountDownLatch(1);
    public final CountDownLatch release = new CountDownLatch(1);
}

@Bean
class Blocker {
    Blocker(Gate gate) throws InterruptedException {
        gate.entered.countDown();
        gate.release.await(10, TimeUnit.SECONDS);
    }
}

@Prototype
class Slow {
    public final A a;
    public final boolean aDestroyedInConstructor;

    Slow(@InjectScope A a, Blocker blocker) {
        this.a = a;
        this.aDestroyedInConstructor = a.destroyed;
    }
}

@Prototype
class Fast {
    public final A a;

    Fast(@InjectScope A a) {
        this.a = a;
    }
}
''')
        def gate = getBean(context, 'injectscopeconcurrent.Gate')
        def slow = null
        def thread = Thread.start { slow = getBean(context, 'injectscopeconcurrent.Slow') }

        when: 'another bean with an inject scoped argument is created while the slow creation waits'
        assert gate.entered.await(10, TimeUnit.SECONDS)
        def fast = getBean(context, 'injectscopeconcurrent.Fast')
        gate.release.countDown()
        thread.join(10_000)

        then:
        fast.a.destroyed
        slow != null
        !slow.aDestroyedInConstructor
        slow.a.destroyed

        cleanup:
        gate?.release?.countDown()
        context.close()
    }

    void "a failed creation destroys its inject scoped arguments and leaves the outer ones alive"() {
        given:
        def context = buildContext('''
package injectscopefailed;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.InjectScope;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.BeanProvider;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Prototype
class A {
    public static final List<A> CREATED = new ArrayList<>();
    public boolean destroyed;

    A() {
        CREATED.add(this);
    }

    @PreDestroy
    void close() {
        destroyed = true;
    }
}

@Bean
class Failing {
    Failing(@InjectScope A a) {
        throw new IllegalStateException("failed");
    }
}

@Prototype
class X {
    public final A a;
    public boolean failed;
    public final boolean aDestroyedInConstructor;

    X(@InjectScope A a, BeanProvider<Failing> failing) {
        this.a = a;
        try {
            failing.get();
        } catch (RuntimeException e) {
            failed = true;
        }
        this.aDestroyedInConstructor = a.destroyed;
    }
}
''')

        when:
        def x = getBean(context, 'injectscopefailed.X')
        def created = x.a.getClass().CREATED

        then:
        x.failed
        !x.aDestroyedInConstructor
        x.a.destroyed
        created.size() == 2
        created.every { it.destroyed }

        cleanup:
        context.close()
    }
}
