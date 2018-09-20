package io.micronaut.configuration.jmx

import io.micronaut.context.ApplicationContext
import io.micronaut.health.HealthStatus
import io.micronaut.management.endpoint.health.HealthEndpoint
import io.micronaut.management.health.indicator.HealthResult
import spock.lang.Specification

import javax.management.MBeanInfo
import javax.management.MBeanOperationInfo
import javax.management.MBeanServer
import javax.management.ObjectName
import java.security.Principal

class HealthEndpointSpec extends Specification {

    void "test the health endpoint works through JMX"() {
        given:
        def ctx = ApplicationContext.run([
                'endpoints.all.enabled': true,
                'endpoints.all.sensitive': false
        ], 'test')

        when:
        MBeanServer server = ctx.getBean(MBeanServer)
        ObjectName name = new ObjectName("io.micronaut.management.endpoint", new Hashtable<>([type: HealthEndpoint.getSimpleName()]))
        MBeanInfo info = server.getMBeanInfo(name)

        then:
        info.operations.length == 1
        info.operations[0].name == "getHealth"
        info.operations[0].returnType == "io.micronaut.management.health.indicator.HealthResult"
        info.operations[0].impact == MBeanOperationInfo.INFO
        info.operations[0].signature.length == 1
        info.operations[0].signature[0].type == 'java.security.Principal'
        info.operations[0].signature[0].name == 'principal'

        when:
        Principal principal = new Principal() {
            @Override
            String getName() {
                return "john"
            }
        }
        Object data = server.invoke(name, "getHealth", [principal] as Object[], ['java.security.Principal'] as String[])

        then:
        data instanceof HealthResult
        data.status == HealthStatus.UP
        data.details.size() > 0

        when:
        data = server.invoke(name, "getHealth", [null] as Object[], ['java.security.Principal'] as String[])

        then:
        data instanceof HealthResult
        data.status == HealthStatus.UP
        data.details == null

        cleanup:
        ctx.close()
    }
}
