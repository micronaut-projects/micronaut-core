package io.micronaut.core.optim

import spock.lang.Specification

class StaticOptimizationsTest extends Specification {
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

    static class TestOptimizations {
    }

    static class TestOptimizations2 {
    }

    static class TestOptimizations3 {
    }

    static class TestOptimizationsLoader implements StaticOptimizations.Loader<TestOptimizations3> {
        @Override
        TestOptimizations3 load() {
            new TestOptimizations3()
        }
    }
}
