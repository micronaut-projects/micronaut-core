package io.micronaut.inject.visitor.util

import spock.lang.Specification

import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.TypeElement

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * When the properties are restored for a compilation that never has its final round, which javac does
 * when it cannot read a class generated in a round.
 */
class ProcessorOptionsHoldersSpec extends Specification {

    static final String OPTION = 'micronaut.test.processorOptionsHolders'

    def cleanup() {
        System.clearProperty(OPTION)
    }

    void 'a compilation without a final round no longer holds the properties once another compilation starts on its thread'() {
        given:
        def first = new Object()
        def firstProcessors = [new Object(), new Object()]
        ProcessorOptionsSystemProperties.apply(first, [(OPTION): 'first'])
        firstProcessors.each { processor -> ProcessorOptionsSystemProperties.process(first, processor, false, { false }) }

        expect:
        System.getProperty(OPTION) == 'first'

        when: 'the next compilation starts on the same thread'
        def second = new Object()
        def secondProcessor = new Object()
        ProcessorOptionsSystemProperties.apply(second, [:])

        then:
        System.getProperty(OPTION) == null

        when:
        ProcessorOptionsSystemProperties.process(second, secondProcessor, false, { false })
        ProcessorOptionsSystemProperties.process(second, secondProcessor, true, { false })

        then:
        System.getProperty(OPTION) == null
    }

    void 'a compilation running on another thread keeps holding the properties'() {
        given:
        def first = new Object()
        def firstProcessor = new Object()
        def inFirstRound = new CountDownLatch(1)
        def finishFirst = new CountDownLatch(1)
        def thread = Thread.start {
            ProcessorOptionsSystemProperties.apply(first, [(OPTION): 'first'])
            ProcessorOptionsSystemProperties.process(first, firstProcessor, false, { false })
            inFirstRound.countDown()
            finishFirst.await(10, TimeUnit.SECONDS)
            ProcessorOptionsSystemProperties.process(first, firstProcessor, true, { false })
        }
        inFirstRound.await(10, TimeUnit.SECONDS)

        when: 'a compilation starts and ends on this thread between two rounds of the other one'
        def second = new Object()
        def secondProcessor = new Object()
        ProcessorOptionsSystemProperties.apply(second, [:])
        ProcessorOptionsSystemProperties.process(second, secondProcessor, true, { false })

        then:
        System.getProperty(OPTION) == 'first'

        when:
        finishFirst.countDown()
        thread.join(10_000)

        then:
        System.getProperty(OPTION) == null
    }

    void 'a compilation started from the round of another processor does not end the compilation of the round'() {
        given: 'a compilation whose processors are done with their round'
        def outer = new Object()
        def outerProcessors = [new Object(), new Object()]
        ProcessorOptionsSystemProperties.apply(outer, [(OPTION): 'outer'])
        outerProcessors.each { processor -> ProcessorOptionsSystemProperties.process(outer, processor, false, { false }) }
        String duringInner = null
        String afterInner = null

        when: 'another processor of the round compiles on the same thread'
        new RoundProcessor({
            def inner = new Object()
            def innerProcessor = new Object()
            ProcessorOptionsSystemProperties.apply(inner, [:])
            ProcessorOptionsSystemProperties.process(inner, innerProcessor, false, {
                duringInner = System.getProperty(OPTION)
                false
            })
            ProcessorOptionsSystemProperties.process(inner, innerProcessor, true, { false })
            afterInner = System.getProperty(OPTION)
        }).process([] as Set, null)

        then:
        duringInner == 'outer'
        afterInner == 'outer'
        System.getProperty(OPTION) == 'outer'

        when:
        ProcessorOptionsSystemProperties.process(outer, outerProcessors[0], true, { false })

        then: 'the other processor of the outer compilation still holds the properties'
        System.getProperty(OPTION) == 'outer'

        when:
        ProcessorOptionsSystemProperties.process(outer, outerProcessors[1], true, { false })

        then:
        System.getProperty(OPTION) == null
    }

    /**
     * An annotation processor running a closure as its round.
     */
    static class RoundProcessor extends AbstractProcessor {

        private final Runnable round

        RoundProcessor(Runnable round) {
            this.round = round
        }

        @Override
        boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            round.run()
            return false
        }
    }
}
