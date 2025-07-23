package io.micronaut.inject.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import org.intellij.lang.annotations.Language

class ConfigurationMetadataSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
        }
    }

    private static boolean jsonEquals(@Language("json") String provided, @Language("json") String expected) {
        ObjectMapper mapper = new ObjectMapper()
        def providedMap = mapper.readValue(provided, Map.class)
        def expectedMap = mapper.readValue(expected, Map.class)
        def providedJson = mapper.writeValueAsString(providedMap)
        def expectedJson = mapper.writeValueAsString(expectedMap)
        assert providedJson == expectedJson
        true
    }

    private static boolean jsonEquals(@Language("json") String provided, Map expected) {
        ObjectMapper mapper = new ObjectMapper()
        def providedMap = mapper.readValue(provided, Map.class)
        def providedJson = mapper.writeValueAsString(providedMap)
        def expectedJson = mapper.writeValueAsString(expected)
        assert providedJson == expectedJson
        true
    }

    protected String buildConfigurationMetadata(@Language("java") String cls) {
        return super.buildAndReadResourceAsString("META-INF/spring-configuration-metadata.json", cls)
    }

    def setup() {
    }

    void "test configuration metadata and records"() {
        when:
        String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

/**
*  My Configuration description.
*
 * @param name The name of the config
 * @param age The age of the config
*/
@ConfigurationProperties("test")
record MyProperties(String name, int age, NestedConfig nested) {
    @ConfigurationProperties("nested")
    record NestedConfig(int num) {}
}

''')

        then:
        jsonEquals(metadataJson, '''
{"groups":[{"name":"test","type":"test.MyProperties","description":"My Configuration description."},{"name":"test.nested","type":"test.MyProperties$NestedConfig"}],"properties":[{"name":"test.name","type":"java.lang.String","sourceType":"test.MyProperties","description":"The name of the config"},{"name":"test.age","type":"int","sourceType":"test.MyProperties","description":"The age of the config"},{"name":"test.nested.num","type":"int","sourceType":"test.MyProperties$NestedConfig"}]}
''')
    }

    void "test configuration metadata and interfaces"() {
        when:
        String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

/**
*  My Configuration description.
*
*/
@ConfigurationProperties("test")
interface MyProperties {
    /**
    * @return The name
    */
    String getName();

    /**
     * The age
     */
    int getAge();
}

''')

        then:
        jsonEquals(metadataJson, '''
{"groups":[{"name":"test","type":"test.MyProperties","description":"My Configuration description."}],"properties":[{"name":"test.name","type":"java.lang.String","sourceType":"test.MyProperties","description":"The name"},{"name":"test.age","type":"int","sourceType":"test.MyProperties","description":"The age"}]}
''')
    }

    void "test default values and descriptions"() {
        when:
        String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

/**
*  My Configuration description.
*/
@ConfigurationProperties("test")
interface MyProperties {

    String DEFAULT_NAME = "Fred";

    /**
    * Get the name, default value {@value #DEFAULT_NAME}.
    * @return The name
    */
    String getName();

    /**
     * The age
     */
    int getAge();
}

''')

        then:
        jsonEquals(metadataJson, [
                groups    : [
                        [name: 'test', type: "test.MyProperties", description: "My Configuration description."],
                ],
                properties: [
                        [name: 'test.name', type: "java.lang.String", sourceType: "test.MyProperties", description: "Get the name, default value {@value #DEFAULT_NAME}."],
                        [name: 'test.age', type: "int", sourceType: "test.MyProperties", description: "The age"]
                ]

        ])
    }

    void "test setter descriptions"() {
        when:
        String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.util.Toggleable;

interface BaseConfig extends Toggleable {

    /**
     * @return The name
     */
    String getName();
}

@ConfigurationProperties("test")
class Config implements BaseConfig {

    public static final String DEFAULT_NAME = "test";

    private String name = DEFAULT_NAME;

    @Override
    public String getName() {
        return name;
    }

    /**
     * Sets the name (default {@value #DEFAULT_NAME}).
     * @param name the name to use
     */
    public void setName(String name) {
        this.name = name;
    }
}
''')

        then:
        jsonEquals(metadataJson, [
                groups    : [
                        [name: 'test', type: "test.Config"],
                ],
                properties: [
                        [name: 'test.name', type: "java.lang.String", sourceType: "test.Config", description: "Sets the name (default {@value #DEFAULT_NAME})."],
                ]

        ])
    }

    void "test configuration metadata and javabeans"() {
        when:
        String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

/**
*  My Configuration description.
*
*/
@ConfigurationProperties("test")
class MyProperties {

    private String name;

    private int age;

    public String getName() {
        return name;
    }

    /**
    * Sets the name.
    * @param name The name
    */
    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    /**
    *
    * @param age The age
    */
    public void setAge(int age) {
        this.age = age;
    }
}

''')

        then:
        jsonEquals(metadataJson, '''
{"groups":[{"name":"test","type":"test.MyProperties","description":"My Configuration description."}],"properties":[{"name":"test.name","type":"java.lang.String","sourceType":"test.MyProperties","description":"Sets the name."},{"name":"test.age","type":"int","sourceType":"test.MyProperties","description":"The age"}]}
''')
    }

    void "test configuration builder on method"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    private Test test;

    @ConfigurationBuilder(factoryMethod="build")
    void setTest(Test test) {
        this.test = test;
    }

    Test getTest() {
        return this.test;
    }

}

class Test {
    private String foo;
    private Test() {}
    public void setFoo(String s) {
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }

    static Test build() {
        return new Test();
    }
}
''')

        then:
            jsonEquals(metadataJson, '''
{"groups":[{"name":"test","type":"test.Test"}],"properties":[{"name":"test.foo","type":"java.lang.String","sourceType":"test.Test"}]}
''')
    }

    void "test configuration builder with includes"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build", includes="foo")
    Test test;

}

