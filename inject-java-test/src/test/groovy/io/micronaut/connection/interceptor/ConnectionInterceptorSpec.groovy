package io.micronaut.management.endpoint.health

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.Statement

class ConnectionInterceptorSpec extends Specification {

    void "test the beans are available"() {
        given:
        ApplicationContext context = ApplicationContext.builder("test").build()
        context.registerSingleton(Mock(DataSource))
        context.start()
        def conn = context.getBean(Connection)

        when:"Intercepted connection does have method implemented"
        def result = conn.prepareStatement("SELECT 1")
        then:
        result == null
        noExceptionThrown()

        when:"Intercepted connection does not have method implemented"
        conn.prepareStatement("SELECT 1", Statement.RETURN_GENERATED_KEYS)
        then:
        AbstractMethodError ex = thrown()
        ex

        cleanup:
        context.close()
    }

}
