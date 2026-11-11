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
package io.micronaut.context

import io.micronaut.core.type.Argument
import spock.lang.AutoCleanup
import spock.lang.Specification

class SingletonLookupEpochSpec extends Specification {

    @AutoCleanup
    DefaultBeanContext context = new DefaultBeanContext().start()

    void "a looked up singleton is returned only for the definitions epoch its lookup began at"() {
        given: "an entry a lookup wrote after a registration moved the epoch"
        context.registerSingleton(String, "indexed")
        BeanRegistration<String> registration = context.getBeanRegistration(String, null)
        SingletonScope scope = context.@singletonScope
        def key = new DefaultBeanContext.BeanKey<String>(Argument.of(String), null)
        scope.indexLookup(key, registration, 5L, 2048)

        expect: "a reader of the epoch it was computed at gets it"
        scope.findLookedUpSingleton(key, 5L).is(registration)

        and: "a reader after the registration rejects it, before the writer could remove it"
        scope.findLookedUpSingleton(key, 6L) == null

        and: "and the rejected entry is gone"
        scope.findLookedUpSingleton(key, 5L) == null
    }
}
