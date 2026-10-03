package io.micronaut.inject.lifecycle.registrationclose

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class DestructionFailurePolicySpec extends AbstractTypeElementSpec {
    private final String source = '''
package destruction.policy;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.context.event.*;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.*;

class Events { static final List<String> calls = new ArrayList<>(); }
@Prototype class Good {
    @PreDestroy void dispose() { Events.calls.add("good"); }
}
@Prototype class Bad {
    static final AssertionError FAILURE = new AssertionError("dependent failure");
    @PreDestroy void dispose() { Events.calls.add("bad"); BAD_FAILURE }
}
@Prototype class Subject {
    static final AssertionError FAILURE = new AssertionError("owner failure");
    Subject(Good good, Bad bad) {}
    @PreDestroy void dispose() { Events.calls.add("owner"); OWNER_FAILURE }
}
@Singleton class Destroyed implements BeanDestroyedEventListener<Subject> {
    public void onDestroyed(BeanDestroyedEvent<Subject> event) { Events.calls.add("destroyed"); }
}
'''

    void 'cleanup errors are suppressed on the original destruction failure and remaining dependents are released'() {
        given:
        def context = buildContext(source.replace('BAD_FAILURE', 'throw FAILURE;').replace('OWNER_FAILURE', 'throw FAILURE;'))
        def subject = context.classLoader.loadClass('destruction.policy.Subject')
        def bad = context.classLoader.loadClass('destruction.policy.Bad')
        def events = context.classLoader.loadClass('destruction.policy.Events')
        def registration = context.getBeanRegistration(subject, null)

        when:
        context.destroyBean(registration)

        then:
        def failure = thrown(AssertionError)
        failure.is(subject.FAILURE)
        failure.suppressed.toList() == [bad.FAILURE]
        events.calls == ['owner', 'bad', 'good']

        when:
        registration.close()
        context.destroyBean(registration)

        then:
        events.calls == ['owner', 'bad', 'good']

        cleanup:
        context.close()
    }

    void 'logged disposal exceptions preserve cleanup and destroyed notifications'() {
        given:
        def context = buildContext(source.replace('BAD_FAILURE', '').replace('OWNER_FAILURE', 'throw new IllegalStateException("logged");'))
        def events = context.classLoader.loadClass('destruction.policy.Events')
        def registration = context.getBeanRegistration(context.classLoader.loadClass('destruction.policy.Subject'), null)

        when:
        context.destroyBean(registration)

        then:
        noExceptionThrown()
        events.calls == ['owner', 'bad', 'good', 'destroyed']

        cleanup:
        context.close()
    }
}
