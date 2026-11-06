package io.micronaut.aop.beandefinition

import io.micronaut.core.reflect.exception.InvocationException
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException

/**
 * A bean definition compiled before 5.2.1 dispatches a private lifecycle callback through
 * {@code ReflectionUtils.invokeMethod}, which wraps what the callback threw.
 */
class LifecycleCallbacksSpec extends Specification {

    void 'test the checked exception of a callback wrapped by an older definition is unwrapped'() {
        given:
        IOException failure = new IOException('init')
        ExecutableMethod<Object, Object> callback = Stub {
            invoke(_, _) >> { throw new InvocationException('wrapped', new InvocationTargetException(failure)) }
        }

        when:
        LifecycleCallbacks.invoke(callback, new Object(), new Object[0])

        then:
        IOException e = thrown()
        e.is(failure)
    }

    void 'test an invocation exception without a target is rethrown'() {
        given:
        InvocationException failure = new InvocationException('access', new IllegalAccessException('access'))
        ExecutableMethod<Object, Object> callback = Stub {
            invoke(_, _) >> { throw failure }
        }

        when:
        LifecycleCallbacks.invoke(callback, new Object(), new Object[0])

        then:
        InvocationException e = thrown()
        e.is(failure)
    }
}
