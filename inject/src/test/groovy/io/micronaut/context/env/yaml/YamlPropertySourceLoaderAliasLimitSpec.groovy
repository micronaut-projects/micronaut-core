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
package io.micronaut.context.env.yaml

import io.micronaut.context.env.CachedEnvironment
import io.micronaut.context.exceptions.ConfigurationException
import org.yaml.snakeyaml.error.YAMLException
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.environment.RestoreSystemProperties

import java.lang.reflect.Field
import java.util.function.UnaryOperator

@RestoreSystemProperties
class YamlPropertySourceLoaderAliasLimitSpec extends Specification {

    private UnaryOperator<String> previousGetenv

    void setup() {
        System.clearProperty(YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_PROPERTY)
        previousGetenv = getenvField().get(null) as UnaryOperator<String>
        setGetenv(null)
    }

    void cleanup() {
        setGetenv(previousGetenv)
    }

    void "a document exceeding the default alias limit is rejected"() {
        when:
        read(anchorHeavyYaml(60))

        then:
        def e = thrown(YAMLException)
        e.message.contains("max=50")
    }

    void "a document within the default alias limit loads"() {
        expect:
        read(anchorHeavyYaml(50)).get("items.i49.key") == "value"
    }

    void "the alias limit can be raised with a system property"() {
        given:
        System.setProperty(YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_PROPERTY, "100")

        when:
        def result = read(anchorHeavyYaml(60))

        then:
        result.get("items.i0.key") == "value"
        result.get("items.i59.key") == "value"
    }

    void "the alias limit can be raised with an environment variable"() {
        given:
        setGetenv({ String name -> name == YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_ENV ? "100" : null } as UnaryOperator<String>)

        expect:
        read(anchorHeavyYaml(60)).get("items.i59.key") == "value"
    }

    void "the system property takes precedence over the environment variable"() {
        given:
        System.setProperty(YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_PROPERTY, "10")
        setGetenv({ String name -> name == YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_ENV ? "100" : null } as UnaryOperator<String>)

        when:
        read(anchorHeavyYaml(20))

        then:
        def e = thrown(YAMLException)
        e.message.contains("max=10")
    }

    void "a raised alias limit is still enforced"() {
        given:
        System.setProperty(YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_PROPERTY, "100")

        when:
        read(billionLaughsYaml(11, 10))

        then:
        def e = thrown(YAMLException)
        e.message.contains("max=100")
    }

    @Unroll
    void "an invalid alias limit #value fails with a clear error"() {
        given:
        System.setProperty(YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_PROPERTY, value)

        when:
        read("foo: bar")

        then:
        def e = thrown(ConfigurationException)
        e.message.contains(YamlPropertySourceLoader.MAX_ALIASES_FOR_COLLECTIONS_PROPERTY)
        e.message.contains(value)
        e.message.contains("between 1 and ${Integer.MAX_VALUE}")
        (e.cause instanceof NumberFormatException) == unparseable

        where:
        value        | unparseable
        "abc"        | true
        "3000000000" | true
        "0"          | false
        "-1"         | false
    }

    private static Map<String, Object> read(String yaml) {
        new YamlPropertySourceLoader().read("application.yml", new ByteArrayInputStream(yaml.bytes))
    }

    /**
     * One anchored map referenced {@code aliases} times.
     */
    private static String anchorHeavyYaml(int aliases) {
        def sb = new StringBuilder("base: &ref\n  key: value\nitems:\n")
        aliases.times { sb.append("  i").append(it).append(": *ref\n") }
        sb.toString()
    }

    /**
     * Each level is a list of {@code fanOut} aliases to the previous level, so the expanded size grows
     * as fanOut^levels while the document needs levels * fanOut aliases.
     */
    private static String billionLaughsYaml(int levels, int fanOut) {
        def sb = new StringBuilder("l0: &l0 [lol]\n")
        (1..levels).each { level ->
            def refs = (1..fanOut).collect { "*l${level - 1}" }.join(", ")
            sb.append("l").append(level).append(": &l").append(level).append(" [").append(refs).append("]\n")
        }
        sb.toString()
    }

    private static void setGetenv(UnaryOperator<String> operator) {
        getenvField().set(null, operator)
    }

    private static Field getenvField() {
        Field field = CachedEnvironment.getDeclaredField("getenv")
        field.setAccessible(true)
        field
    }
}
