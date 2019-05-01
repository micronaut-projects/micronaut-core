package io.micronaut.docs.factories.nullable

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class EngineSpec extends Specification {

    void "test factory null"() {
        ApplicationContext applicationContext = ApplicationContext.run([
                "engines.subaru.cylinders": 4,
                "engines.ford.cylinders": 8,
                "engines.ford.enabled": false,
                "engines.lamborghini.cylinders": 12
        ])

        when:
        Collection<Engine> engines = applicationContext.getBeansOfType(Engine)
        int totalCylinders = engines.sum { it.cylinders }

        then:
        engines.size() == 2
        totalCylinders == 16
    }
}
