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
package io.micronaut.context.reload

import spock.lang.Specification


class ClassChangeEventSpec extends Specification {

    void "a class is stale when a retired generation loaded it"() {
        given: "two generations defining the same class"
        GroovyClassLoader retired = new GroovyClassLoader(getClass().classLoader)
        GroovyClassLoader current = new GroovyClassLoader(getClass().classLoader)
        Class<?> retiredCopy = retired.parseClass('package example; class Reloadable {}')
        Class<?> currentCopy = current.parseClass('package example; class Reloadable {}')
        def event = new ClassChangeEvent(this, 1, [retiredCopy.classLoader] as Set, currentCopy.classLoader,
            [new ClassChange("example.Reloadable", ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD)

        expect:
        event.isStale(retiredCopy)
        event.isStaleInstance(retiredCopy.getDeclaredConstructor().newInstance())
        !event.isStale(currentCopy)
        !event.isStale(String)
        !event.isStale((Class) null)
        event.affects("example.Reloadable")
        !event.affects("example.Other")
        event.generation() == 1
        event.strategy() == ReloadStrategy.RELOAD
        event.retiredLoaders().contains(retiredCopy.classLoader)

        cleanup:
        retired.close()
        current.close()
    }

    void "the strategy of a change is the one applied"() {
        when:
        new ClassChangeEvent(this, 1, [] as Set, getClass().classLoader, [], ReloadStrategy.AUTO)

        then:
        thrown(IllegalArgumentException)
    }
}