class Test {
    private String foo;
    private String bar;
    private Test() {}
    public void setFoo(String s) {
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }
    public void setBar(String s) {
        this.bar = s;
    }
    public String getBar() {
        return bar;
    }

    static Test build() {
        return new Test();
    }
}
''')
        then:
            jsonEquals(metadataJson, '''
{"groups":[{"name":"test","type":"test.Test"}],"properties":[{"name":"test.foo","type":"java.lang.String","sourceType":"test.Test"}]}
''')
    }

    void "test configuration builder with factory method"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder(factoryMethod="build")
    Test test;

}

class Test {
    private String foo;
    private Test() {}
    public void setFoo(String s) {
        this.foo = s;
    }
    public String getFoo() {
        return foo;
    }

    static Test build() {
        return new Test();
    }
}
''')
        then:
            jsonEquals(metadataJson, '''
{"groups":[{"name":"test","type":"test.Test"}],"properties":[{"name":"test.foo","type":"java.lang.String","sourceType":"test.Test"}]}
''')
    }

    void "test with setters that return void"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;
import java.lang.Deprecated;

@ConfigurationProperties("test")
class MyProperties {

    @ConfigurationBuilder
    Test test = new Test();


}

class Test {
    private String foo;
    private int bar;
    private Long baz;

    public void setFoo(String s) { this.foo = s;}
    public void setBar(int s) {this.bar = s;}
    @Deprecated
    public void setBaz(Long s) {this.baz = s;}

