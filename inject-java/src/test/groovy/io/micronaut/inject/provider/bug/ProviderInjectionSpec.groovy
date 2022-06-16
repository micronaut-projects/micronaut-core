package io.micronaut.inject.provider.bug

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicInteger
import java.util.stream.Collectors
import java.util.stream.IntStream

class ProviderInjectionSpec extends Specification {
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void 'test proper provider lookup'() {
        when:
            Injectee bean = context.getBean(Injectee)
        then:
            bean.showWhatIsInjected() == "first 1, second 10, third null"
            bean.showWhatIsInjected() == "first 2, second 20, third null"
            bean.showWhatIsInjected() == "first 3, second 30, third null"
    }

    void 'test concurrency'() {
        when:
            Injectee bean = context.getBean(Injectee)
        then:
            ForkJoinPool.commonPool()
                    .invokeAll(
                            IntStream.range(0, 1000)
                                    .mapToObj(
                                            idx -> () -> {
                                                bean.showWhatIsInjected()
                                                return true
                                            })
                                    .collect(Collectors.toList()))
                    .parallelStream()
                    .allMatch { it.get() }
        when:
            def counter1 = context.getBean(AtomicInteger, Qualifiers.byStereotype(First))
            def counter2 = context.getBean(AtomicInteger, Qualifiers.byStereotype(Second))
            def counter3 = context.getBean(AtomicInteger, Qualifiers.byStereotype(Third))
        then:
            counter1.intValue() == 1000
            counter2.intValue() == 10000
            counter3.intValue() == 0
    }

    void 'test concurrency2'() {
        when:
            ForkJoinPool.commonPool()
                    .invokeAll(
                            IntStream.range(0, 1000)
                                    .mapToObj(
                                            idx -> () -> {
                                                def bean = context.getBean(Injectee)
                                                bean.showWhatIsInjected()
                                                return true
                                            })
                                    .collect(Collectors.toList()))
                    .parallelStream()
                    .allMatch { it.get() }
        then:
            noExceptionThrown()
        when:
            def counter1 = context.getBean(AtomicInteger, Qualifiers.byStereotype(First))
            def counter2 = context.getBean(AtomicInteger, Qualifiers.byStereotype(Second))
            def counter3 = context.getBean(AtomicInteger, Qualifiers.byStereotype(Third))
        then:
            counter1.intValue() == 1000
            counter2.intValue() == 10000
            counter3.intValue() == 0
    }

    void 'test concurrency3'() {
        when:
            Injectee bean1 = context.getBean(Injectee)
            ForkJoinPool.commonPool()
                    .invokeAll(
                            IntStream.range(0, 1000)
                                    .mapToObj(
                                            idx -> () -> {
                                                def bean2 = context.getBean(Injectee)
                                                assert bean1 == bean2
                                                bean2.showWhatIsInjected()
                                                return true
                                            })
                                    .collect(Collectors.toList()))
                    .parallelStream()
                    .allMatch { it.get() }
        then:
            noExceptionThrown()
        when:
            def counter1 = context.getBean(AtomicInteger, Qualifiers.byStereotype(First))
            def counter2 = context.getBean(AtomicInteger, Qualifiers.byStereotype(Second))
            def counter3 = context.getBean(AtomicInteger, Qualifiers.byStereotype(Third))
        then:
            counter1.intValue() == 1000
            counter2.intValue() == 10000
            counter3.intValue() == 0
    }
}


