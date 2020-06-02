package io.micronaut.health

import io.micronaut.context.ApplicationContext
import io.micronaut.context.condition.ConditionContext
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.discovery.CompositeDiscoveryClient
import io.micronaut.discovery.DiscoveryClient
import io.micronaut.inject.BeanDefinition
import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.util.environment.Jvm

import static java.lang.Boolean.FALSE

// for some reason these tests fail with illegal method name on JDK 11/13
// seems like Spock issue
@IgnoreIf({ Jvm.current.isJava9Compatible() })
class HeartbeatDiscoveryClientConditionSpec extends Specification {

    HeartbeatDiscoveryClientCondition heartbeatDiscoveryClientCondition = new HeartbeatDiscoveryClientCondition()

    def "matches should return true when micronaut.heartbeat.enabled is not defined"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        !matches
        1 * conditionContext.getProperty(HeartbeatConfiguration.ENABLED, ArgumentConversionContext.BOOLEAN) >> Optional.empty()
        1 * conditionContext.getBean(CompositeDiscoveryClient.class) >> new CompositeDiscoveryClient() {}
    }

    def "matches should return false when micronaut.heartbeat.enabled is defined as false and there are no discovery clients"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        !matches
        1 * conditionContext.getProperty(HeartbeatConfiguration.ENABLED, ArgumentConversionContext.BOOLEAN) >> Optional.of(FALSE)
        1 * conditionContext.getBean(CompositeDiscoveryClient.class) >> new CompositeDiscoveryClient() {}
    }

    def "matches should return true when micronaut.heartbeat.enabled is defined as false and there are discovery clients"() {

        given:
        ConditionContext conditionContext = Mock(ConditionContext)

        when:
        Boolean matches = heartbeatDiscoveryClientCondition.matches(conditionContext)

        then:
        matches
        1 * conditionContext.getBean(CompositeDiscoveryClient.class) >> new CompositeDiscoveryClient(Mock(DiscoveryClient)) {
        }
    }
}
