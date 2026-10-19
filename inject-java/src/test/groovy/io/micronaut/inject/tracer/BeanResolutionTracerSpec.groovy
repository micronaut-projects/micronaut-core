package io.micronaut.inject.tracer

import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanResolutionTraceMode
import io.micronaut.context.BeanResolutionTracer
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class BeanResolutionTracerSpec extends Specification {

    void cleanup() {
        DiscoveredTracerProvider.CREATED.clear()
        DiscoveredTracerProvider.CREATED_BY.clear()
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
  inject CONSTRUCTOR TracedEngine.<init>(spark)
    create TracedSpark <- TracedEngine
  inject FIELD TracedEngine.fuel(fuel)
    create TracedFuel <- TracedEngine
create TracedFuel'''
        tracer.violations.isEmpty()

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
  inject CONSTRUCTOR TracedTyre.<init>(wheel)
    create TracedWheel <- TracedTyre
      create TracedWheelFactory <- TracedWheel
      inject FACTORY_METHOD TracedWheel.front(spark)
        create TracedSpark <- TracedWheel'''
        tracer.violations.isEmpty()

        cleanup:
        context.close()
    }

    void "beans created with createBean are traced, with the qualifiers of their injection points"() {
        given:
        def tracer = new RecordingTracer()
        def context = builder().beanResolutionTracer(tracer).start()

        when:
        context.createBean(TracedGarage)

        then:
        tracer.eventsAsText() == '''\
create TracedGarage
  inject CONSTRUCTOR TracedGarage.<init>(rear) @Named('rear')
    create TracedWheel <- TracedGarage
      create TracedWheelFactory <- TracedWheel
      inject FACTORY_METHOD TracedWheel.rear(spark)
        create TracedSpark <- TracedWheel'''
        tracer.violations.isEmpty()

        when: "a bean is created with arguments, which are resolved within its creation"
        def front = context.getBean(TracedWheel, Qualifiers.byName("front"))
        tracer.events.clear()
        context.createBean(TracedGarage, (Object) front)

        then:
        tracer.eventsAsText() == '''\
create TracedGarage
  inject CONSTRUCTOR TracedGarage.<init>(rear) @Named('rear')'''
        tracer.violations.isEmpty()

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
  inject CONSTRUCTOR TracedBroken.<init>(exploding)
    create TracedExploding <- TracedBroken
      inject CONSTRUCTOR TracedExploding.<init>(spark)
        create TracedSpark <- TracedExploding
    failed TracedExploding: BeanInstantiationException
failed TracedBroken: BeanInstantiationException'''
        tracer.violations.isEmpty()

        when: "the context goes on resolving beans"
        tracer.events.clear()
        context.getBean(TracedEngine)

        then: "the callbacks remain paired"
        tracer.events.first() == 'create TracedEngine'
        tracer.violations.isEmpty()

        cleanup:
        context.close()
    }

    void "a tracer failing to trace a failed creation does not hide the failure"() {
        given:
        def tracer = new RecordingTracer() {
            @Override
            void traceBeanCreationFailed(BeanResolutionTracer.Resolution resolution, BeanDefinition<?> beanDefinition, Throwable failure) {
                throw new IllegalStateException("Tracer failed")
            }
        }
        def context = builder().beanResolutionTracer(tracer).start()

        when:
        context.getBean(TracedBroken)

        then:
        def e = thrown(BeanInstantiationException)
        e.message.contains('Bad spark')
        e.suppressed*.message.contains('Tracer failed')

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
        first.violations.isEmpty()
        second.violations.isEmpty()

        cleanup:
        context.close()
    }

    void "a tracer discovered with ServiceLoader is only active when discovery is enabled or its provider enables it"() {
        when: "discovery is not enabled"
        def context = builder().start()

        then:
        DiscoveredTracerProvider.CREATED.isEmpty()
        context.customTracer == null

        when: "discovery is enabled"
        context.close()
        context = builder().beanResolutionTracerDiscovery(true).start()
        context.getBean(TracedEngine)

        then: "the tracers are created in the order of their providers"
        DiscoveredTracerProvider.CREATED_BY == ['FirstDiscoveredTracerProvider', 'DiscoveredTracerProvider']
        DiscoveredTracerProvider.CREATED.every { it.events.first() == 'create TracedEngine' && it.violations.isEmpty() }

        when: "the provider activates the tracer for an environment"
        context.close()
        cleanup()
        context = builder().environments(DiscoveredTracerProvider.ENVIRONMENT).start()
        context.getBean(TracedEngine)

        then:
        DiscoveredTracerProvider.CREATED.size() == 2
        DiscoveredTracerProvider.CREATED.first().events.first() == 'create TracedEngine'

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
        tracer.violations.isEmpty()

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
        tracer.violations.isEmpty()

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
