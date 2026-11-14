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

import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

class CandidateCacheBoundSpec extends Specification {

    @AutoCleanup
    DefaultBeanContext context = new DefaultBeanContext().start()

    void "lookups of qualifiers that match no bean do not grow the candidate cache without bound"() {
        given:
        context.registerSingleton(String, "kept", Qualifiers.byName("kept"))

        when:
        for (int i = 0; i < DefaultBeanContext.MAX_CACHED_CANDIDATES + 500; i++) {
            assert !context.findBean(String, Qualifiers.byName("missing-" + i)).isPresent()
        }

        then: "the cache stops at its bound"
        context.@beanConcreteCandidateCache.size() <= DefaultBeanContext.MAX_CACHED_CANDIDATES

        and: "lookups past the bound are still answered"
        context.getBean(String, Qualifiers.byName("kept")) == "kept"
        !context.findBean(String, Qualifiers.byName("missing-again")).isPresent()
    }

    void "presence lookups of missing names do not grow their cache without bound"() {
        when:
        for (int i = 0; i < DefaultBeanContext.MAX_CACHED_CANDIDATES + 500; i++) {
            assert !context.containsBean(String, Qualifiers.byName("missing-" + i))
        }

        then:
        context.containsBeanCache.size() <= DefaultBeanContext.MAX_CACHED_CANDIDATES
        !context.containsBean(String, Qualifiers.byName("missing-again"))
    }
}
