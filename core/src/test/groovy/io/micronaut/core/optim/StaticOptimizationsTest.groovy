package io.micronaut.core.optim

import io.micronaut.core.io.service.ServiceIndex
import io.micronaut.core.io.service.ServiceIndexTest
import io.micronaut.core.io.service.SoftServiceLoader
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StaticOptimizationsTest extends Specification {
    static final String IMAGE_CODE = "org.graalvm.nativeimage.imagecode"

    def setup() {
        StaticOptimizations.reset()
    }

    void "environment isn't cached by default"() {
        expect:
        !StaticOptimizations.environmentCached
    }

    void "can set a optimization data"() {
        def testOptimizations = new TestOptimizations()

        when:
        def opt = StaticOptimizations.get(TestOptimizations)

        then:
        !opt.present
        StaticOptimizations.reset()

        when:
        StaticOptimizations.set(testOptimizations)
        opt = StaticOptimizations.get(TestOptimizations)

        then:
        opt.present
        opt.get().is(testOptimizations)
    }

    void "differentiates between optimization classes"() {
        when:
        StaticOptimizations.set(new TestOptimizations())

        then:
        StaticOptimizations.get(TestOptimizations).present
        !StaticOptimizations.get(TestOptimizations2).present
    }

    def "writing optimizations after reading is disallowed"() {
        def testOptimizations = new TestOptimizations()

        when:
        def opt = StaticOptimizations.get(TestOptimizations)

        then:
        !opt.present

        when:
        StaticOptimizations.set(testOptimizations)

        then:
        RuntimeException ex = thrown()
        ex.message.contains("Optimization state for class io.micronaut.core.optim.StaticOptimizationsTest\$TestOptimizations was read before it was set.")

    }

    def "optimizations are loaded via service loading"() {
        when:
        def opt = StaticOptimizations.get(TestOptimizations3)

        then:
        opt.present
    }

    def "setting an optimization again replaces it"() {
        def second = new TestOptimizations()

        when:
        StaticOptimizations.set(new TestOptimizations())
        StaticOptimizations.set(second)

        then:
        StaticOptimizations.get(TestOptimizations).get().is(second)
    }

    def "a set-once optimization is found by name, and that read does not prevent setting it"() {
        def optimization = new TestSetOnce()

        expect: "a read that comes before the value is set finds nothing"
        StaticOptimizations.findSetOnce(TestSetOnce.name) == null

        when:
        StaticOptimizations.set(optimization)

        then:
        noExceptionThrown()
        StaticOptimizations.findSetOnce(TestSetOnce.name).is(optimization)
        StaticOptimizations.get(TestSetOnce).get().is(optimization)

        when:
        StaticOptimizations.set(new TestSetOnce())

        then:
        IllegalStateException ex = thrown()
        ex.message == "An optimization of class io.micronaut.core.optim.StaticOptimizationsTest\$TestSetOnce was already set: it can only be set once"
        StaticOptimizations.findSetOnce(TestSetOnce.name).is(optimization)
    }

    def "only a set-once optimization is found by name"() {
        when:
        StaticOptimizations.set(new TestOptimizations())

        then:
        StaticOptimizations.findSetOnce(TestOptimizations.name) == null
    }

    def "a JVM-only optimization is not stored in #imageCode native image code"() {
        given:
        def previous = System.getProperty(IMAGE_CODE)

        when:
        System.setProperty(IMAGE_CODE, imageCode)
        StaticOptimizations.set(new TestJvmOnly())
        StaticOptimizations.set(new TestOptimizations())

        then: "the JVM-only value is dropped, and any other value is stored"
        !StaticOptimizations.@OPTIMIZATIONS.containsKey(TestJvmOnly)
        StaticOptimizations.@OPTIMIZATIONS.containsKey(TestOptimizations)

        when:
        System.clearProperty(IMAGE_CODE)
        StaticOptimizations.set(new TestJvmOnly())

        then:
        StaticOptimizations.@OPTIMIZATIONS.containsKey(TestJvmOnly)

        cleanup:
        previous == null ? System.clearProperty(IMAGE_CODE) : System.setProperty(IMAGE_CODE, previous)

        where:
        imageCode << ["buildtime", "runtime"]
    }

    def "a service index is not stored in native image code"() {
        given: "the loaders run as they do when a native image is built"
        def previous = System.getProperty(IMAGE_CODE)
        System.setProperty(IMAGE_CODE, "buildtime")

        when:
        StaticOptimizations.reset()

        then: "the index that the core tests register through a loader is dropped, and the other optimizations are not"
        StaticOptimizations.findSetOnce(ServiceIndex.name) == null
        !StaticOptimizations.@OPTIMIZATIONS.containsKey(ServiceIndex)
        StaticOptimizations.@OPTIMIZATIONS.containsKey(TestOptimizations3)

        cleanup:
        previous == null ? System.clearProperty(IMAGE_CODE) : System.setProperty(IMAGE_CODE, previous)
        StaticOptimizations.reset()
    }

    def "a second service index is rejected"() {
        given: "the index that the core tests register through a loader"
        def registered = StaticOptimizations.findSetOnce(ServiceIndex.name)

        when:
        StaticOptimizations.set(new ServiceIndex(getClass().classLoader, [:], [:]))

        then:
        IllegalStateException ex = thrown()
        ex.message == "An optimization of class io.micronaut.core.io.service.ServiceIndex was already set: it can only be set once"
        registered.is(ServiceIndexTest.TestServiceIndexLoader.INDEX)
        StaticOptimizations.findSetOnce(ServiceIndex.name).is(registered)
        StaticOptimizations.get(ServiceIndex).get().is(registered)
    }

    def "a loader that looks a service up does not prevent the registration of the service index"() {
        given:
        def classLoader = ServiceIndexTest.TestServiceIndexLoader.CLASS_LOADER

        when: "the loaders run again, and one of them looks a service up before the loader of the index runs"
        ServiceIndexTest.LookingUpLoader.lookUp = true
        StaticOptimizations.reset()

        then: "that lookup scanned the class path, which has no such service, as no index was registered yet"
        noExceptionThrown()
        ServiceIndexTest.LookingUpLoader.found == []

        and: "the index is registered, and the lookups that follow are served from it"
        StaticOptimizations.findSetOnce(ServiceIndex.name).is(ServiceIndexTest.TestServiceIndexLoader.INDEX)
        SoftServiceLoader.load(ServiceIndexTest.Greeter, classLoader).collectAll()*.getClass() == [ServiceIndexTest.Hello, ServiceIndexTest.Hi, ServiceIndexTest.Hey]

        cleanup:
        ServiceIndexTest.LookingUpLoader.lookUp = false
        ServiceIndexTest.LookingUpLoader.found = null
    }

    def "of service indexes set concurrently exactly one is set"() {
        given:
        int threads = 4
        def classLoader = new URLClassLoader(new URL[0], getClass().classLoader)
        def pool = Executors.newFixedThreadPool(threads)

        when: "in each round, several indexes are set at the same time while no index is set"
        def rounds = (1..1000).collect {
            StaticOptimizations.@OPTIMIZATIONS.remove(ServiceIndex)
            StaticOptimizations.@SET_ONCE.remove(ServiceIndex.name)
            def indexes = (1..threads).collect { new ServiceIndex(classLoader, [:], [:]) }
            def barrier = new CyclicBarrier(threads)
            def winners = indexes.collect { index ->
                pool.submit({
                    barrier.await(30, TimeUnit.SECONDS)
                    try {
                        StaticOptimizations.set(index)
                        return index
                    } catch (IllegalStateException ignored) {
                        return null
                    }
                } as Callable<ServiceIndex>)
            }*.get(60, TimeUnit.SECONDS).findAll()
            [winners: winners, registered: StaticOptimizations.findSetOnce(ServiceIndex.name), stored: StaticOptimizations.@OPTIMIZATIONS.get(ServiceIndex)]
        }

        then: "one set succeeds and the others fail, every time"
        rounds.collect { it.winners.size() } == [1] * rounds.size()
        rounds.every { it.registered.is(it.winners[0]) && it.stored.is(it.winners[0]) }

        cleanup:
        pool.shutdownNow()
        classLoader.close()
        StaticOptimizations.reset()
    }

    static class TestOptimizations {
    }

    static class TestOptimizations2 {
    }

    static class TestOptimizations3 {
    }

    static class TestSetOnce implements StaticOptimizations.SetOnce {
    }

    static class TestJvmOnly implements StaticOptimizations.JvmOnly {
    }

    static class TestOptimizationsLoader implements StaticOptimizations.Loader<TestOptimizations3> {
        @Override
        TestOptimizations3 load() {
            new TestOptimizations3()
        }
    }
}
