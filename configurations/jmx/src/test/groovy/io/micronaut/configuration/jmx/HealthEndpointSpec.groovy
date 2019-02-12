/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.jmx

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
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
        ], Environment.TEST)

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
