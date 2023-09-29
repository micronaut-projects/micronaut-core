package io.micronaut.inject

import spock.lang.Specification

class DefaultBeanDefinitionMethodReferenceSpec extends Specification {

    void "default bean definition method reference delegates isAbstract() to target method"() {
        given:
        final target = Mock(ExecutableMethod) { isAbstract() >> true }
        final method = new DefaultBeanDefinitionMethodReference(null, target)
        expect:
        method.isAbstract() == true
    }

    void "default bean definition method reference delegates isSuspend() to target method"() {
        given:
        final target = Mock(ExecutableMethod) { isSuspend() >> true }
        final method = new DefaultBeanDefinitionMethodReference(null, target)
        expect:
        method.isSuspend() == true
    }
}
