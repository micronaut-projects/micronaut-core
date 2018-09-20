package io.micronaut.configuration.jmx

import io.micronaut.context.ApplicationContext
import io.micronaut.management.endpoint.loggers.LogLevel
import io.micronaut.management.endpoint.loggers.LoggersEndpoint
import spock.lang.Specification

import javax.management.MBeanInfo
import javax.management.MBeanOperationInfo
import javax.management.MBeanServer
import javax.management.ObjectName

class LoggersEndpointSpec extends Specification {

    void "test loggers endpoint execution through jmx"() {
        given:
        def ctx = ApplicationContext.run([
                'endpoints.all.enabled': true,
                'endpoints.all.sensitive': false
        ], 'test')

        when:
        MBeanServer server = ctx.getBean(MBeanServer)
        ObjectName name = new ObjectName("io.micronaut.management.endpoint", new Hashtable<>([type: LoggersEndpoint.getSimpleName()]))
        MBeanInfo info = server.getMBeanInfo(name)

        then:
        info.operations.length == 3
        info.operations[0].name == "loggers"
        info.operations[0].returnType == "java.util.Map"
        info.operations[0].impact == MBeanOperationInfo.INFO
        info.operations[0].signature.length == 0
        info.operations[1].name == "logger"
        info.operations[1].returnType == "java.util.Map"
        info.operations[1].impact == MBeanOperationInfo.INFO
        info.operations[1].signature.length == 1
        info.operations[1].signature[0].type == 'java.lang.String'
        info.operations[1].signature[0].name == 'name'
        info.operations[2].name == "setLogLevel"
        info.operations[2].returnType == "void"
        info.operations[2].impact == MBeanOperationInfo.ACTION
        info.operations[2].signature.length == 2
        info.operations[2].signature[0].type == 'java.lang.String'
        info.operations[2].signature[0].name == 'name'
        info.operations[2].signature[1].type == LogLevel.getName()
        info.operations[2].signature[1].name == 'configuredLevel'

        when:
        Object data = server.invoke(name, "loggers", [] as Object[], [] as String[])

        then:
        data instanceof Map
        data.containsKey('loggers')
        data.containsKey('levels')

        when:
        data = server.invoke(name, "logger", ['io.micronaut'] as Object[], ['java.lang.String'] as String[])

        then:
        data.configuredLevel == LogLevel.NOT_SPECIFIED

        when:
        server.invoke(name, "setLogLevel", ['io.micronaut', LogLevel.DEBUG] as Object[], ['java.lang.String', LogLevel.getName()] as String[])
        data = server.invoke(name, "logger", ['io.micronaut'] as Object[], ['java.lang.String'] as String[])

        then:
        data.configuredLevel == LogLevel.DEBUG

        cleanup:
        ctx.close()
    }
}
