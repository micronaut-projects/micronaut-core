package io.micronaut.health

import io.micronaut.context.ApplicationContext
import io.micronaut.context.condition.ConditionContext
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.discovery.CompositeDiscoveryClient
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
        !matches
        1 * conditionContext.getBeanContext() >> beanContext
        1 * beanContext.getProperty(HeartbeatConfiguration.ENABLED, ArgumentConversionContext.BOOLEAN) >> Optional.empty()
        1 * beanContext.getBean(CompositeDiscoveryClient.class) >> new CompositeDiscoveryClient() {}
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
        1 * beanContext.getProperty(HeartbeatConfiguration.ENABLED, ArgumentConversionContext.BOOLEAN) >> Optional.of(FALSE)
        1 * beanContext.getBean(CompositeDiscoveryClient.class) >> new CompositeDiscoveryClient() {}
    }

    def "matches should return true when micronaut.heartbeat.enabled is defined as false and there are discovery clients"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)
        ApplicationContext  beanContext = Mock(ApplicationContext)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        matches
        1 * conditionContext.getBeanContext() >> beanContext
        1 * beanContext.getBean(CompositeDiscoveryClient.class) >> new CompositeDiscoveryClient(Mock(DiscoveryClient)) {
        }
    }
}
