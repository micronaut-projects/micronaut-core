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
package io.micronaut.cache

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class SyncCacheJavaSpec extends Specification {
    void "test cacheable annotations"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run(
                'micronaut.caches.counter.initialCapacity':10,
                'micronaut.caches.counter.maximumSize':20,
                'micronaut.caches.counter.testMode':true

        )

        when:
        CounterService counterService = applicationContext.getBean(CounterService)
        def result =counterService.increment("test")

        then:
        result == 1
        counterService.getValue("test") == 1
        counterService.getValue("test") == 1

        when:
        result = counterService.incrementNoCache("test")

        then:
        result == 2
        counterService.getValue("test") == 1

        when:
        counterService.reset("test")
        then:
        counterService.getValue("test") == 0

        when:
        result = counterService.increment("test")

        then:
        result == 1
        counterService.getValue("test") == 1

    }
}
