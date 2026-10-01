/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.inject.close.shutdowncreated

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class CreatedDuringShutdownSpec extends Specification {

    static List<Class<?>> destroyed = []

    void "test a singleton created from a @PreDestroy hook is destroyed before stop() returns"() {
        given:
        destroyed.clear()
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])
        ctx.getBean(ShutdownCreator)

        expect: "nothing has been destroyed yet"
        destroyed.isEmpty()

        when:
        ctx.stop()

        then: "both the original bean and the one created during shutdown were destroyed"
        destroyed == [ShutdownCreator, LateSingleton]
    }
}
