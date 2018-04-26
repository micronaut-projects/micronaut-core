/*
 * Copyright 2017-2018 original authors
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
package io.micronaut.management.health.indicator.diskspace

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class DiskSpaceIndicatorConfigurationSpec extends Specification {

    void "test threshold configuration"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.health.disk-space.threshold': '100GB'])

        expect:
        context.getBean(DiskSpaceIndicatorConfiguration).threshold == 1024L * 1024L * 1024L * 100L

        cleanup:
        context.close()
    }

    void "test path configuration"() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.health.disk-space.path': '/foo'])

        expect:
        context.getBean(DiskSpaceIndicatorConfiguration).path.absolutePath == "/foo"

        cleanup:
        context.close()
    }
}
