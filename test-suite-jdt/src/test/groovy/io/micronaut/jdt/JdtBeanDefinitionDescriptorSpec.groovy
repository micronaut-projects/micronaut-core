package io.micronaut.jdt

import io.micronaut.inject.writer.BeanDefinitionDescriptors
import spock.lang.Specification

/**
 * The beans of {@code src/jdt/java} are compiled by the Eclipse JDT compiler, whose {@code Filer} writes the
 * {@code META-INF/micronaut} entries of their definitions.
 */
class JdtBeanDefinitionDescriptorSpec extends Specification {

    void "the entries written with the JDT compiler carry descriptors that agree with the loaded references"() {
        when:
        def comparison = BeanDefinitionDescriptors.compareAll(getClass().classLoader)

        then:
        comparison.differences.isEmpty()
        comparison.compared.containsAll([
            'io.micronaut.jdt.beans.$Engine$Definition',
            'io.micronaut.jdt.beans.$EngineConfig$Definition',
            'io.micronaut.jdt.beans.$Greeter$Definition',
            'io.micronaut.jdt.beans.$Greeter$Definition$Intercepted$Definition'
        ])
    }
}
