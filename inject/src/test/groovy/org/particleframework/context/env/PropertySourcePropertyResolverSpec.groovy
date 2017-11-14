/*
 * Copyright 2017 original authors
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
package org.particleframework.context.env

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class PropertySourcePropertyResolverSpec extends Specification {

    @Unroll
    void "test resolve property #property value for #key"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of((property): value)
        )

        expect:
        resolver.containsProperty(key)
        resolver.getProperty(key).isPresent()
        resolver.getProperty(key, type).get() == expected

        where:
        property       | value       | key            | type    | expected
        'foo.bar'      | 'test'      | 'foo.bar'      | String  | 'test'
        'foo.bar'      | '10'        | 'foo.bar'      | Integer | 10
        'foo.bar'      | ['10']      | 'foo.bar[0]'   | Integer | 10
        'foo.bar'      | [foo: '10'] | 'foo.bar[foo]' | Integer | 10
        'foo.bar.baz'  | '10'        | 'foo.bar[baz]' | Integer | 10
        'foo.bar[0]'   | '10'        | 'foo.bar[0]'   | Integer | 10
        'foo.bar[0]'   | '10'        | 'foo.bar'      | List    | ['10']
        'foo.bar[baz]' | '10'        | 'foo.bar[baz]' | Integer | 10
        'foo.bar[baz]' | '10'        | 'foo.bar'      | Map     | [baz: '10']
    }
}
