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
package io.micronaut.scheduling.exceptions

import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class ScheduledExceptionHandlingSpec extends Specification {

    void "test that a task that throws a specific exception is handled by the correct handler"() {
        given:
        ApplicationContext ctx = ApplicationContext.run('scheduled-exception1.task.enabled':'true')
        PollingConditions conditions = new PollingConditions(timeout: 10, delay: 0.5)

        expect:
        conditions.eventually {
            ctx.getBean(BeanAndTypeSpecificHandler).getThrowable()
            ctx.getBean(BeanAndTypeSpecificHandler).getBean()
            !ctx.getBean(TypeSpecificHandler).getThrowable()
            !ctx.getBean(TypeSpecificHandler).getBean()
        }

        cleanup:
        ctx.close()
    }
}
