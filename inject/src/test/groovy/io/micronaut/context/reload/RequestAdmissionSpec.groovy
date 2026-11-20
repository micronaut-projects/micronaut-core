package io.micronaut.context.reload

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class RequestAdmissionSpec extends Specification {

    void cleanup() {
        def current = RequestAdmission.current()
        if (current != null) {
            RequestAdmissionHolder.uninstall(current)
        }
    }

    void "there is no admission outside development mode"() {
        expect:
        RequestAdmission.current() == null
    }

    void "the launcher that installs its admission first owns it until it uninstalls it"() {
        given:
        def first = new TestAdmission()
        def second = new TestAdmission()

        expect:
        RequestAdmissionHolder.install(first)
        RequestAdmission.current().is(first)
        !RequestAdmissionHolder.install(second)
        RequestAdmission.current().is(first)

        when: "another launcher cannot remove it"
        RequestAdmissionHolder.uninstall(second)

        then:
        RequestAdmission.current().is(first)

        when:
        RequestAdmissionHolder.uninstall(first)

        then:
        RequestAdmission.current() == null
        RequestAdmissionHolder.install(second)
        RequestAdmission.current().is(second)
    }

    void "a request is admitted at once when no batch is in progress"() {
        given:
        def admission = new TestAdmission()

        expect:
        admission.isAdmitted()
        admission.awaitAdmission(Duration.ZERO)
        admission.whenAdmitted().toCompletableFuture().isDone()
    }

    void "a blocking server waits for admission, at most for the given time"() {
        given:
        def admission = new TestAdmission()
        def batch = admission.batch()

        expect:
        !admission.isAdmitted()
        !admission.awaitAdmission(Duration.ofMillis(50))

        when:
        def waiting = CompletableFuture.supplyAsync { admission.awaitAdmission(Duration.ofSeconds(30)) }
        Thread.sleep(50)

        then:
        !waiting.isDone()

        when:
        batch.complete(null)

        then:
        waiting.get()
        admission.isAdmitted()
    }

    void "an asynchronous server holds a request on the stage, which it cannot complete itself"() {
        given:
        def admission = new TestAdmission()
        def batch = admission.batch()
        def held = admission.whenAdmitted()
        def proceeded = new CompletableFuture<Boolean>()
        held.thenRun { proceeded.complete(true) }

        when: "a caller completes what it was handed"
        held.toCompletableFuture().complete(null)

        then: "the batch still holds requests"
        !admission.isAdmitted()
        !proceeded.isDone()

        when:
        batch.complete(null)

        then:
        proceeded.get()
    }

    void "the timeouts are the launcher's"() {
        given:
        def admission = new TestAdmission()

        expect:
        admission.holdTimeout() == Duration.ofSeconds(7)
        admission.drainTimeout() == Duration.ofSeconds(3)
    }

    /**
     * An admission as a launcher provides it: one future per batch.
     */
    static final class TestAdmission implements RequestAdmission {
        volatile CompletableFuture<Void> admitted = CompletableFuture.completedFuture(null)

        CompletableFuture<Void> batch() {
            admitted = new CompletableFuture<>()
            return admitted
        }

        @Override
        boolean isAdmitted() {
            return admitted.isDone()
        }

        @Override
        CompletionStage<Void> whenAdmitted() {
            return admitted.minimalCompletionStage()
        }

        @Override
        Duration holdTimeout() {
            return Duration.ofSeconds(7)
        }

        @Override
        Duration drainTimeout() {
            return Duration.ofSeconds(3)
        }
    }
}
