package io.micronaut.context

import io.micronaut.core.type.Argument
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class BeanConcreteCandidateCacheSpec extends Specification {

    def "repeated lookups with equal closest type argument qualifiers reuse one cache entry"() {
        given:
            DefaultBeanContext beanContext = (DefaultBeanContext) BeanContext.run()

        when:
            100.times {
                beanContext.findBeanDefinition(Comparator, Qualifiers.byTypeArgumentsClosest(IllegalStateException, Object))
                beanContext.findBeanDefinition(Comparator, Qualifiers.byTypeArgumentsClosest(IllegalArgumentException, Object))
            }

        then:
            beanContext.@beanConcreteCandidateCache.keySet().count { it.@beanType.type == Comparator } == 2

        cleanup:
            beanContext.close()
    }

    def "candidate keys that differ by qualifier do not share a hash code"() {
        given:
            def stateKey = new DefaultBeanContext.BeanKey(Argument.of(Comparator), Qualifiers.byTypeArgumentsClosest(IllegalStateException, Object))
            def argumentKey = new DefaultBeanContext.BeanKey(Argument.of(Comparator), Qualifiers.byTypeArgumentsClosest(IllegalArgumentException, Object))
            def sameStateKey = new DefaultBeanContext.BeanKey(Argument.of(Comparator), Qualifiers.byTypeArgumentsClosest(IllegalStateException, Object))

        expect:
            stateKey.hashCode() != argumentKey.hashCode()
            stateKey == sameStateKey
            stateKey.hashCode() == sameStateKey.hashCode()
    }
}
