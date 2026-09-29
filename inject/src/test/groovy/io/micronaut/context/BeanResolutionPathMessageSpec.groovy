package io.micronaut.context

import io.micronaut.core.annotation.Nullable
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Rendering a resolution path must not be able to fail.
 *
 * The path is a LinkedList, and the renderers read it several times -- iterating it,
 * asking for its size, and asking for the index of an element. Reading the live list
 * meant that a modification part way through surfaced as a ConcurrentModificationException
 * thrown from inside the constructor of the exception being reported, so a
 * "Circular dependency detected" message and the path that explains it were replaced by
 * an unrelated error that named neither.
 */
class BeanResolutionPathMessageSpec extends Specification {

    void "rendering a path while it is modified does not throw"() {
        given:
        def context = new DefaultBeanContext()
        def path = newPath(context)
        20.times { path.push(segment(it)) }

        when: "one thread renders the path while another keeps changing it"
        def failure = new AtomicReference<Throwable>()
        def rendered = new AtomicReference<String>()
        def start = new CountDownLatch(1)
        def pool = Executors.newFixedThreadPool(2)
        pool.submit {
            start.await()
            try {
                500.times { rendered.set(path.toCircularString()); path.toString() }
            } catch (Throwable t) {
                failure.set(t)
            }
        }
        pool.submit {
            start.await()
            try {
                500.times { path.push(segment(1000 + it)); path.pop() }
            } catch (Throwable ignored) {
                // the mutating side is not what is under test
            }
        }
        start.countDown()
        pool.shutdown()
        pool.awaitTermination(30, TimeUnit.SECONDS)

        then: "no ConcurrentModificationException escapes, and a path was still rendered"
        failure.get() == null
        rendered.get() != null
    }

    void "an empty path renders without failing"() {
        given:
        def path = newPath(new DefaultBeanContext())

        expect: "no exception from asking an empty path for its circular form"
        path.toCircularString() != null
    }

    private def newPath(BeanContext context) {
        // A resolution context only to reach its Path; no bean is resolved here.
        new DefaultBeanResolutionContext(context, Stub(BeanDefinition)).path
    }

    private def segment(int index) {
        def definition = Stub(BeanDefinition) {
            getBeanType() >> String
            toString() >> "definition-${index}"
        }
        new AbstractBeanResolutionContext.ConstructorArgumentSegment(
                definition,
                (io.micronaut.context.Qualifier<Object>) null,
                "<init>",
                Argument.of(String, "arg${index}"),
                [Argument.of(String, "arg${index}")] as Argument[]
        )
    }
}
