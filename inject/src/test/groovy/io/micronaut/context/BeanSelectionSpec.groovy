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

import io.micronaut.inject.BeanDefinition
import spock.lang.Specification

class BeanSelectionSpec extends Specification {

    void "order selection handles ties and extreme values without changing unresolved candidate order"() {
        given:
        List<BeanDefinition<Object>> candidates = orders.collect { int order ->
            Stub(BeanDefinition) {
                getOrder() >> order
                getDefaultImplementation() >> null
            }
        }

        expect:
        BeanSelection.select(null, null, candidates, BeanResolutionCustomizer.DEFAULT) == selected.collect { candidates[it] }

        where:
        orders                                                 | selected
        [Integer.MAX_VALUE, 0, Integer.MIN_VALUE]                | [2]
        [Integer.MIN_VALUE, Integer.MAX_VALUE]                   | [0]
        [0, 0, Integer.MIN_VALUE]                                | [2]
        [1, 0, 1]                                              | [1]
        [Integer.MAX_VALUE, Integer.MAX_VALUE]                   | [0, 1]
        [Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE] | [0, 1, 2]
        [0, 0, 1]                                              | [0, 1, 2]
    }
}
