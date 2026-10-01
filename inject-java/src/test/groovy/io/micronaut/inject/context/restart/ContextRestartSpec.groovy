/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.context.restart

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ContextRestartSpec extends Specification {

    void "a context closed twice can be restarted and stopped again"() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when: "the context is closed twice"
        context.close()
        context.close()

        then: "it is stopped"
        !context.isRunning()

        when: "the context is started again and a singleton is created"
        context.start()
        RestartableBean bean = context.getBean(RestartableBean)

        then:
        context.isRunning()
        !bean.destroyed

        when: "the restarted context is closed"
        context.close()

        then: "the singleton received its @PreDestroy callback"
        !context.isRunning()
        bean.destroyed
    }
}
