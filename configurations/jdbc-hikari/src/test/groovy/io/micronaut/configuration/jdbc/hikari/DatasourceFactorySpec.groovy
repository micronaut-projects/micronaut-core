package io.micronaut.configuration.jdbc.hikari

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class DatasourceFactorySpec extends Specification {

    def "wire class with constructor"() {
        expect:
        new DatasourceFactory(Mock(ApplicationContext))
    }
}
