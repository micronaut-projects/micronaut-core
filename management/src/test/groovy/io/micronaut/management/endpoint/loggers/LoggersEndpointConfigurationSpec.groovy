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
package io.micronaut.management.endpoint.loggers

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

/**
 * @uathor Matthew Moss
 * @since 1.0
 */
class LoggersEndpointConfigurationSpec extends Specification {

    void 'test that the loggers endpoint is not enabled by default'() {
        given:
        ApplicationContext context = ApplicationContext.run()

        expect:
        !context.containsBean(LoggersEndpoint)

        cleanup:
        context.close()
    }

    void 'test that the loggers endpoint is not available when disabled via config'() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.loggers.enabled': false])

        expect:
        !context.containsBean(LoggersEndpoint)

        cleanup:
        context.close()
    }

    void 'test that the loggers endpoint is available when enabled via config'() {
        given:
        ApplicationContext context = ApplicationContext.run(['endpoints.loggers.enabled': true])

        expect:
        context.containsBean(LoggersEndpoint)

        cleanup:
        context.close()
    }

}
