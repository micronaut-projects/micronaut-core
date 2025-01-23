package io.micronaut.context

import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.ThreadPropagatedContextElement
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Requires
import spock.lang.Specification
import spock.util.environment.Jvm

import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicInteger

@Requires({ Jvm.current.isJava21Compatible() })
class PropagatedContext2Spec extends Specification {

    void 'test PropagatedContext are correctly called for ExecutorServices io, virtual and blocking'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()
        ExecutorService io = applicationContext.getBean(ExecutorService, Qualifiers.byName("io"))
        ExecutorService virtual = applicationContext.getBean(ExecutorService, Qualifiers.byName("virtual"))
        ExecutorService blocking = applicationContext.getBean(ExecutorService, Qualifiers.byName("blocking"))

        and:
        TestPropagatedContext contextForIo = new TestPropagatedContext("io")
        TestPropagatedContext contextForVirtual = new TestPropagatedContext("virtual")
        TestPropagatedContext contextForBlocking = new TestPropagatedContext("blocking")

        when:
        println("---------")
        println("Running IO ExecutorService:")
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(contextForIo).propagate()) {
            io.submit {
                println("Executing IO Thread Service")
            }.get()
        }

        println("---------")
        println("Running Virtual ExecutorService:")
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(contextForVirtual).propagate()) {
            virtual.submit {
                println("Executing Virtual Thread Service")
            }.get()
        }

        println("---------")
        println("Running Blocking ExecutorService:")
        try (PropagatedContext.Scope ignored = PropagatedContext.getOrEmpty().plus(contextForBlocking).propagate()) {
            blocking.submit {
                println("Executing Blocking Thread Service")
            }.get()
        }

        then: "Should be called 1x on the propagate() method and 1x by the ExecutorServiceInstrumenter"
        contextForIo.state() == 1

        and: "Should be called 1x on the propagate() method and 1x by the ExecutorServiceInstrumenter"
        contextForVirtual.state() == 1

        and: "Should be called 1x on the propagate() method and 1x by the ExecutorServiceInstrumenter"
        contextForBlocking.state() == 1

        cleanup:
        applicationContext.stop()
    }

    class TestPropagatedContext implements ThreadPropagatedContextElement<Integer> {

        private final String name
        private AtomicInteger counter = new AtomicInteger(0)

        TestPropagatedContext(String name) {
            this.name = name
        }

        Integer updateThreadContext() {
            int value = counter.incrementAndGet();
            println("Updating thread context for $name: $value")
            return value;
        }

        void restoreThreadContext(Integer oldState) {
            println("Restoring thread context for $name: $oldState")
        }

        Integer state() {
            return this.counter.get()
        }
    }

}
