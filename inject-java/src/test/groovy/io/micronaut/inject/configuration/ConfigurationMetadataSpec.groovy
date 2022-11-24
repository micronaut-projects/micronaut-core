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
package io.micronaut.inject.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.annotation.processing.ConfigurationMetadataProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import org.intellij.lang.annotations.Language

import javax.annotation.processing.Processor

class ConfigurationMetadataSpec extends AbstractTypeElementSpec {

    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {

            @Override
            protected List<Processor> getAnnotationProcessors() {
                def processors = super.getAnnotationProcessors()
                processors.add(new ConfigurationMetadataProcessor())
                return processors
            }
        }
    }

    private boolean jsonEquals(@Language("json") String provided, @Language("json") String expected) {
        ObjectMapper mapper = new ObjectMapper()
        def providedMap = mapper.readValue(provided, Map.class)
        def expectedMap = mapper.readValue(expected, Map.class)
        def providedJson = mapper.writeValueAsString(providedMap)
        def expectedJson = mapper.writeValueAsString(expectedMap)
        return providedJson == expectedJson
    }

    protected String buildConfigurationMetadata(@Language("java") String cls) {
        return super.buildAndReadResourceAsString("META-INF/spring-configuration-metadata.json", cls)
    }

    def setup() {
        ConfigurationMetadataBuilder.reset()
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
{"groups":[{"name":"test","type":"test.MyProperties"}],"properties":[{"name":"test.foo","type":"java.lang.String","sourceType":"test.MyProperties"}]}
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
{"groups":[{"name":"test","type":"test.MyProperties"}],"properties":[{"name":"test.foo","type":"java.lang.String","sourceType":"test.MyProperties"}]}
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
{"groups":[{"name":"test","type":"test.MyProperties"}],"properties":[{"name":"test.foo","type":"java.lang.String","sourceType":"test.MyProperties"}]}
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
      "type": "test.MyProperties"
    }
  ],
  "properties": [
    {
      "name": "test.foo",
      "type": "java.lang.String",
      "sourceType": "test.MyProperties"
    },
    {
      "name": "test.bar",
      "type": "int",
      "sourceType": "test.MyProperties"
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
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true
    )
    Config.ConfigBuilder options = Config.build();


}
''')
        then:
            jsonEquals(metadataJson, '''
{
  "groups": [
    {
      "name": "neo4j.test",
      "type": "test.Neo4jProperties"
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
      "type": "org.neo4j.driver.v1.Logging",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.leaked-sessions-logging",
      "type": "boolean",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.max-idle-sessions",
      "type": "int",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.connection-liveness-check-timeout",
      "type": "java.time.Duration",
      "sourceType": "org.neo4j.driver.v1.Config$ConfigBuilder"
    },
    {
      "name": "neo4j.test.encryption",
      "type": "boolean",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.trust-strategy",
      "type": "org.neo4j.driver.v1.Config$TrustStrategy",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.connection-timeout",
      "type": "java.time.Duration",
      "sourceType": "org.neo4j.driver.v1.Config$ConfigBuilder"
    },
    {
      "name": "neo4j.test.max-transaction-retry-time",
      "type": "java.time.Duration",
      "sourceType": "org.neo4j.driver.v1.Config$ConfigBuilder"
    }
  ]
}
''')
    }

    void "test specifying a configuration prefix"() {
        when:
            String metadataJson = buildConfigurationMetadata('''
package test;

import io.micronaut.context.annotation.*;
import org.neo4j.driver.v1.*;

@ConfigurationProperties("neo4j.test")
class Neo4jProperties {
    protected java.net.URI uri;

    @ConfigurationBuilder(
        prefixes="with",
        allowZeroArgs=true,
        configurationPrefix="options"
    )
    Config.ConfigBuilder options = Config.build();


}
''')
        then:
            jsonEquals(metadataJson, '''
{
  "groups": [
    {
      "name": "neo4j.test",
      "type": "test.Neo4jProperties"
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
      "type": "org.neo4j.driver.v1.Logging",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.options.leaked-sessions-logging",
      "type": "boolean",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.options.max-idle-sessions",
      "type": "int",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.options.connection-liveness-check-timeout",
      "type": "java.time.Duration",
      "sourceType": "org.neo4j.driver.v1.Config$ConfigBuilder"
    },
    {
      "name": "neo4j.test.options.encryption",
      "type": "boolean",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.options.trust-strategy",
      "type": "org.neo4j.driver.v1.Config$TrustStrategy",
      "sourceType": "test.Neo4jProperties"
    },
    {
      "name": "neo4j.test.options.connection-timeout",
      "type": "java.time.Duration",
      "sourceType": "org.neo4j.driver.v1.Config$ConfigBuilder"
    },
    {
      "name": "neo4j.test.options.max-transaction-retry-time",
      "type": "java.time.Duration",
      "sourceType": "org.neo4j.driver.v1.Config$ConfigBuilder"
    }
  ]
}
''')
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
    },
    {
      "name": "parent",
      "type": "test.ParentConfig"
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
