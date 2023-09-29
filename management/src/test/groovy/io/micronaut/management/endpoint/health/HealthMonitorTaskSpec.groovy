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
package io.micronaut.management.endpoint.health

import io.micronaut.context.ApplicationContext
import io.micronaut.health.CurrentHealthStatus
import io.micronaut.health.HealthStatus
import spock.lang.Specification

class HealthMonitorTaskSpec extends Specification {

    void "test CurrentHealthStatus is updated when check is down"() {
        given:
        ApplicationContext context = ApplicationContext.builder(['micronaut.health.monitor.enabled': true,
                                                                 'endpoints.health.disk-space.threshold': 999999999999999999,
                                                                 'micronaut.health.monitor.initial-delay': '0ms',
                                                                 'micronaut.application.name': 'health-monitor-task-test']).build()
        context.start()
        Thread.sleep(1000)

        expect:
        context.getBean(CurrentHealthStatus).current().name == HealthStatus.NAME_DOWN

        cleanup:
        context.close()
    }

}