    public String getFoo() { return this.foo; }
    public int getBar() { return this.bar; }
    public Long getBaz() { return this.baz; }
}
''')
        then:
            jsonEquals(metadataJson, '''
{
  "groups": [
    {
      "name": "test",
      "type": "test.Test"
    }
  ],
  "properties": [
    {
      "name": "test.foo",
      "type": "java.lang.String",
      "sourceType": "test.Test"
    },
    {
      "name": "test.bar",
      "type": "int",
      "sourceType": "test.Test"
    }
  ]
}
''')
    }

    void "test different inject types for config properties"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.builder();


}
''')
        then:
            jsonEquals(metadataJson, '''
{
	"groups": [
		{
			"name": "neo4j.test",
			"type": "test.Neo4jProperties"
		},
        {
          "name": "neo4j.test",
          "type": "org.neo4j.driver.Config$ConfigBuilder"
        }
	],
	"properties": [
		{
			"name": "neo4j.test.uri",
			"type": "java.net.URI",
			"sourceType": "test.Neo4jProperties"
		},
		{
			"name": "neo4j.test.logging",
			"type": "org.neo4j.driver.Logging",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.leaked-sessions-logging",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.connection-liveness-check-timeout",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.max-connection-lifetime",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.max-connection-pool-size",
			"type": "int",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.connection-acquisition-timeout",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.encryption",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.trust-strategy",
			"type": "org.neo4j.driver.Config$TrustStrategy",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.routing-table-purge-delay",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.fetch-size",
			"type": "long",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.connection-timeout",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.max-transaction-retry-time",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.resolver",
			"type": "org.neo4j.driver.net.ServerAddressResolver",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.driver-metrics",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.metrics-adapter",
			"type": "org.neo4j.driver.MetricsAdapter",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.event-loop-threads",
			"type": "int",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.user-agent",
			"type": "java.lang.String",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.notification-config",
			"type": "org.neo4j.driver.NotificationConfig",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.telemetry-disabled",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		}
	]
}''')
    }

    void "test specifying a configuration prefix"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    Config.ConfigBuilder options = Config.builder();


}
''')
        then:
            jsonEquals(metadataJson, '''
{
	"groups": [
		{
			"name": "neo4j.test",
			"type": "test.Neo4jProperties"
		},
		{
		    "name":"neo4j.test.options",
		    "type":"org.neo4j.driver.Config$ConfigBuilder"
		}
	],
	"properties": [
		{
			"name": "neo4j.test.uri",
			"type": "java.net.URI",
			"sourceType": "test.Neo4jProperties"
		},
		{
			"name": "neo4j.test.options.logging",
			"type": "org.neo4j.driver.Logging",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.leaked-sessions-logging",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.connection-liveness-check-timeout",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.max-connection-lifetime",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.max-connection-pool-size",
			"type": "int",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.connection-acquisition-timeout",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.encryption",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.trust-strategy",
			"type": "org.neo4j.driver.Config$TrustStrategy",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.routing-table-purge-delay",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.fetch-size",
			"type": "long",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.connection-timeout",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.max-transaction-retry-time",
			"type": "java.time.Duration",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.resolver",
			"type": "org.neo4j.driver.net.ServerAddressResolver",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.driver-metrics",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.metrics-adapter",
			"type": "org.neo4j.driver.MetricsAdapter",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.event-loop-threads",
			"type": "int",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.user-agent",
			"type": "java.lang.String",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.notification-config",
			"type": "org.neo4j.driver.NotificationConfig",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		},
		{
			"name": "neo4j.test.options.telemetry-disabled",
			"type": "boolean",
			"sourceType": "org.neo4j.driver.Config$ConfigBuilder"
		}
	]
}''')
    }

    void "test inner"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("foo.bar")
class MyConfigInner {

    private List<InnerVal> innerVals;

    public List<InnerVal> getInnerVals() {
        return innerVals;
    }

    public void setInnerVals(List<InnerVal> innerVals) {
        this.innerVals = innerVals;
    }

    public static class InnerVal {

        private Integer expireUnsignedSeconds;

        public Integer getExpireUnsignedSeconds() {
            return expireUnsignedSeconds;
        }

        public void setExpireUnsignedSeconds(Integer expireUnsignedSeconds) {
            this.expireUnsignedSeconds = expireUnsignedSeconds;
        }
    }

}
''')
        then:
            jsonEquals(metadataJson, '''
{"groups":[{"name":"foo.bar","type":"test.MyConfigInner"}],"properties":[{"name":"foo.bar.inner-vals","type":"java.util.List","sourceType":"test.MyConfigInner"}]}
''')
    }

    void "test inheritance"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig extends ParentConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @ConfigurationProperties("baz")
    static class ChildConfig {
        String stuff;

        public String getStuff() {
            return stuff;
        }

        public void setStuff(String stuff) {
            this.stuff = stuff;
        }
    }
}

@ConfigurationProperties("parent")
class ParentConfig {

}
''')
        then:
            jsonEquals(metadataJson, '''
{
  "groups": [
    {
      "name": "parent.foo.bar",
      "type": "test.MyConfig"
    },
    {
      "name": "parent.foo.bar.baz",
      "type": "test.MyConfig$ChildConfig"
    }
  ],
  "properties": [
    {
      "name": "parent.foo.bar.host",
      "type": "java.lang.String",
      "sourceType": "test.MyConfig"
    },
    {
      "name": "parent.foo.bar.baz.stuff",
      "type": "java.lang.String",
      "sourceType": "test.MyConfig$ChildConfig"
    }
  ]
}
''')
    }

}
