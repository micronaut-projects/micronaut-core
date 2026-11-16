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

import io.micronaut.inject.BeanType
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

import java.util.stream.Stream

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

    void "a full collection cache replaces the holders a change of the bean definitions left stale"() {
        given:
        context.registerSingleton(String, "kept")
        for (int i = 0; i < DefaultBeanContext.MAX_CACHED_CANDIDATES; i++) {
            assert context.getBeanRegistrations(String, new MatchingAll(i)).size() == 1
        }
        assert context.@singletonBeanRegistrations.size() == DefaultBeanContext.MAX_CACHED_CANDIDATES

        when: "an unrelated registration leaves every cached collection of strings stale"
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(Integer, () -> 1).build())
        def first = context.getBeanRegistrations(String, new MatchingAll(0))

        then: "the stale holder of the key is replaced although the cache is full"
        first.size() == 1
        context.getBeanRegistrations(String, new MatchingAll(0)).is(first)
        context.@singletonBeanRegistrations.size() <= DefaultBeanContext.MAX_CACHED_CANDIDATES
    }

    private static final class MatchingAll implements Qualifier<String> {
        final int id

        MatchingAll(int id) {
            this.id = id
        }

        @Override
        <BT extends BeanType<String>> Stream<BT> reduce(Class<String> beanType, Stream<BT> candidates) {
            return candidates
        }

        @Override
        boolean equals(Object o) {
            return o instanceof MatchingAll && ((MatchingAll) o).id == id
        }

        @Override
        int hashCode() {
            return id
        }
    }
}
