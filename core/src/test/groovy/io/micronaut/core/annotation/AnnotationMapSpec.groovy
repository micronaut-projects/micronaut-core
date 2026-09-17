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
package io.micronaut.core.annotation

import spock.lang.Specification

class AnnotationMapSpec extends Specification {
    void "metadata preserves immutable typed maps but wraps live maps"() {
        given:
        def source = [name: 'one']
        def frozen = new TestMap(source, true)
        def live = new TestMap(source, false)
        def ordinaryValue = new AnnotationValue('example.Rule', source)
        def liveValue = new AnnotationValue('example.Rule', live)
        def frozenValue = new AnnotationValue('example.Rule', frozen)

        when:
        source.name = 'two'

        then:
        frozenValue.values.is(frozen)
        frozenValue.values == [name: 'one']
        !liveValue.values.is(live)
        liveValue.values.name == 'two'
        ordinaryValue.values.name == 'two'
        frozen == [name: 'one']
        [name: 'one'] == frozen
        frozen.hashCode() == [name: 'one'].hashCode()
        frozen.toString() == '{name=one}'

        when:
        liveValue.values.put('other', 'value')

        then:
        thrown(UnsupportedOperationException)
    }

    void "all mutation paths on frozen maps reject writes"() {
        given:
        def frozen = new TestMap([name: 'one'], true)

        when:
        mutation(frozen)

        then:
        thrown(UnsupportedOperationException)
        frozen == [name: 'one']

        where:
        mutation << [
            { it.put('name', 'two') },
            { it.remove('name') },
            { it.putAll([other: 'two']) },
            { it.clear() },
            { it.putIfAbsent('name', 'two') },
            { it.remove('name', 'one') },
            { it.replace('name', 'two') },
            { it.replace('name', 'one', 'two') },
            { it.replaceAll { k, v -> 'two' } },
            { it.computeIfAbsent('other') { 'two' } },
            { it.computeIfPresent('name') { k, v -> 'two' } },
            { it.compute('name') { k, v -> 'two' } },
            { it.merge('name', 'two') { a, b -> b } },
            { it.keySet().remove('name') },
            { it.values().remove('one') },
            { it.entrySet().iterator().next().setValue('two') }
        ]
    }

    void "live adapters delegate default map methods and views"() {
        given:
        def source = [name: 'one']
        def live = new TestMap(source, false)

        when:
        live.compute('name') { key, value -> value + '!' }
        live.putIfAbsent('other', 'two')
        live.entrySet().find { it.key == 'other' }.setValue('three')

        then:
        source == [name: 'one!', other: 'three']
        live.getOrDefault('missing', 'fallback') == 'fallback'
    }

    private static class TestMap extends AnnotationMap {
        TestMap(Map<CharSequence, Object> map, boolean immutable) {
            super(map, immutable)
        }
    }
}
