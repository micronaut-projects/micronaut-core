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
package io.micronaut.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.value.MapPropertyResolver
import io.micronaut.core.value.PropertyResolver
import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class PropertySourcePropertyResolverSpec extends Specification {

    @Rule
    private final EnvironmentVariables environmentVariables = new EnvironmentVariables()

    void "test resolve property entries"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [DATASOURCE_DEFAULT_URL: 'xxx', DATASOURCE_OTHER_URL:'xxx'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE),
                PropertySource.of("test",
                        ['datasource.third.url': 'xxx'],
                        PropertySource.PropertyConvention.JAVA_PROPERTIES
                )
        )

        expect:
        resolver.getPropertyEntries("datasource") == ['default', 'other', 'third'] as Set
    }

    void "test resolve raw properties"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [TWITTER_OAUTH2_ACCESS_TOKEN: 'xxx'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE),
                PropertySource.of("test",
                        ['camelCase.fooBar': 'xxx',
                         'camelCase.URL'   : "http://localhost"],
                        PropertySource.PropertyConvention.JAVA_PROPERTIES
                )
        )

        expect:
        resolver.getPropertyEntries("twitter") == ['oauth2'] as Set
        resolver.containsProperty('TWITTER_OAUTH2_ACCESS_TOKEN')
        resolver.getProperty('TWITTER_OAUTH2_ACCESS_TOKEN', String).get() == 'xxx'
        resolver.containsProperty("camelCase.URL")
        resolver.containsProperties("camel-case")
        resolver.containsProperties("camelCase")
        resolver.getProperties("camelCase", StringConvention.RAW) == ['fooBar': 'xxx',
                                                                            'URL'   : "http://localhost"]
        resolver.getProperty("camelCase.URL", URL).get() == new URL("http://localhost")
    }

    @Unroll
    void "test property resolution rules for key #key"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [TWITTER_OAUTH2_ACCESS_TOKEN: 'xxx'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE),
                PropertySource.of("test",
                        ['camelCase.fooBar': 'xxx',
                         'camelCase.URL'   : "http://localhost"],
                        PropertySource.PropertyConvention.JAVA_PROPERTIES
                )
        )

        expect:
        resolver.containsProperty(key)
        resolver.getProperty(key, Object).isPresent()
        resolver.getProperty(key, String).get() == expected


        where:
        key                           | expected
        'twitter.oauth2.access.token' | 'xxx'
        'camel-case.foo-bar'          | 'xxx'
        'camel-case.url'              | 'http://localhost'
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
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2.access-token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2.access.token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter.oauth2-access.token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter-oauth2.access.token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter-oauth2-access-token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter-oauth2.access-token' | String | 'xxx'
        'TWITTER_OAUTH2_ACCESS_TOKEN' | 'xxx' | 'twitter-oauth2-access.token' | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my.app.my.stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my.app.my-stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my.app-my.stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my.app-my-stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my-app.my.stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my-app.my-stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my-app-my.stuff'             | String | 'xxx'
        'MY_APP_MY_STUFF'             | 'xxx' | 'my-app-my-stuff'             | String | 'xxx'
    }

    @Unroll
    void "test resolve placeholders for property #property and #value"() {
        given:
        def values = [
                'foo.bar': '10',
                'foo.baz': 20,
                'bar'    : 30
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [(property): value] + values)
        )
        environmentVariables["FOO_BAR"] = "foo bar"
        environmentVariables["FOO_BAR_1"] = "foo bar 1"

        expect:

        resolver.getProperty(key, Object).isPresent()
        resolver.getProperty(key, type)
        resolver.getProperty(key, type).get() == expected
        resolver.containsProperty(key)

        where:
        property      | value                                                | key           | type    | expected
        'my.property' | '${not.there:foo.bar:50}'                            | 'my.property' | String  | '10'
        'my.property' | '/${foo.bar}/stuff'                                  | 'my.property' | String  | '/10/stuff'
        'my.property' | '${not.there:foo.bar:50}'                            | 'my.property' | String  | '10'
        'my.property' | '${not.there:also.not.there:50}'                     | 'my.property' | String  | '50'
        'my.property' | '${not.there:also.not.there:}'                       | 'my.property' | String  | ''
        'my.property' | '${not.there:FOO_BAR:50}'                            | 'my.property' | String  | 'foo bar'
        'my.property' | '${foo.bar} + ${not.there:50} + ${foo.bar}'          | 'my.property' | String  | '10 + 50 + 10'
        'my.property' | '${foo.bar}'                                         | 'my.property' | String  | '10'
        'my.property' | '${not.there:50}'                                    | 'my.property' | String  | '50'
        'my.property' | '${foo.bar} + ${foo.bar}'                            | 'my.property' | String  | '10 + 10'
        'my.property' | '${foo.bar[0]}'                                      | 'my.property' | List    | ['10']
        'my.property' | '${foo.bar[0]}'                                      | 'my.property' | Integer | 10
        'my.property' | '${FOO_BAR}'                                         | 'my.property' | String  | 'foo bar'
        'my.property' | '${FOO_BAR_1}'                                       | 'my.property' | String  | 'foo bar 1'
        'my.property' | 'bolt://${NEO4J_HOST:localhost}:${NEO4J_PORT:32781}' | 'my.property' | String  | 'bolt://localhost:32781'
        'my.property' | '${bar}'                                             | 'my.property' | Integer | 30
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

    void "test resolve placeholders for lists of map"() {
        given:
        def values = [
                'foo.bar'     : '10',
                'foo.bar.list': [
                        [
                                'foo': '${foo.bar}'
                        ],
                        [
                                'bar': 'baz'
                        ]
                ]
        ]

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty('foo.bar.list', List).isPresent()
        resolver.getProperty('foo.bar.list', List).get() == [['foo': '10'], ['bar': 'baz']]
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

    void "test map placeholder resolver"() {
        given:
        String template = "Hello \${foo}!"
        Map<String, Object> parameters = [foo: "bar"]
        PropertyResolver propertyResolver = new MapPropertyResolver(parameters)
        DefaultPropertyPlaceholderResolver propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(propertyResolver, ConversionService.SHARED)
        List<DefaultPropertyPlaceholderResolver.Segment> segments = propertyPlaceholderResolver.buildSegments("Hello \${foo} \${bar:test}!")

        expect:
        propertyPlaceholderResolver.resolvePlaceholders(template).get() == "Hello bar!"
        segments.size() == 5
        segments[0] instanceof DefaultPropertyPlaceholderResolver.RawSegment
        segments[1] instanceof DefaultPropertyPlaceholderResolver.PlaceholderSegment
        segments[2] instanceof DefaultPropertyPlaceholderResolver.RawSegment
        segments[3] instanceof DefaultPropertyPlaceholderResolver.PlaceholderSegment
        segments[4] instanceof DefaultPropertyPlaceholderResolver.RawSegment
        segments[0].getValue(String.class) == "Hello "
        segments[1].getValue(String.class) == "bar"
        segments[2].getValue(String.class) == " "
        segments[3].getValue(String.class) == "test"
        segments[4].getValue(String.class) == "!"
    }

    void "test random placeholders for properties"() {
        given:
        def values = [
                'random.integer'  : '${random.integer}',
                'random.long'     : '${random.long}',
                'random.float'    : '${random.float}',
                'random.uuid'     : '${random.uuid}',
                'random.uuid2'    : '${random.uuid2}',
                'random.shortuuid': '${random.shortuuid}'
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty('random.integer', String).isPresent()
        resolver.getProperty('random.integer', String).get() =~ /\d+/

        and:
        resolver.getProperty('random.long', String).isPresent()
        resolver.getProperty('random.long', String).get() =~ /\d+/

        and:
        resolver.getProperty('random.float', String).isPresent()
        resolver.getProperty('random.float', String).get() =~ /\d+/

        and:
        resolver.getProperty('random.uuid', String).isPresent()
        resolver.getProperty('random.uuid', String).get().length() == 36

        and:
        resolver.getProperty('random.uuid2', String).isPresent()
        resolver.getProperty('random.uuid2', String).get().length() == 32

        and:
        resolver.getProperty('random.shortuuid', String).isPresent()
        resolver.getProperty('random.shortuuid', String).get().length() == 10
    }

    void "test invalid random placeholders for properties"() {
        when:
        def values = [
                'random.invalid': '${random.invalid}'
        ]
        new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        then:
        thrown(ConfigurationException)
    }

    void "test escaping the value delimiter"() {
        given:
        def values = [
                'foo.bar': '10',
                'foo.baz': 20,
                'bar'    : '${foo:`some:value`}',
                'baz'    : '${foo:`some:value`:`some:other:value`}',
                'single' : '${foo:some default with `something` in backticks}',
                'start'  : '${foo:`startswithtick}'
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty("bar", String).get() == "some:value"
        resolver.getProperty("baz", String).get() == "some:other:value"
        resolver.getProperty("single", String).get() == "some default with `something` in backticks"
        resolver.getProperty("start", String).get() == "`startswithtick"
    }

    void "test properties starting with z"() {
        given:
        def values = [
                'z': true
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("zprops", values)
        )

        expect:
        resolver.getProperty("z", Boolean).isPresent()
    }

    void "test retrieving a property as string then boolean"() {
        given:
        def applicationContext = ApplicationContext.run(['micronaut.security.enabled': true])

        expect:
        applicationContext.getProperty('micronaut.security.enabled', String).get() == "true"
        applicationContext.getProperty('micronaut.security.enabled', Boolean).get() == true

    }

    void "test property lists with 3 entries or more"() {
        given:
        def values = new HashMap()
        values.put('foo[0]', 'bar')
        values.put('foo[1]', 'baz')
        values.put('foo[2]', 'foo')
        values.put('foo[3]', 'baar')
        values.put('foo[4]', 'baaz')
        values.put('foo[5]', 'fooo')
        values.put('foo[15]', 'fooo')

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty("foo", List).get().get(0) == "bar"

    }

    void "test getProperties"() {
        given:
        def values = [
                'foo.bar'          : 'two',
                'my.property.one'  : 'one',
                'my.property.two'  : '${foo.bar}',
                'my.property.three': 'three',
                'test-key.convention-test': 'key'
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values))

        expect:
        resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.NESTED).containsKey('my')
        resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT).containsKey('my.property.one')
        resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT).get('my.property.two') == 'two'
        resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.NESTED).get('my').get('property').get('two') == 'two'
        resolver.getAllProperties(StringConvention.CAMEL_CASE, MapFormat.MapTransformation.FLAT).get('testKey.conventionTest') == 'key'
        resolver.getAllProperties(StringConvention.CAMEL_CASE, MapFormat.MapTransformation.NESTED).get('testKey').get('conventionTest') == 'key'
    }

    void "test inner properties"() {
        given:
            def values = new HashMap()
            values.put('foo[0].bar[0]', 'foo0Bar0')
            values.put('foo[0].bar[1]', 'foo0Bar1')
            values.put('foo[0].bar[3]', 'foo0Bar2')
            values.put('foo[1].bar[abx]', 'foo1Bar0')
            values.put('foo[1].bar[xyz]', 'foo1Bar1')
            values.put('custom[0][0][key][4]', 'ohh')
            values.put('custom[0][0][key][5]', 'ehh')
            values.put('custom[0][0][key2]', 'xyz')
            values.put('micronaut.security.intercept-url-map[0].access[0]', '/some-path')
            values.put('micronaut.security.interceptUrlMap[0].access[1]', '/some-path-x')

            PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                    PropertySource.of("test", values)
            )
        when:
            def foos = resolver.getProperty("foo", List).get()
            def custom = resolver.getProperty("custom", List).get()
            def micronaut = resolver.getProperty("micronaut", Map).get()
        then:
            foos.size() == 2
            foos[0].bar.size() == 4
            foos[0].bar[0] == 'foo0Bar0'
            foos[0].bar[2] == null
            foos[1].bar.size() == 2
            foos[1].bar['abx'] == 'foo1Bar0'
            foos[1].bar['xyz'] == 'foo1Bar1'
            custom.size() == 1
            custom[0].size() == 1
            custom[0][0].size() == 2
            custom[0][0]['key'].size() == 6
            custom[0][0]['key'][4] == 'ohh'
            custom[0][0]['key'][5] == 'ehh'
            custom[0][0]['key2'] == 'xyz'
            micronaut['security']['intercept-url-map'][0]['access'][0] == '/some-path'
            micronaut['security']['intercept-url-map'][0]['access'][1] == '/some-path-x'
    }
    
    void "test map and list values are collapsed"() {
        given:
        def values = new HashMap()
        values.put("foo", [[bar: ['foo0Bar0', 'foo0Bar1', null, 'foo0Bar2']], [bar: [abx: 'foo1Bar0', xyz: 'foo1Bar1']]])
        values.put("custom", [[[key: [null, null, null, null, 'ohh', 'ehh'], key2: 'xyz']]])
        values.put("micronaut.security.intercept-url-map", [[access:['/some-path']]])
        values.put("micronaut.security.interceptUrlMap", [[access:[null, '/some-path-x']]])

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )
        
        expect:
        resolver.getRequiredProperty('foo[0].bar[0]', String) == "foo0Bar0"
        resolver.getRequiredProperty('foo[0].bar[1]', String) == 'foo0Bar1'
        !resolver.containsProperty('foo[0].bar[2]')
        resolver.getRequiredProperty('foo[0].bar[3]', String) == 'foo0Bar2'
        resolver.getRequiredProperty('foo[1].bar.abx', String) == 'foo1Bar0'
        resolver.getRequiredProperty('foo[1].bar.xyz', String) == 'foo1Bar1'
        !resolver.containsProperty('custom[0][0].key[0]')
        !resolver.containsProperty('custom[0][0].key[1]')
        !resolver.containsProperty('custom[0][0].key[2]')
        !resolver.containsProperty('custom[0][0].key[3]')
        resolver.getRequiredProperty('custom[0][0].key[4]', String) == 'ohh'
        resolver.getRequiredProperty('custom[0][0].key[5]', String) == 'ehh'
        resolver.getRequiredProperty('custom[0][0].key2', String) == 'xyz'
        resolver.getRequiredProperty('micronaut.security.intercept-url-map[0].access[0]', String) == '/some-path'
        resolver.getRequiredProperty('micronaut.security.intercept-url-map[0].access[1]', String) == '/some-path-x'
    }

}
