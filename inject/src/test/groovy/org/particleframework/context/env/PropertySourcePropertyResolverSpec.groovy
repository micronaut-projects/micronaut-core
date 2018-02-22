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
    void "test resolve environment properties"() {

    }

    @Unroll
    void "test resolve property #property value for #key"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [(property): value], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE)
        )

        expect:
        resolver.containsProperty(key)
        resolver.getProperty(key, Object).isPresent()
        resolver.getProperty(key, type).get() == expected

        where:
        property                      | value | key                           | type   | expected
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2-access-token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2.access.token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2.accesstoken'  | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2.access-token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.OAuth2AccessToken'   | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2accesstoken'   | String | 'xxx'

    }

    @Unroll
    void "test resolve placeholders for property #property and #value"() {
        given:
        def values = [
                'foo.bar': '10',
                'foo.baz': 20
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [(property): value] + values)
        )

        expect:

        resolver.getProperty(key, Object).isPresent()
        resolver.getProperty(key, type)
        resolver.getProperty(key, type).get() == expected
        resolver.containsProperty(key)

        where:
        property      | value                                                | key           | type    | expected
        'my.property' | '/${foo.bar}/stuff'                                  | 'my.property' | String  | '/10/stuff'
        'my.property' | '${not.there:foo.bar:50}'                            | 'my.property' | String  | '10'
        'my.property' | '${not.there:foo.bar:50}'                            | 'my.property' | String  | '10'
        'my.property' | '${not.there:also.not.there:50}'                     | 'my.property' | String  | '50'
        'my.property' | '${not.there:also.not.there:}'                       | 'my.property' | String  | ''
        'my.property' | '${not.there:USER:50}'                               | 'my.property' | String  | System.getenv('USER')
        'my.property' | '${foo.bar} + ${not.there:50} + ${foo.bar}'          | 'my.property' | String  | '10 + 50 + 10'
        'my.property' | '${foo.bar}'                                         | 'my.property' | String  | '10'
        'my.property' | '${not.there:50}'                                    | 'my.property' | String  | '50'
        'my.property' | '${foo.bar} + ${foo.bar}'                            | 'my.property' | String  | '10 + 10'
        'my.property' | '${foo.bar[0]}'                                      | 'my.property' | List    | ['10']
        'my.property' | '${foo.bar[0]}'                                      | 'my.property' | Integer | 10
        'my.property' | '${USER}'                                            | 'my.property' | String  | System.getenv('USER')
        'my.property' | 'bolt://${NEO4J_HOST:localhost}:${NEO4J_PORT:32781}' | 'my.property' | String  | 'bolt://localhost:32781'
    }


    void "test resolve placeholders for maps"() {
        given:
        def values = [
                'foo.bar'          : '10',
                'foo.baz'          : 20,
                'my.property.one'  : '${foo.bar} + ${not.there:50} + ${foo.bar}',
                'my.property.two'  : '${foo.bar}',
                'my.property.three': '${foo.bar } + ${ foo.baz}'
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:

        resolver.getProperty('my.property', Map).isPresent()
        resolver.getProperty('my.property', Map).get() == [one: '10 + 50 + 10', two: '10', three: '10 + 20']
    }


    void "test resolve placeholders for properties"() {
        given:
        def values = [
                'foo.bar'          : '10',
                'foo.baz'          : 20,
                'my.property.one'  : '${foo.bar} + ${not.there:50} + ${foo.bar}',
                'my.property.two'  : '${foo.bar}',
                'my.property.three': '${foo.bar } + ${ foo.baz}'
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )
        Properties properties = new Properties()
        properties.putAll([one: '10 + 50 + 10', two: '10', three: '10 + 20'])
        expect:

        resolver.getProperty('my.property', Properties).isPresent()
        resolver.getProperty('my.property', Properties).get() == properties
    }
}
