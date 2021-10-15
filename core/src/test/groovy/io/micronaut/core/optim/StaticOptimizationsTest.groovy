package io.micronaut.core.optim

import spock.lang.Specification

class StaticOptimizationsTest extends Specification {
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

    static class TestOptimizations {
    }

    static class TestOptimizations2 {
    }
}
