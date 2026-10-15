package io.micronaut.inject.lifecycle.registrationclose

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class ConcurrentRegistrationDestructionSpec extends AbstractTypeElementSpec {
    void 'concurrent and reentrant destruction share the claim made before callbacks start'() {
        given:
        def context = buildContext('''
package destruction.concurrent;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;
import java.util.concurrent.*;

class Events { static final List<String> calls = new CopyOnWriteArrayList<>(); }
@Prototype class Subject {
    @PreDestroy void dispose() { Events.calls.add("dispose"); }
}
@Singleton class Callbacks implements BeanPreDestroyEventListener<Subject>, BeanDestroyedEventListener<Subject> {
    final BeanContext context;
    final CountDownLatch entered = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);
    BeanRegistration<Subject> registration;
    Callbacks(BeanContext context) { this.context = context; }
    public Subject onPreDestroy(BeanPreDestroyEvent<Subject> event) {
        Events.calls.add("before");
        registration.close();
        context.destroyBean(registration);
        entered.countDown();
        try {
            if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("callback not released"); }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        return event.getBean();
    }
    public void onDestroyed(BeanDestroyedEvent<Subject> event) { Events.calls.add("after"); }
}
''')
        def callbacks = context.getBean(context.classLoader.loadClass('destruction.concurrent.Callbacks'))
        def events = context.classLoader.loadClass('destruction.concurrent.Events')
        def registration = context.getBeanRegistration(context.classLoader.loadClass('destruction.concurrent.Subject'), null)
        callbacks.registration = registration

        when:
        def first = CompletableFuture.runAsync { context.destroyBean(registration) }
        assert callbacks.entered.await(10, TimeUnit.SECONDS)
        CompletableFuture.runAsync { context.destroyBean(registration) }.get(10, TimeUnit.SECONDS)

        then:
        events.calls == ['before']

        when:
        callbacks.release.countDown()
        first.get(10, TimeUnit.SECONDS)

        then:
        events.calls == ['before', 'dispose', 'after']

        cleanup:
        callbacks?.release?.countDown()
        first?.get(10, TimeUnit.SECONDS)
        context.close()
    }
}
