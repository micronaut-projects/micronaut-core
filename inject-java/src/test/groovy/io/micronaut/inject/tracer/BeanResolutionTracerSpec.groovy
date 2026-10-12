package io.micronaut.inject.tracer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionTraceMode
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class BeanResolutionTracerSpec extends Specification {

    void cleanup() {
        DiscoveredTracer.ACTIVATED.clear()
    }

    void "a registered tracer receives paired creation and injection callbacks for constructor, field and prototype beans"() {
        given:
        def tracer = new RecordingTracer()
        def context = builder().beanResolutionTracer(tracer).start()

        when:
        context.getBean(TracedEngine)
        context.getBean(TracedFuel)

        then:
        tracer.eventsAsText() == '''\
create TracedEngine
  inject TracedEngine.io.micronaut.inject.tracer.TracedEngine(tracedEngine)
    inject TracedEngine.io.micronaut.inject.tracer.TracedEngine(spark)
      create TracedSpark <- TracedEngine
    inject TracedEngine.fuel(fuel)
      create TracedFuel <- TracedEngine
create TracedFuel
  inject TracedFuel.io.micronaut.inject.tracer.TracedFuel(tracedFuel)'''
        tracer.unpaired.isEmpty()

        cleanup:
        context.close()
    }

    void "a registered tracer receives the creation of factory and each beans nested in the bean they are created for"() {
        given:
        def tracer = new RecordingTracer()
        def context = builder().beanResolutionTracer(tracer).start()

        when:
        context.getBean(TracedTyre, Qualifiers.byName("front"))

        then:
        tracer.eventsAsText() == '''\
create TracedTyre
  inject TracedTyre.io.micronaut.inject.tracer.TracedTyre(tracedTyre)
    inject TracedTyre.io.micronaut.inject.tracer.TracedTyre(wheel)
      create TracedWheel <- TracedTyre
        create TracedWheelFactory <- TracedWheel
        inject TracedWheel.front(spark)
          create TracedSpark <- TracedWheel'''
        tracer.unpaired.isEmpty()

        cleanup:
        context.close()
    }

    void "a failed creation is reported for the failing bean and each bean it fails, after their injection points completed"() {
        given:
        def tracer = new RecordingTracer()
        def context = builder().beanResolutionTracer(tracer).start()

        when:
        context.getBean(TracedBroken)

        then:
        thrown(BeanInstantiationException)
        tracer.eventsAsText() == '''\
create TracedBroken
  inject TracedBroken.io.micronaut.inject.tracer.TracedBroken(tracedBroken)
    inject TracedBroken.io.micronaut.inject.tracer.TracedBroken(exploding)
      create TracedExploding <- TracedBroken
        inject TracedExploding.io.micronaut.inject.tracer.TracedExploding(spark)
          create TracedSpark <- TracedExploding
      failed TracedExploding: BeanInstantiationException
failed TracedBroken: BeanInstantiationException'''
        tracer.unpaired.isEmpty()

        when: "the context goes on resolving beans"
        tracer.events.clear()
        context.getBean(TracedEngine)

        then: "the callbacks remain paired"
        tracer.events.first() == 'create TracedEngine'
        tracer.unpaired.isEmpty()

        cleanup:
        context.close()
    }

    void "several registered tracers receive every callback"() {
        given:
        def first = new RecordingTracer()
        def second = new RecordingTracer()
        def context = builder().beanResolutionTracer(first).beanResolutionTracer(second).start()

        when:
        context.getBean(TracedEngine)

        then:
        !first.events.isEmpty()
        first.events == second.events
        first.unpaired.isEmpty()
        second.unpaired.isEmpty()

        cleanup:
        context.close()
    }

    void "a tracer discovered with ServiceLoader is only active when discovery is enabled or it enables itself"() {
        when: "discovery is not enabled"
        def context = builder().start()

        then:
        DiscoveredTracer.ACTIVATED.isEmpty()
        context.customTracer == null

        when: "discovery is enabled"
        context.close()
        context = builder().beanResolutionTracerDiscovery(true).start()
        context.getBean(TracedEngine)

        then:
        DiscoveredTracer.ACTIVATED.size() == 1
        DiscoveredTracer.ACTIVATED.first().events.first() == 'create TracedEngine'
        DiscoveredTracer.ACTIVATED.first().unpaired.isEmpty()

        when: "the tracer activates itself for an environment"
        context.close()
        DiscoveredTracer.ACTIVATED.clear()
        context = builder().environments(DiscoveredTracer.ENVIRONMENT).start()
        context.getBean(TracedEngine)

        then:
        DiscoveredTracer.ACTIVATED.size() == 1
        DiscoveredTracer.ACTIVATED.first().events.first() == 'create TracedEngine'

        cleanup:
        context.close()
    }

    void "registered tracers compose with the standard out trace mode, whose class patterns only restrict its own tracer"() {
        given:
        def tracer = new RecordingTracer()
        def out = new ByteArrayOutputStream()
        def originalOut = System.out
        System.setOut(new PrintStream(out, true))
        def context = builder()
                .beanResolutionTrace(BeanResolutionTraceMode.STANDARD_OUT, '.+TracedEngine')
                .beanResolutionTracer(tracer)
                .start()

        when:
        context.getBean(TracedEngine)
        context.getBean(TracedTyre, Qualifiers.byName("rear"))
        def output = out.toString().replaceAll(/\u001B\[[;\d]*m/, "")

        then: "the mode traces the matching bean"
        output =~ /Created .*TracedEngine in \d+ms/
        !output.contains('TracedTyre')

        and: "the registered tracer traces every bean"
        tracer.events.contains('create TracedEngine')
        tracer.events.contains('create TracedTyre')
        tracer.unpaired.isEmpty()

        cleanup:
        System.setOut(originalOut)
        context.close()
    }

    void "registered tracers compose with the log trace mode"() {
        given:
        def tracer = new RecordingTracer()
        def context = builder()
                .beanResolutionTrace(BeanResolutionTraceMode.LOG)
                .beanResolutionTracer(tracer)
                .start()

        when:
        context.getBean(TracedEngine)

        then:
        context.combinedTracer.getClass().simpleName == 'CompositeBeanResolutionTracer'
        context.customTracer.is(tracer)
        tracer.events.first() == 'create TracedEngine'
        tracer.unpaired.isEmpty()

        cleanup:
        context.close()
    }

    void "without a tracer no tracer is installed on the resolution path"() {
        given:
        def context = builder().beanResolutionTrace(BeanResolutionTraceMode.NONE).start()

        expect:
        context.customTracer == null
        context.combinedTracer == null
        context.newResolutionContext(context.getBeanDefinition(TracedEngine), null).tracer == null
        context.getBean(TracedEngine).fuel != null

        cleanup:
        context.close()
    }

    private static ApplicationContextBuilder builder() {
        ApplicationContext.builder().properties("spec.name": "BeanResolutionTracerSpec")
    }
}
