package io.micronaut.health

import io.micronaut.context.ApplicationContext
import io.micronaut.context.condition.ConditionContext
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

import static java.lang.Boolean.FALSE

class HeartbeatDiscoveryClientConditionSpec extends Specification {

    HeartbeatDiscoveryClientCondition heartbeatDiscoveryClientCondition = new HeartbeatDiscoveryClientCondition()

    def "matches should return true when micronaut.heartbeat.enabled is not defined"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)
        ApplicationContext  beanContext = Mock(ApplicationContext)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        matches
        1 * conditionContext.getBeanContext() >> beanContext
        1 * beanContext.getProperty(HeartbeatConfiguration.ENABLED, Boolean.class) >> Optional.empty()
    }

    def "matches should return false when micronaut.heartbeat.enabled is defined as false and there are no discovery clients"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)
        ApplicationContext  beanContext = Mock(ApplicationContext)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        !matches
        1 * conditionContext.getBeanContext() >> beanContext
        1 * beanContext.getProperty(HeartbeatConfiguration.ENABLED, Boolean.class) >> Optional.of(FALSE)
        1 * beanContext.getBeanDefinitions(DiscoveryClient.class) >> []
    }

    def "matches should return true when micronaut.heartbeat.enabled is defined as false and there are discovery clients"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)
        ApplicationContext  beanContext = Mock(ApplicationContext)
        def beanDefinition = Mock(BeanDefinition)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        matches
        1 * conditionContext.getBeanContext() >> beanContext
        1 * beanContext.getProperty(HeartbeatConfiguration.ENABLED, Boolean.class) >> Optional.of(FALSE)
        1 * beanDefinition.getBeanType() >> DiscoveryClient.class
        1 * beanContext.getBeanDefinitions(DiscoveryClient.class) >> [beanDefinition]
    }
}
