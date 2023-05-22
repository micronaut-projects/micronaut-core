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
package io.micronaut.core.convert.value


import spock.lang.Specification

class ConvertibleValuesSpec extends Specification {

    void "test convertible values as Map"() {
        given:
        def vals = ConvertibleValues.of(
                'foo':'bar',
                'num':1,
                'baz': ['one': 1, 'two': 2]
        )

        when:
        def map = vals.asMap(String, String)

        then:
        map.foo == 'bar'
        map.num == '1'

        when:
        def props = vals.asProperties()

        then:
        props.size() == 2
        props.foo == 'bar'
        props.num == '1'
    }
}
