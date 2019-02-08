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
import spock.lang.Ignore
import spock.lang.Specification

class ScheduledInjectionExceptionSpec extends Specification {

    @Ignore
    void "testing bean injections in scheduled beans logs an error"() {
        given:
        def oldOut = System.out
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        System.out = new PrintStream(baos)
        ApplicationContext ctx = ApplicationContext.run('injection-exception.task.enabled':'true')

        when:
        String output = baos.toString("UTF-8")
        baos.close()

        then:
        output.contains("DependencyInjectionException: Failed to inject value for field [notInjectable]")

        cleanup:
        System.out = oldOut
        ctx.close()
    }
}
