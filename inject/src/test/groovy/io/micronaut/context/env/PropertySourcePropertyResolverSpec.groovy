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

import com.github.stefanbirkner.systemlambda.SystemLambda
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.ConfigurationException
import org.jspecify.annotations.NonNull
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.convert.MutableConversionService
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import io.micronaut.core.value.MapPropertyResolver
import io.micronaut.core.value.PropertyCatalog
import io.micronaut.core.value.PropertyResolver
import io.micronaut.core.value.ValueException
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class PropertySourcePropertyResolverSpec extends Specification {

    void "placeholder lookup does not retain a missing value observed during catalog reinitialization"() {
        given:
        PropertySource source = PropertySource.of("test", ["test.value": "resolved"])
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(source)
        DefaultPropertyPlaceholderResolver placeholders = new DefaultPropertyPlaceholderResolver(resolver, ConversionService.SHARED)
        CountDownLatch catalogCleared = new CountDownLatch(1)
        CountDownLatch lookupCompleted = new CountDownLatch(1)
        Thread refreshThread = Thread.start {
            resolver.reset()
            catalogCleared.countDown()
            assert lookupCompleted.await(10, TimeUnit.SECONDS)
            resolver.addPropertySource(source)
        }

        when: "a request observes the empty catalog between reset and its reinitialization"
        ConfigurationException missingDuringRefresh
        try {
            assert catalogCleared.await(10, TimeUnit.SECONDS)
            try {
                placeholders.resolveRequiredPlaceholders('${test.value}')
                throw new AssertionError("Expected placeholder resolution to fail while the catalog is empty")
            } catch (ConfigurationException e) {
                missingDuringRefresh = e
            }
        } finally {
            lookupCompleted.countDown()
            refreshThread.join(10_000)
        }
        then: "the negative lookup must not survive once the catalog has been rebuilt"
        !refreshThread.alive
        missingDuringRefresh.message == 'Could not resolve placeholder ${test.value}'
        placeholders.resolveRequiredPlaceholders('${test.value}') == "resolved"
    }

    @Unroll
    void "test resolve property #property matches for pattern #pattern"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [(property): "whatever"], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE)
        )

        expect:
        resolver.getPropertyPathMatches(pattern) == expected

        where:
        property                            | pattern                     | expected
        'twitter.oauth2.access.token'       | "twitter.*.access.token"    | [['oauth2']] as Set
        'twitter.oauth2.access.token.stuff' | "twitter.*.access.*.stuff"  | [['oauth2', 'token']] as Set
        'twitter.oauth2.access.token.stuff' | "twitter.*.access.*"        | [['oauth2']] as Set
        'twitter.oauth2.access[0].stuff'    | "twitter.*.access[*].stuff" | [['oauth2', '0']] as Set
    }

    void "test resolve property entries"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [DATASOURCE_DEFAULT_URL: 'xxx', DATASOURCE_OTHER_URL: 'xxx'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE),
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

    void "test resolve property entries for env key with hyphenated prefix"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [MICRONAUT_OBJECT_STORAGE_ORACLE_CLOUD_DEFAULT_BUCKET: 'bucket'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE)
        )
        Set<String> generatedEntries = resolver.getPropertyEntries("micronaut.object-storage.oracle-cloud", PropertyCatalog.GENERATED)

        expect:
        generatedEntries.contains('default')
        resolver.getPropertyEntries("micronaut.object-storage.oracle-cloud").isEmpty()
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
                'bar'    : 30,
                'arr'    : new byte[] {1, 2, 3, 0x80},
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [(property): value] + values)
        )

        expect:
        SystemLambda.withEnvironmentVariable("FOO_BAR", "foo bar")
                .and("FOO_BAR_1", "foo bar 1")
                .execute(() -> {
                    resolver.getProperty(key, Object).isPresent() &&
                            resolver.getProperty(key, type).get() == expected &&
                            resolver.containsProperty(key)
                })

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
        'my.property' | '${foo.bar}'                                         | 'my.property' | List    | ['10']
        'my.property' | '${foo.bar}'                                         | 'my.property' | Integer | 10
        'my.property' | '${FOO_BAR}'                                         | 'my.property' | String  | 'foo bar'
        'my.property' | '${FOO_BAR_1}'                                       | 'my.property' | String  | 'foo bar 1'
        'my.property' | 'bolt://${NEO4J_HOST:localhost}:${NEO4J_PORT:32781}' | 'my.property' | String  | 'bolt://localhost:32781'
        'my.property' | '${bar}'                                             | 'my.property' | Integer | 30
        'my.property' | '${arr}'                                             | 'my.property' | byte[]  | new byte[] {1, 2, 3, 0x80}
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

    void "map sub-properties use a Map conversion when the requested type is not a Map"() {
        given:
        MutableConversionService conversionService = MutableConversionService.create()
        conversionService.addConverter(Map, MapConvertible, new Function<Map, MapConvertible>() {
            @Override
            MapConvertible apply(Map value) {
                return new MapConvertible(value)
            }
        })
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(conversionService, false)
        resolver.addPropertySource(PropertySource.of("test", ["target.value": "converted"]))

        expect:
        resolver.getProperty("target", MapConvertible).get().values == [value: "converted"]
    }

    void "empty map sub-properties are only returned for Map targets"() {
        given:
        MutableConversionService conversionService = MutableConversionService.create()
        conversionService.addConverter(Map, MapConvertible, new Function<Map, MapConvertible>() {
            @Override
            MapConvertible apply(Map value) {
                return new MapConvertible(value)
            }
        })
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(conversionService, false)
        resolver.addPropertySource(PropertySource.of("test", [target: null]))

        expect:
        resolver.getProperty("target", Map).get().isEmpty()
        !resolver.getProperty("target", MapConvertible).isPresent()
    }

    void "sub-properties are not resolved for types only reachable through a generic Object converter"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(ConversionService.SHARED, false)
        resolver.addPropertySource(PropertySource.of("test", ["target.value": "one"]))

        expect: "types such as List or String have an Object -> X converter, but must not consume the sub-map"
        !resolver.getProperty("target", List).isPresent()
        !resolver.getProperty("target", String[]).isPresent()
        !resolver.getProperty("target", MapConvertible).isPresent()

        and: "the leaf property is still resolvable"
        resolver.getProperty("target.value", String).get() == "one"
    }

    void "a PropertyResolver target keeps precedence over a registered Map conversion"() {
        given:
        MutableConversionService conversionService = MutableConversionService.create()
        conversionService.addConverter(Map, MapPropertyResolver, new Function<Map, MapPropertyResolver>() {
            @Override
            MapPropertyResolver apply(Map value) {
                throw new IllegalStateException("Should not be used")
            }
        })
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(conversionService, false)
        resolver.addPropertySource(PropertySource.of("test", ["target.value": "one"]))

        expect:
        resolver.getProperty("target", MapPropertyResolver).get().getProperty("value", String).get() == "one"
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

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a random value stays the same when another property source is processed"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.id': '${random.uuid}', 'app.url': 'http://localhost:${random.port}/'])
        )
        String id = resolver.getProperty('app.id', String).get()
        String url = resolver.getProperty('app.url', String).get()

        when:
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then:
        resolver.getProperty('app.id', String).get() == id
        resolver.getProperty('app.url', String).get() == url

        when: "a property source replaces the random expression"
        resolver.addPropertySource(PropertySource.of('c', ['app.id': '${random.shortuuid}']))

        then: "the new expression is resolved"
        resolver.getProperty('app.id', String).get().length() == 10
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a property source that reads a random value while it is processed gets the value later lookups return"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.id': '${random.uuid}'])
        )
        resolver.addPropertySource(new PropertySource() {
            @Override
            String getName() { 'derived' }

            @Override
            Object get(String key) {
                key == 'derived.id' ? resolver.getProperty('app.id', String).orElse(null) : null
            }

            @Override
            Iterator<String> iterator() { ['derived.id'].iterator() }
        })

        expect:
        resolver.getProperty('derived.id', String).get() == resolver.getProperty('app.id', String).get()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a lookup overtaken by a property source that replaces a random text does not replace what a later lookup remembered"() {
        given: "a text that stops the lookup thread once it has read it"
        AtomicReference<Thread> lookupThread = new AtomicReference<>()
        CountDownLatch read = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        CharSequence text = stoppingText('${random.shortuuid}', lookupThread, read, release)
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of('a', ['app.id': text]))
        AtomicReference<String> overtaken = new AtomicReference<>()
        Thread thread = new Thread({ overtaken.set(resolver.getProperty('app.id', String).orElse(null)) })
        lookupThread.set(thread)
        thread.start()
        assert read.await(10, TimeUnit.SECONDS)

        when: "a property source replaces the text, and it is looked up, while the first lookup is stopped"
        resolver.addPropertySource(PropertySource.of('b', ['app.id': '${random.uuid}']))
        String id = resolver.getProperty('app.id', String).get()
        release.countDown()
        thread.join(10_000)

        then: "the first lookup finished with a value of the text it read"
        !thread.alive
        overtaken.get().length() == 10

        and: "every later lookup gets the value of the replacement"
        resolver.getProperty('app.id', UUID).get().toString() == id
        resolver.getProperty('app.id', String).get() == id
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a lookup started while a property source replaces a random text does not replace what the property source read"() {
        given: "a text that stops the lookup thread once it has read it"
        AtomicReference<Thread> lookupThread = new AtomicReference<>()
        CountDownLatch read = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        CharSequence text = stoppingText('${random.shortuuid}', lookupThread, read, release)
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of('a', ['app.id': text]))
        AtomicReference<String> overtaken = new AtomicReference<>()
        Thread thread = new Thread({ overtaken.set(resolver.getProperty('app.id', String).orElse(null)) })
        lookupThread.set(thread)

        when: "a property source replaces the text and derives a value from the replacement, while the lookup that read the text before is stopped"
        resolver.addPropertySource(new PropertySource() {
            @Override
            String getName() { 'b' }

            @Override
            Object get(String key) {
                if (key == 'app.id') {
                    return '${random.uuid}'
                }
                String id = resolver.getProperty('app.id', String).orElse(null)
                // the stopped lookup finishes while the property source is still processed
                release.countDown()
                thread.join(10_000)
                return id
            }

            @Override
            Iterator<String> iterator() {
                // the lookup starts once the property source is being processed
                thread.start()
                assert read.await(10, TimeUnit.SECONDS)
                ['app.id', 'derived.id'].iterator()
            }
        })

        then: "the stopped lookup finished with a value of the text it read"
        !thread.alive
        overtaken.get().length() == 10

        and: "every later lookup gets the value the property source read"
        resolver.getProperty('app.id', String).get() == resolver.getProperty('derived.id', String).get()
        resolver.getProperty('app.id', UUID).get().toString() == resolver.getProperty('derived.id', String).get()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a lookup overtaken by an unrelated property source gets the random value later lookups return"() {
        given: "a text that stops the lookup thread once it has read it"
        AtomicReference<Thread> lookupThread = new AtomicReference<>()
        CountDownLatch read = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        CharSequence text = stoppingText('${random.shortuuid}', lookupThread, read, release)
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of('a', ['app.id': text]))
        AtomicReference<String> overtaken = new AtomicReference<>()
        Thread thread = new Thread({ overtaken.set(resolver.getProperty('app.id', String).orElse(null)) })
        lookupThread.set(thread)
        thread.start()
        assert read.await(10, TimeUnit.SECONDS)

        when: "an unrelated property source is processed while the first lookup of the text is stopped"
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))
        release.countDown()
        thread.join(10_000)

        then: "later lookups get the value the first lookup got"
        !thread.alive
        resolver.getProperty('app.id', String).get() == overtaken.get()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "the random values of a text that also names a property stay the same when another property source is processed"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', [
                        'app.host': 'localhost',
                        'app.url' : 'http://${app.host}:${random.port}/',
                        'app.name': '${app.host}-${random.shortuuid}-${random.shortuuid}'
                ])
        )
        String url = resolver.getProperty('app.url', String).get()
        String name = resolver.getProperty('app.name', String).get()

        when:
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then:
        resolver.getProperty('app.url', String).get() == url
        resolver.getProperty('app.name', String).get() == name

        when: "a property source changes the property the texts name"
        resolver.addPropertySource(PropertySource.of('c', ['app.host': 'example.com']))

        then: "the property is resolved again, and the random values stay"
        resolver.getProperty('app.url', String).get() == url.replace('localhost', 'example.com')
        resolver.getProperty('app.name', String).get() == name.replace('localhost', 'example.com')

        when: "a property source replaces a text with another one"
        resolver.addPropertySource(PropertySource.of('d', ['app.name': '${app.host}+${random.shortuuid}']))
        String renamed = resolver.getProperty('app.name', String).get()

        then: "the random value of the new text is drawn"
        renamed.startsWith('example.com+')
        !name.contains(renamed.substring('example.com+'.length()))
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "the random values in a list stay the same when another property source is processed"() {
        given:
        List<String> declaredIds = ['${random.uuid}', 'id-${random.shortuuid}', 'fixed']
        List<Map<String, String>> declaredServers = [['url': 'http://${app.host}:${random.port}/']]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', [
                        'app.host'   : 'localhost',
                        'app.ids'    : declaredIds,
                        'app.servers': declaredServers
                ])
        )
        List ids = resolver.getProperty('app.ids', List).get()
        List servers = resolver.getProperty('app.servers', List).get()

        when:
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then:
        resolver.getProperty('app.ids', List).get() == ids
        resolver.getProperty('app.servers', List).get() == servers
        ids[2] == 'fixed'

        and: "the declared list and map are left as they were"
        declaredIds == ['${random.uuid}', 'id-${random.shortuuid}', 'fixed']
        declaredServers == [['url': 'http://${app.host}:${random.port}/']]

        and: "a text that is one random expression keeps the type of its value"
        ids[0] instanceof UUID

        when: "a property source changes the property a text in the list names"
        resolver.addPropertySource(PropertySource.of('c', ['app.host': 'example.com']))

        then: "the property is resolved again, and the random value stays"
        resolver.getProperty('app.servers', List).get() == [['url': servers[0].url.replace('localhost', 'example.com')]]

        when: "a property source replaces one element of the list"
        resolver.addPropertySource(PropertySource.of('d', ['app.ids[1]': 'other-${random.shortuuid}']))
        List replaced = resolver.getProperty('app.ids', List).get()

        then: "that element gets a new random value, and the others keep theirs"
        replaced[0] == ids[0]
        replaced[1].startsWith('other-')
        replaced[2] == 'fixed'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a lookup of a list that a property source adds to meanwhile gets the list it read: #first"() {
        given: "a list whose first text stops the lookup thread once it has read it"
        AtomicReference<Thread> lookupThread = new AtomicReference<>()
        CountDownLatch read = new CountDownLatch(1)
        CountDownLatch release = new CountDownLatch(1)
        List<Object> ids = new ArrayList<>([stoppingText(first, lookupThread, read, release), 'b'])
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(PropertySource.of('a', ['app.ids': ids]))
        AtomicReference<Object> found = new AtomicReference<>()
        AtomicReference<Throwable> failure = new AtomicReference<>()
        Thread thread = new Thread({
            try {
                found.set(resolver.getProperty('app.ids', List).orElse(null))
            } catch (Throwable e) {
                failure.set(e)
            }
        })
        lookupThread.set(thread)
        thread.start()
        assert read.await(10, TimeUnit.SECONDS)

        when: "a property source adds an element to the list while the lookup is stopped"
        resolver.addPropertySource(PropertySource.of('b', ['app.ids[3]': 'd']))
        release.countDown()
        thread.join(10_000)

        then:
        !thread.alive
        failure.get() == null
        found.get().size() == 2

        where:
        first << ['${random.uuid}', 'a']
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "the random values of a map in a list are kept by key, for keys that read the same too"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', [
                        'app.servers': [[
                                (1)    : '${random.uuid}',
                                '1'    : '${random.uuid}',
                                'id'   : ['${random.shortuuid}'],
                                'id[0]': 'x-${random.shortuuid}'
                        ]]
                ])
        )
        Map server = resolver.getProperty('app.servers', List).get()[0]

        when:
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then: "each key has its own random value, and keeps it"
        server[1] != server['1']
        resolver.getProperty('app.servers', List).get()[0] == server
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a list in a list is returned as it was declared, as its placeholders are not resolved"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', [
                        'app.host'   : 'localhost',
                        'app.matrix' : [['${random.port}', 'http://${app.host}:${random.port}/']],
                        'app.servers': [['ports': [['${random.port}']]]]
                ])
        )

        expect:
        resolver.getProperty('app.matrix', List).get() == [['${random.port}', 'http://${app.host}:${random.port}/']]
        resolver.getProperty('app.servers', List).get() == [['ports': [['${random.port}']]]]
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "the random expressions of a text are found as its placeholders are resolved: #text"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.host': 'localhost', 'app.value': text])
        )
        String value = resolver.getProperty('app.value', String).get()

        when:
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then:
        resolver.getProperty('app.value', String).get() == value

        where:
        text << ['${ RANDOM.UUID }', '${random.int:5}-${app.host}', 'x-${ random.shortuuid }-${app.host}']
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a random expression that a placeholder falls back to keeps its value, and the property named first still wins: #text"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.host': 'localhost', 'app.value': text])
        )
        String value = resolver.getProperty('app.value', String).get()

        when:
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then:
        resolver.getProperty('app.value', String).get() == value

        when: "the property the placeholder names first is defined"
        resolver.addPropertySource(PropertySource.of('c', ['app.port': '9000']))

        then:
        resolver.getProperty('app.value', String).get() == expected

        where:
        text                                               | expected
        '${app.port:random.uuid:8080}'                     | '9000'
        'http://${app.host}:${app.port:random.port:8080}/' | 'http://localhost:9000/'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a random fallback is not resolved while the property named first is set: #text"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.port': '9000', 'app.value': text])
        )

        expect:
        resolver.getProperty('app.value', String).get() == '9000'

        where:
        text << ['${app.port:random.int(0):8080}', '${app.port:random.bogus:8080}']
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a random fallback keeps the type of its value"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', [
                        'app.value': '${missing:random.uuid:default}',
                        'app.list' : ['${missing:random.int[1,2]:default}']
                ])
        )

        when:
        Object value = resolver.getProperty('app.value', Object).get()

        then:
        value instanceof UUID
        resolver.getProperty('app.list', Object).get() == [1]

        when: "a property source is processed"
        resolver.addPropertySource(PropertySource.of('b', ['other.value': 'x']))

        then: "the same value, of the same type"
        resolver.getProperty('app.value', Object).get() == value
        resolver.getProperty('app.list', Object).get() == [1]
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a text with a missing property before an invalid random expression reports the property, as before"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.value': '${app.missing}-${random.bogus}'])
        )

        when:
        resolver.getProperty('app.value', String)

        then:
        ConfigurationException e = thrown()
        e.message.contains('app.missing')
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a text with a random expression and an incomplete placeholder still fails to resolve"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of('a', ['app.value': '${random.uuid}-${app.host'])
        )

        when:
        resolver.getProperty('app.value', String)

        then:
        ConfigurationException e = thrown()
        e.message.contains('${random.uuid}-${app.host')
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/13913")
    void "a lookup made while the random values are forgotten gets the value every later lookup returns"() {
        given: "a lookup made right after the caches are reset"
        AtomicBoolean lookUpOnReset = new AtomicBoolean()
        AtomicReference<String> duringReset = new AtomicReference<>()
        PropertySourcePropertyResolver resolver
        resolver = new PropertySourcePropertyResolver() {
            @Override
            protected void resetCaches() {
                super.resetCaches()
                if (lookUpOnReset.compareAndSet(true, false)) {
                    duringReset.set(resolver.getProperty('app.id', String).get())
                }
            }
        }
        resolver.addPropertySource(PropertySource.of('a', ['app.id': '${random.uuid}']))
        String before = resolver.getProperty('app.id', String).get()

        when: "the random values are forgotten, as a property source removed from a running environment does"
        lookUpOnReset.set(true)
        resolver.resetCachesAndRememberedValues()

        then: "the lookup made meanwhile got the value that later lookups of every type return"
        duringReset.get() != null
        resolver.getProperty('app.id', String).get() == duringReset.get()
        resolver.getProperty('app.id', UUID).get().toString() == duringReset.get()

        and: "it is another value than before"
        duringReset.get() != before
    }

    /**
     * A text that stops the given thread the first time the thread reads it, until it is released.
     */
    private static CharSequence stoppingText(String value, AtomicReference<Thread> thread, CountDownLatch read, CountDownLatch release) {
        new CharSequence() {
            @Override
            int length() { value.length() }

            @Override
            char charAt(int index) { value.charAt(index) }

            @Override
            CharSequence subSequence(int start, int end) { value.subSequence(start, end) }

            @Override
            String toString() {
                if (Thread.currentThread() == thread.get() && read.count > 0) {
                    read.countDown()
                    release.await(10, TimeUnit.SECONDS)
                }
                value
            }
        }
    }

    void "test random integer placeholders in range for properties"() {
        given:
        def values = [
                'random.integer_lower'                        : '${random.integer(10)}',
                'random.integer_lower-negative'               : '${random.integer(-10)}',
                'random.integer_lower_upper'                  : '${random.integer[5,10]}',
                'random.integer_lower-negative_upper'         : '${random.integer[-5,10]}',
                'random.integer_lower-negative_upper-negative': '${random.integer[-10,-5]}',
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty('random.integer_lower', String).isPresent()
        resolver.getProperty('random.integer_lower', Integer).get() >= 0
        resolver.getProperty('random.integer_lower', Integer).get() < 10

        and:
        resolver.getProperty('random.integer_lower-negative', String).isPresent()
        resolver.getProperty('random.integer_lower-negative', Integer).get() <= 0
        resolver.getProperty('random.integer_lower-negative', Integer).get() > -10

        and:
        resolver.getProperty('random.integer_lower_upper', String).isPresent()
        resolver.getProperty('random.integer_lower_upper', Integer).get() < 10
        resolver.getProperty('random.integer_lower_upper', Integer).get() >= 5

        and:
        resolver.getProperty('random.integer_lower-negative_upper', String).isPresent()
        resolver.getProperty('random.integer_lower-negative_upper', Integer).get() < 10
        resolver.getProperty('random.integer_lower-negative_upper', Integer).get() >= -5

        and:
        resolver.getProperty('random.integer_lower-negative_upper-negative', String).isPresent()
        resolver.getProperty('random.integer_lower-negative_upper-negative', Integer).get() < -5
        resolver.getProperty('random.integer_lower-negative_upper-negative', Integer).get() >= -10
    }

    void "test random long placeholders in range for properties"() {
        given:
        def values = [
                'random.long_lower'                        : '${random.long(10)}',
                'random.long_lower-negative'               : '${random.long(-10)}',
                'random.long_lower_upper'                  : '${random.long[5,10]}',
                'random.long_lower-negative_upper'         : '${random.long[-5,10]}',
                'random.long_lower-negative_upper-negative': '${random.long[-10,-5]}',
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty('random.long_lower', String).isPresent()
        resolver.getProperty('random.long_lower', Long).get() >= 0
        resolver.getProperty('random.long_lower', Long).get() < 10

        and:
        resolver.getProperty('random.long_lower-negative', String).isPresent()
        resolver.getProperty('random.long_lower-negative', Long).get() <= 0
        resolver.getProperty('random.long_lower-negative', Long).get() > -10

        and:
        resolver.getProperty('random.long_lower_upper', String).isPresent()
        resolver.getProperty('random.long_lower_upper', Long).get() < 10
        resolver.getProperty('random.long_lower_upper', Long).get() >= 5

        and:
        resolver.getProperty('random.long_lower-negative_upper', String).isPresent()
        resolver.getProperty('random.long_lower-negative_upper', Long).get() < 10
        resolver.getProperty('random.long_lower-negative_upper', Long).get() >= -5

        and:
        resolver.getProperty('random.long_lower-negative_upper-negative', String).isPresent()
        resolver.getProperty('random.long_lower-negative_upper-negative', Long).get() < -5
        resolver.getProperty('random.long_lower-negative_upper-negative', Long).get() >= -10
    }

    void "test random float placeholders in range for properties"() {
        given:
        def values = [
                'random.float_lower'                        : '${random.float(10.5)}',
                'random.float_lower-negative'               : '${random.float(-10.5)}',
                'random.float_lower_upper'                  : '${random.float[5.5,10.5]}',
                'random.float_lower-negative_upper'         : '${random.float[-5.5,10.5]}',
                'random.float_lower-negative_upper-negative': '${random.float[-10.5,-5.5]}',
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperty('random.float_lower', String).isPresent()
        resolver.getProperty('random.float_lower', Float).get() >= 0.0
        resolver.getProperty('random.float_lower', Float).get() < 10.5

        and:
        resolver.getProperty('random.float_lower-negative', String).isPresent()
        resolver.getProperty('random.float_lower-negative', Float).get() <= 0.0
        resolver.getProperty('random.float_lower-negative', Float).get() > -10.5

        and:
        resolver.getProperty('random.float_lower_upper', String).isPresent()
        resolver.getProperty('random.float_lower_upper', Float).get() < 10.5
        resolver.getProperty('random.float_lower_upper', Float).get() >= 5.5

        and:
        resolver.getProperty('random.float_lower-negative_upper', String).isPresent()
        resolver.getProperty('random.float_lower-negative_upper', Float).get() < 10.5
        resolver.getProperty('random.float_lower-negative_upper', Float).get() >= -5.5

        and:
        resolver.getProperty('random.float_lower-negative_upper-negative', String).isPresent()
        resolver.getProperty('random.float_lower-negative_upper-negative', Float).get() < -5.5
        resolver.getProperty('random.float_lower-negative_upper-negative', Float).get() >= -10.5
    }

    void "test invalid random Integer range"() {
        when:
        def values = [
                'random.integer': '${random.integer(9999999999)}'
        ]
        new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        ).getProperty('random.integer', String).isPresent()

        then:
        def ex = thrown(ValueException)
        ex.message == 'Invalid range: `9999999999` found for type Integer for expression: random.integer(9999999999)'
        ex.cause != null
        ex.cause instanceof NumberFormatException
    }

    void "test invalid random Long range"() {
        when:
        def values = [
                'random.long': '${random.long(9999999999999999999)}'
        ]
        new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        ).getProperty('random.long', String).isPresent()

        then:
        def ex = thrown(ValueException)
        ex.message == 'Invalid range: `9999999999999999999` found for type Long for expression: random.long(9999999999999999999)'
        ex.cause != null
        ex.cause instanceof NumberFormatException
    }

    void "test invalid random placeholders for properties"() {
        when:
        def values = [
                'random.invalid': '${random.invalid}'
        ]
        new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        ).getProperty('random.invalid', String).isPresent()

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
                'foo.bar'                 : 'two',
                'my.property.one'         : 'one',
                'my.property.two'         : '${foo.bar}',
                'my.property.three'       : 'three',
                'test-key.convention-test': 'key',
                'FranKen_Ste-in.property' : 'Victor'
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
        resolver.getAllProperties(StringConvention.CAMEL_CASE_CAPITALIZED, MapFormat.MapTransformation.FLAT).get('FranKenSteIn.property') == 'Victor'
        resolver.getAllProperties(StringConvention.CAMEL_CASE, MapFormat.MapTransformation.FLAT).get('franKenSteIn.property') == 'Victor'
        resolver.getAllProperties(StringConvention.HYPHENATED, MapFormat.MapTransformation.FLAT).get('fran-ken-ste-in.property') == 'Victor'
        resolver.getAllProperties(StringConvention.RAW, MapFormat.MapTransformation.FLAT).get('FranKen_Ste-in.property') == 'Victor'
        resolver.getAllProperties(StringConvention.UNDER_SCORE_SEPARATED, MapFormat.MapTransformation.FLAT).get('FRAN_KEN_STE_IN_PROPERTY') == 'Victor'
        resolver.getAllProperties(StringConvention.UNDER_SCORE_SEPARATED_LOWER_CASE, MapFormat.MapTransformation.FLAT).get('fran_ken_ste_in.property') == 'Victor'
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

    void "test indexed properties expand into the RAW catalog with the map key and base name as written"() {
        given:
        def values = new HashMap()
        values.put('foo.items[0].SOME_KEY', 'x')
        values.put('foo.myItems[0].K', 'v')

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperties("foo", StringConvention.RAW) == [
                'items[0].SOME_KEY': 'x',
                'items'             : [['SOME_KEY': 'x']],
                'myItems[0].K'      : 'v',
                'myItems'           : [['K': 'v']],
        ]
    }

    void "test indexed properties still hyphenate into the GENERATED catalog"() {
        given:
        def values = new HashMap()
        values.put('foo.items[0].SOME_KEY', 'x')
        values.put('foo.myItems[0].K', 'v')

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getProperties("foo") == [
                'items[0].some-key': 'x',
                'items'            : [['some-key': 'x']],
                'my-items[0].k'    : 'v',
                'my-items'         : [['k': 'v']],
        ]
    }

    void "test indexed property expansion is skipped for the ENVIRONMENT_VARIABLE convention"() {
        given: "the source key as EnvironmentPropertySource.getEnv would rewrite A_0__B into"
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", ['A[0]_B': 'x'], PropertySource.PropertyConvention.ENVIRONMENT_VARIABLE)
        )

        expect: "the dotted property still resolves"
        resolver.getRequiredProperty('a[0].b', String) == 'x'

        and: "no aggregate entry is created in RAW for the rewritten key; checked directly because a top-level key has no dotted prefix for getProperties"
        resolver.resolveEntriesForKey('A', false, PropertyCatalog.RAW)?.get('A') == null
    }

    void "test nested and bracketed indexed properties expand into the RAW catalog with map keys as written"() {
        given: "top-level indexed keys, whose RAW aggregate is fetched from the catalog directly since getProperties only returns entries under a dotted prefix"
        def values = new HashMap()
        values.put('custom[0][0][KEY][4]', 'ohh')
        values.put('custom[0][0][KEY][5]', 'ehh')
        values.put('foo[1].bar[abx]', 'foo1Bar0')
        values.put('a[0].b[1].C', 'deep')

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        when:
        def rawCustom = resolver.resolveEntriesForKey('custom', false, PropertyCatalog.RAW).get('custom').value()
        def rawFoo = resolver.resolveEntriesForKey('foo', false, PropertyCatalog.RAW).get('foo').value()
        def rawA = resolver.resolveEntriesForKey('a', false, PropertyCatalog.RAW).get('a').value()

        then: "RAW preserves the spelling as written at every map-key position"
        rawCustom[0][0]['KEY'][4] == 'ohh'
        rawCustom[0][0]['KEY'][5] == 'ehh'
        rawFoo[1]['bar']['abx'] == 'foo1Bar0'
        rawA[0]['b'][1]['C'] == 'deep'

        and: "GENERATED still holds the hyphenated map keys NameUtils.hyphenate produces"
        resolver.getProperty("custom", List).get()[0][0]['-key'][4] == 'ohh'
        resolver.getProperty("foo", List).get()[1]['bar']['abx'] == 'foo1Bar0'
        resolver.getProperty("a", List).get()[0]['b'][1]['c'] == 'deep'
    }

    void "test indexed property expansion leaves the NORMALIZED catalog unchanged"() {
        given:
        def values = new HashMap()
        values.put('foo.items[0].SOME_KEY', 'x')

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect: "NORMALIZED still holds only the scalar under the base name, with no aggregate added"
        resolver.getPropertyEntries("foo", PropertyCatalog.NORMALIZED) == ['items'] as Set

        and:
        def normalized = resolver.resolveEntriesForKey('foo', false, PropertyCatalog.NORMALIZED)
        normalized.size() == 1
        normalized['foo.items'].value() == 'x'
    }

    void "test a structured base value plus an indexed key targeting the same base does not corrupt GENERATED"() {
        given: "a structured value as a YAML source supplies it, plus an indexed key targeting a new index of the same base; LinkedHashMap keeps the structured value first"
        def values = new LinkedHashMap()
        values.put('t.env', [[A: 1]])
        values.put('t.env[1].B', 2)

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect: "GENERATED is unchanged: the RAW expansion must not mutate the list or map instance GENERATED serves"
        resolver.getProperties("t")['env'] == [[A: 1], [b: 2]]

        and: "RAW carries the spelling as written for the indexed key"
        resolver.resolveEntriesForKey('t.env', false, PropertyCatalog.RAW).get('t.env').value()[1]['B'] == 2
    }

    void "test a structured base value plus an indexed key leaves no hyphenated key in RAW"() {
        given: "a structured value, then an indexed key adding a new index to it; GENERATED expands into the structured value in place, writing the hyphenated key b"
        def values = new LinkedHashMap()
        values.put('t.env', [[A: 1]])
        values.put('t.env[1].B', 2)

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect: "RAW shows only what was written; it must not also carry GENERATED's b, which it would if RAW shared the structured value GENERATED expands into"
        resolver.getProperties("t", StringConvention.RAW)['env'] == [[A: 1], [B: 2]]

        and: "GENERATED still hyphenates"
        resolver.getProperties("t")['env'] == [[A: 1], [b: 2]]
    }

    void "test an indexed key, then a bare structured key re-aliasing the base, then another indexed key does not corrupt GENERATED"() {
        given: "two indexed keys privatize RAW's base value, then the bare structured key re-aliases RAW's base value to the same instance GENERATED serves, then a third indexed key must re-privatize before mutating"
        def values = new LinkedHashMap()
        values.put('t.env[0].A', 1)
        values.put('t.env[1].B', 2)
        values.put('t.env', [[A: 1], [B: 2]])
        values.put('t.env[2].C', 3)

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect: "GENERATED is unchanged: the RAW expansion must not mutate the list or map instance GENERATED serves"
        resolver.getProperties("t")['env'] == [[A: 1], [B: 2], [c: 3]]

        and: "RAW carries the spelling as written for the third indexed key"
        resolver.resolveEntriesForKey('t.env', false, PropertyCatalog.RAW).get('t.env').value()[2]['C'] == 3
    }

    void "test an indexed key, then a bare map re-aliasing an index, then a nested indexed key does not leak into GENERATED"() {
        given: "an indexed key establishes index 0, a bare map re-aliases index 1 to the source's own instance, then a nested indexed key targets that same index 1"
        def values = new LinkedHashMap()
        values.put('t.env[0].A', 1)
        values.put('t.env[1]', [B: 2])
        values.put('t.env[1].C', 3)

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect: "GENERATED is unchanged: the RAW expansion of the nested indexed key must not mutate the map instance GENERATED serves"
        resolver.getProperties("t")['env'] == [[a: 1], [B: 2, c: 3]]

        and: "RAW carries the nested indexed key's value verbatim"
        resolver.resolveEntriesForKey('t.env', false, PropertyCatalog.RAW).get('t.env').value()[1]['C'] == 3
    }

    void "test the RAW catalog shares the property source value when no indexed key targets it"() {
        given: "a structured value and no indexed key anywhere, as in a plain YAML config"
        def sourceList = [[A: 1], [B: 2]]
        def sourceMap = [C: 3]

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", ['t.env': sourceList, 't.other': sourceMap])
        )

        expect: "RAW stores them by reference; nothing may expand into them, so nothing is copied"
        resolver.resolveEntriesForKey('t.env', false, PropertyCatalog.RAW).get('t.env').value().is(sourceList)
        resolver.resolveEntriesForKey('t.other', false, PropertyCatalog.RAW).get('t.other').value().is(sourceMap)
    }

    void "test the RAW catalog copies a base only once however many indexed keys target it"() {
        given: "a structured base re-aliased by a bare key, then several indexed keys targeting it"
        def values = new LinkedHashMap()
        values.put('t.env', [[A: 1]])
        values.put('t.env[1].B', 2)
        values.put('t.env[2].C', 3)
        values.put('t.env[3].D', 4)

        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect: "the first indexed key privatizes the base; the rest expand into the copy in place"
        resolver.resolveEntriesForKey('t.env', false, PropertyCatalog.RAW).get('t.env').value() ==
                [[A: 1], [B: 2], [C: 3], [D: 4]]

        and: "GENERATED keeps its own hyphenated expansion, and the source value is untouched by RAW"
        resolver.getProperties("t")['env'] == [[A: 1], [b: 2], [c: 3], [d: 4]]
    }

    void "test map and list values are collapsed"() {
        given:
        def values = new HashMap()
        values.put("foo", [[bar: ['foo0Bar0', 'foo0Bar1', null, 'foo0Bar2']], [bar: [abx: 'foo1Bar0', xyz: 'foo1Bar1']]])
        values.put("custom", [[[key: [null, null, null, null, 'ohh', 'ehh'], key2: 'xyz']]])
        values.put("micronaut.security.intercept-url-map", [[access: ['/some-path']]])
        values.put("micronaut.security.interceptUrlMap", [[access: [null, '/some-path-x']]])

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

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/3811")
    void "test properties start with the letter A"() {
        given:
        def values = [
                'AAA': 'fonzie',
                'aaa': 'fonzie',
        ]
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", values)
        )

        expect:
        resolver.getRequiredProperty('AAA', String) == "fonzie"
        resolver.getRequiredProperty('aaa', String) == "fonzie"
    }

    void "test an inner list property is created"() {
        given:
        Map<String, Object> extra = ["stringval": "foo", "listval": ["item1", "item2"]];

        PropertySource external = MapPropertySource.of("external", ["extra": extra])

        when:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                external
        )

        then:
        resolver.containsProperty("extra.listval")
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10030")
    void "test resolving a property below an index in the middle of the name"() {
        given:
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(
                PropertySource.of("test", [
                        'outer.items[0].name'               : 'first',
                        'outer.items[0].nested-items[0].foo': 'a',
                        'outer.items[0].nested-items[1].foo': 'b',
                        'outer.items[1].nested-items[0].foo': 'c',
                        'outer.items[1].map[key].foo'       : 'd',
                        'outer.items[1].inner.list[0].foo'  : 'e',
                ])
        )

        expect:
        resolver.getProperty('outer.items[0].nested-items', List).get() == [[foo: 'a'], [foo: 'b']]
        resolver.getProperty('outer.items[1].nested-items', List).get() == [[foo: 'c']]
        resolver.getProperty('outer.items[0].nested-items[1]', Map).get() == [foo: 'b']
        resolver.getProperty('outer.items[0].nested-items[1].foo', String).get() == 'b'
        resolver.getProperty('outer.items[1].map[key].foo', String).get() == 'd'
        resolver.getProperty('outer.items[0].name', String).get() == 'first'
        !resolver.getProperty('outer.items[2].nested-items', List).isPresent()
        !resolver.getProperty('outer.items[0].missing', List).isPresent()
        !resolver.getProperty('outer.items[0].name.foo', String).isPresent()
        resolver.getProperty('outer.items[1].inner.list', List).get() == [[foo: 'e']]
        resolver.getProperty('outer.items[1].inner.list[0].foo', String).get() == 'e'
        resolver.containsProperties('outer.items[0].nested-items')
        resolver.containsProperties('outer.items[1].inner.list')
        !resolver.containsProperties('outer.items[0].missing')
    }

    void "test expression resolver"() {
        given:
        Map<String, Object> parameters = [foo: "bar"]
        PropertyResolver mapPropertyResolver = new MapPropertyResolver(parameters)
        DefaultPropertyPlaceholderResolver propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(mapPropertyResolver, ConversionService.SHARED);
        propertyPlaceholderResolver.@expressionResolvers = [new PropertyExpressionResolver() {
            @Override
            @NonNull
            <T> Optional<T> resolve(@NonNull PropertyResolver propertyResolver,
                                    @NonNull ConversionService conversionService,
                                    @NonNull String expression,
                                    @NonNull Class<T> requiredType) {
                assert requiredType == String.class
                assert conversionService
                if ("foobar" == expression) {
                    return Optional.of("ABC")
                }
                if ("xyz" == expression) {
                    return Optional.of("123")
                }
                if ("external" == expression) {
                    return propertyResolver.get("foo", requiredType)
                }
                return Optional.empty()
            }
        }]

        expect:
        Optional<String> resolved = propertyPlaceholderResolver.resolvePlaceholders(template)
        if (result) {
            assert resolved.isPresent()
            assert resolved.get() == result
        } else {
            assert !resolved.isPresent()
        }
        where:
        template             | result
        'Hello ${foo}!'      | "Hello bar!"
        'Hello ${foobar}!'   | "Hello ABC!"
        'Hello ${xyz}!'      | "Hello 123!"
        'Hello ${external}!' | "Hello bar!"
        'Hello ${lol}!'      | null
    }

    void "test expression resolver is closed"() {
        given:
        AtomicBoolean closed = new AtomicBoolean()
        Map<String, Object> parameters = [foo: "bar"]
        PropertyResolver mapPropertyResolver = new MapPropertyResolver(parameters)
        DefaultPropertyPlaceholderResolver propertyPlaceholderResolver = new DefaultPropertyPlaceholderResolver(mapPropertyResolver, ConversionService.SHARED);
        propertyPlaceholderResolver.@expressionResolvers = [new PropertyExpressionResolverAutoCloseable() {
            @Override
            @NonNull
            <T> Optional<T> resolve(@NonNull PropertyResolver propertyResolver,
                                    @NonNull ConversionService conversionService,
                                    @NonNull String expression,
                                    @NonNull Class<T> requiredType) {
                Optional.empty()
            }

            @Override
            void close() throws Exception {
                closed.set(true)
            }
        }]
        when:
        !closed.get()
        propertyPlaceholderResolver.close()
        then:
        closed.get()
    }

    void "test expression resolver services are loaded from configured classloader"() {
        given:
        java.nio.file.Path servicesRoot = java.nio.file.Files.createTempDirectory("property-expression-services")
        java.nio.file.Path serviceFile = servicesRoot.resolve("META-INF/services/io.micronaut.context.env.PropertyExpressionResolver")
        java.nio.file.Files.createDirectories(serviceFile.parent)
        java.nio.file.Files.writeString(serviceFile, TestExpressionResolver.name)
        URLClassLoader classLoader = new URLClassLoader([servicesRoot.toUri().toURL()] as URL[], getClass().classLoader)
        PropertySourcePropertyResolver resolver = new PropertySourcePropertyResolver(ConversionService.SHARED, true, classLoader)
        resolver.addPropertySource(PropertySource.of("test", [foo: '${service.loaded}']))

        expect:
        resolver.getProperty("foo", String).get() == "loaded"

        cleanup:
        classLoader.close()
        servicesRoot.toFile().deleteDir()
    }

    interface PropertyExpressionResolverAutoCloseable extends PropertyExpressionResolver, AutoCloseable {
    }

    static class TestExpressionResolver implements PropertyExpressionResolver {
        @Override
        @NonNull
        <T> Optional<T> resolve(@NonNull PropertyResolver propertyResolver,
                                @NonNull ConversionService conversionService,
                                @NonNull String expression,
                                @NonNull Class<T> requiredType) {
            if (expression == "service.loaded") {
                return conversionService.convert("loaded", requiredType)
            }
            Optional.empty()
        }
    }

    static class MapConvertible {
        final Map<String, Object> values

        MapConvertible(Map<String, Object> values) {
            this.values = values
        }
    }
}
