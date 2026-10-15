package io.micronaut.context

import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

/**
 * Rendering a resolution path must not be able to fail.
 *
 * The path is a LinkedList, and the renderers read it several times -- iterating it,
 * asking for its size, and asking for the index of an element. Reading the live list
 * meant that a modification part way through surfaced as a ConcurrentModificationException
 * thrown from inside the constructor of the exception being reported, so a
 * "Circular dependency detected" message and the path that explains it were replaced by
 * an unrelated error that named neither.
 *
 * The modification that matters is on the rendering thread: a path belongs to one
 * thread's resolution context, and a segment's toString() can resolve something that
 * pushes onto it part way through the render. This is not, and does not claim to be, a
 * guard against another thread mutating the path concurrently -- LinkedList.toArray()
 * walks its nodes without checking modCount, so a snapshot cannot make that safe.
 */
class BeanResolutionPathMessageSpec extends Specification {

    void "rendering a path that is modified during the render does not throw"() {
        given: "a segment whose own toString pushes onto the path, as a resolution can"
        def path = newPath(new DefaultBeanContext())
        def pushed = false
        def extra = segment(99)
        def mutating = Stub(BeanResolutionContext.Segment) {
            toString() >> {
                if (!pushed) {
                    pushed = true
                    path.push(extra)
                }
                "mutating-segment"
            }
        }
        path.push(segment(1))
        path.push(mutating)
        path.push(segment(2))

        when:
        def circular = path.toCircularString()
        def plain = path.toString()

        then: "the render completes instead of throwing from inside the exception being built"
        noExceptionThrown()
        circular != null
        plain != null
        pushed
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
