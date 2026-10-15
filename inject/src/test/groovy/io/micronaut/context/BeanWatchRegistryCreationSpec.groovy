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

import io.micronaut.context.watch.BeanWatch
import io.micronaut.context.watch.ClassChangeWatcher
import spock.lang.AutoCleanup
import spock.lang.Specification

class BeanWatchRegistryCreationSpec extends Specification {

    @AutoCleanup
    DefaultBeanContext context = new DefaultBeanContext()
    BeanWatchRegistry registry = new BeanWatchRegistry(context)
    RuntimeBeanDefinition<String> outer = RuntimeBeanDefinition.of("outer")
    RuntimeBeanDefinition<String> inner = RuntimeBeanDefinition.of("inner")

    void "a creation abandoned between its instance and its end is unwound by the end of the creation around it"() {
        given:
        Object outerBean = new Object()
        registry.beginCreation(outer)
        registry.beginCreation(inner)
        BeanWatch innerWatch = watch()

        when: "the inner creation never ends, as when something throws after its instance was made, and the outer one does"
        registry.endCreation(outer, outerBean)

        then: "the abandoned creation's watch is closed, and no creation is left in progress on the thread"
        !innerWatch.active

        when: "a watch is registered outside any creation"
        BeanWatch unowned = watch()
        registry.closeOwnedBy(outerBean)

        then: "it belongs to no bean, rather than to the abandoned creation or the one around it"
        unowned.active

        cleanup:
        unowned?.close()
    }

    void "the thread keeps no creation state once its outermost creation ended"() {
        when:
        registry.beginCreation(outer)
        registry.beginCreation(inner)
        registry.endCreation(inner, new Object())

        then: "a creation is still in progress"
        registry.@creating.get() != null

        when:
        registry.endCreation(outer, new Object())

        then: "the thread local entry is removed"
        registry.@creating.get() == null
    }

    void "an end that pairs with no creation in progress leaves the creations in progress alone"() {
        given:
        Object outerBean = new Object()
        registry.beginCreation(outer)
        BeanWatch outerWatch = watch()

        when:
        registry.endCreation(inner, new Object())
        registry.endCreation(outer, outerBean)

        then: "the watch still belongs to the outer bean"
        outerWatch.active

        when:
        registry.closeOwnedBy(outerBean)

        then:
        !outerWatch.active
    }

    void "a failed creation closes the watches registered during it"() {
        given:
        registry.beginCreation(outer)
        BeanWatch failedWatch = watch()

        when:
        registry.endCreation(outer, null)

        then:
        !failedWatch.active
    }

    private BeanWatch watch() {
        registry.watchClassChanges({ change -> } as ClassChangeWatcher)
    }
}
