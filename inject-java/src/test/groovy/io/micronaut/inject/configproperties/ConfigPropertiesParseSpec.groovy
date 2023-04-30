package io.micronaut.inject.configproperties

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.ApplicationContextBuilder
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.PropertySource
import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.configuration.Engine
import spock.lang.Issue

import java.time.Duration

class ConfigPropertiesParseSpec extends AbstractTypeElementSpec {

    void "test configuration properties implementing interface"() {
        when:
        def context = buildContext('''
package jdbctest;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("jdbc")
class TestConfiguration extends AbstractConfiguration implements BasicJdbcConfiguration {
    private String url;
    @Override public void setUrl(String url) {
        this.url = url;
    }
    @Override public String getUrl() {
        return url;
    }
}
class AbstractConfiguration {
    private String username;
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}
interface BasicJdbcConfiguration {
    String getUrl();
    void setUrl(String url);
    String getUsername();
    void setUsername(String username);
}
''')
        def bean = getBean(context, 'jdbctest.TestConfiguration')

        then:
        bean.url == 'test'
        bean.username == 'foo'
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/8574")
    void "test configuration properties inherited from parent with multiple overloads"() {
        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import java.io.InputStream;
import java.util.*;

@ConfigurationProperties("freemarker")
class TestConfiguration extends ParentConfiguration {
    @Override
    public void setSettings(
            @MapFormat(keyFormat = StringConvention.UNDER_SCORE_SEPARATED_LOWER_CASE) Properties props){
        super.setSettings(props);
    }

}
class ParentConfiguration {
    private Properties properties;
    public void setSettings(InputStream inputStream) {
    }
    public void setSettings(Properties properties) {
        this.properties = properties;
    }

    public Properties properties() {
        return properties;
    }
}
''')
        def bean = getBean(context, 'test.TestConfiguration')

        then:
        bean != null
        bean.properties() as Map == [url_escaping_charset:'UTF-8']
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/8480")
    void "test configuration properties inheritance for compiled classes - inherited props"() {
        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.http.server.HttpServerConfiguration;

@ConfigurationProperties("netty")
class NettyHttpServerConfiguration extends
 HttpServerConfiguration {
    private Parent parent;
    private Child child;
    public test.NettyHttpServerConfiguration.Parent getParent() {
        return parent;
    }

    public void setParent(test.NettyHttpServerConfiguration.Parent parent) {
        this.parent = parent;
    }

    public void setChild(test.NettyHttpServerConfiguration.Child child) {
        this.child = child;
    }
    public test.NettyHttpServerConfiguration.Child getChild() {
        return child;
    }
    @ConfigurationProperties("child")
    public static class Child extends EventLoopConfig {

    }
    @ConfigurationProperties("parent")
    public static class Parent extends EventLoopConfig {

    }
    public abstract static class EventLoopConfig {
        private Integer ioRatio;
        private int threads;
        public void setIoRatio(Integer ioRatio) {
            this.ioRatio = ioRatio;
        }
        public Integer getIoRatio() {
            return ioRatio;
        }
        public void setThreads(int threads) {
            this.threads = threads;
        }
        public int getNumOfThreads() {
            return threads;
        }
    }
}
''')
        def config = getBean(context, "test.NettyHttpServerConfiguration")

        then:
        config.idleTimeout == Duration.ofSeconds(2)
        config.parent.ioRatio == 10
        config.parent.numOfThreads == 5
        config.child.ioRatio == 15
        config.child.numOfThreads == 55
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/8480")
    void "test configuration properties inheritance for compiled classes"() {
        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.session.http.HttpSessionConfiguration;
import java.net.URI;
import java.util.Optional;
import java.util.List;

@ConfigurationProperties("test")
class RedisHttpSessionConfiguration extends
 HttpSessionConfiguration {
    private String writeMode;
    private URI uri;
    private List<URI> uris;

    public void setWriteMode(String writeMode) {
        this.writeMode = writeMode;
    }
    public String getWriteMode() {
        return writeMode;
    }

    public Optional<URI> getUri() {
        return Optional.ofNullable(uri);
    }
    public void setUri(String uri) {
        this.uri = URI.create(uri);
    }

    public List<URI> getUris() {
        return uris;
    }

    public void setUris(URI... uris) {
        this.uris = List.of(uris);
    }
}
''')
        def config = getBean(context, "test.RedisHttpSessionConfiguration")

        then:
        config.writeMode == 'test'
        config.uri.isPresent()
        config.uri.get() == URI.create('http://localhost:9999')
        config.uris == List.of(URI.create('http://localhost:9999'))
    }

    void "test configuration properties with mixed getters/setters"() {
        when:
        def context = buildContext('''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.Nullable;
import java.time.Duration;
import java.util.Optional;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public Optional<String> getHost() {
        return Optional.ofNullable(host);
    }

    public void setHost(@Nullable String host) {
        this.host = host;
    }
}

''')
        def config = getBean(context, 'test.MyConfig')

        then:
        config.host.get() == 'bar'
    }

    @Override
    protected void configureContext(ApplicationContextBuilder contextBuilder) {
        contextBuilder.properties(
                'foo.bar.host':'bar',
                'jdbc.url':'test',
                'jdbc.username':'foo',
                "micronaut.session.http.test.write-mode": "test",
                "micronaut.session.http.test.uri": "http://localhost:9999",
                "micronaut.session.http.test.uris": "http://localhost:9999",
                "micronaut.server.idle-timeout": "2s",
                "micronaut.server.netty.parent.io-ratio": "10",
                "micronaut.server.netty.parent.threads": "5",
                "micronaut.server.netty.child.io-ratio": "15",
                "micronaut.server.netty.child.threads": "55",
                "freemarker.settings.urlEscapingCharset": 'UTF-8'
        )
    }

    void "test inner class paths - pojo inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @ConfigurationProperties("baz")
    static class ChildConfig extends ParentConfig {
        protected String stuff;
    }
}

class ParentConfig {
    private String foo;

    public void setFoo(String foo) {
        this.foo = foo;
    }
}
''')
        then:
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedFields[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedFields[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedFields[0].name == 'stuff'

        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.foo'
        beanDefinition.injectedMethods[0].name == 'setFoo'
    }

    void "test inner class paths - fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    @ConfigurationProperties("baz")
    static class ChildConfig {
        protected String stuff;
    }
}
''')
        then:
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 0
        beanDefinition.injectedFields[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedFields[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedFields[0].name == 'stuff'
    }

    void "test inner class paths - one level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
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
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }


    void "test inner class paths - two levels"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$MoreConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
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

        @ConfigurationProperties("more")
        static class MoreConfig {
            String stuff;

            public String getStuff() {
                return stuff;
            }

            public void setStuff(String stuff) {
                this.stuff = stuff;
            }
        }
    }
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.more.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inner class paths - with parent inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
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
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'parent.foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test setters with two arguments are not injected"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo.bar")
class MyConfig {
    private String host = "localhost";


    public String getHost() {
        return host;
    }

    public void setHost(String host, int port) {
        this.host = host;
    }
}


''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 0
    }

    void "test setters with two arguments from abstract parent are not injected"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;


abstract class MyConfig {
    private String host = "localhost";


    public String getHost() {
        return host;
    }

    public void setHost(String host, int port) {
        this.host = host;
    }
}

@ConfigurationProperties("baz")
class ChildConfig extends MyConfig {
    String stuff;

    public String getStuff() {
        return stuff;
    }

    public void setStuff(String stuff) {
        this.stuff = stuff;
    }
}


''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inheritance with setters"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    protected int port;
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}

@ConfigurationProperties("baz")
class ChildConfig extends MyConfig {
    String stuff;

    public String getStuff() {
        return stuff;
    }

    public void setStuff(String stuff) {
        this.stuff = stuff;
    }
}




''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedFields[0].name == 'port'
        beanDefinition.injectedFields[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.port'
        beanDefinition.injectedMethods[1].name == 'setStuff'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.host'
        beanDefinition.injectedMethods[0].name == 'setHost'
    }

    void "test annotation on package scope setters arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.HttpClientConfiguration', '''
package test;

import io.micronaut.core.convert.format.*;
import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("http.client")
public class HttpClientConfiguration {
    private int maxContentLength = 1024 * 1024 * 10; // 10MB;

    void setMaxContentLength(@ReadableBytes int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
    public int getMaxContentLength() {
        return maxContentLength;
    }

}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesize(ReadableBytes)
    }

    void "test annotation on setters arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.HttpClientConfiguration', '''
package test;

import io.micronaut.core.convert.format.*;
import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("http.client")
public class HttpClientConfiguration {
    private int maxContentLength = 1024 * 1024 * 10; // 10MB;

    public void setMaxContentLength(@ReadableBytes int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
    public int getMaxContentLength() {
        return maxContentLength;
    }

}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesizeAll().size() == 2
        beanDefinition.injectedMethods[0].arguments[0].synthesize(ReadableBytes)
        // This should be removed in Micronaut 4
        beanDefinition.injectedMethods[0].arguments[0].synthesize(PropertySource)
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties {
    protected String fieldTest = "unconfigured";
    private final boolean privateFinal = true;
    protected final boolean protectedFinal = true;
    private boolean anotherField;
    private String internalField = "unconfigured";
    public void setSetterTest(String s) {
        this.internalField = s;
    }

    public String getSetter() { return this.internalField; }
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'fieldTest'
        beanDefinition.injectedMethods.size() == 1

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder().start()
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"

        when:
        applicationContext.environment.addPropertySource(
                "test",
                ['foo.setterTest' :'foo',
                'foo.fieldTest' :'bar']
        )
        bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"

    }

    void "test configuration properties inheritance from non-configuration properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties extends Parent {
    protected String fieldTest = "unconfigured";
    private final boolean privateFinal = true;
    protected final boolean protectedFinal = true;
    private boolean anotherField;
    private String internalField = "unconfigured";
    public void setSetterTest(String s) {
        this.internalField = s;
    }

    public String getSetter() { return this.internalField; }
}

class Parent {
    private String parentField;

    public void setParentTest(String s) {
        this.parentField = s;
    }

    public String getParentTest() { return this.parentField; }
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'fieldTest'
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == 'setParentTest'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.parent-test'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.setter-test'
        beanDefinition.injectedMethods[1].name == 'setSetterTest'


        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder().start()
        def bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"

        when:
        applicationContext.environment.addPropertySource(
                "test",
                ['foo.setterTest' :'foo',
                'foo.fieldTest' :'bar']
        )
        bean = factory.instantiate(applicationContext)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"
    }

    void "test boolean fields starting with is[A-Z] map to set methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("micronaut.issuer.FooConfigurationProperties", """
package micronaut.issuer;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("foo")
public class FooConfigurationProperties {

    private String issuer;
    private boolean isEnabled;
    protected Boolean isOther;


    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    //isEnabled field maps to setEnabled method
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    //isOther field does not map to setOther method because its the class and not primitive
    public void setOther(Boolean other) {
        this.isOther = other;
    }
}

""")
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].name == "setIssuer"
        beanDefinition.injectedMethods[1].name == "setEnabled"
        beanDefinition.injectedFields[0].name == "isOther"
    }

    void "test configuration properties returns self"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("my")
class MyConfig {
    String host;
    public String getHost() {
        return host;
    }
    public MyConfig setHost(String host) {
        this.host = host;
        return this;
    }
}''')
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder(["my.host": "abc"]).start()
        def bean = factory.instantiate(applicationContext)

        then:
        bean.getHost() == "abc"
    }

    void "test includes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", includes = {"publicField", "parentPublicField"})
class MyProperties extends Parent {
    public String publicField;
    public String anotherPublicField;
}

class Parent {
    public String parentPublicField;
    public String anotherParentPublicField;
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedFields.size() == 2
        beanDefinition.injectedFields[0].name == "parentPublicField"
        beanDefinition.injectedFields[1].name == "publicField"
    }

    void "test includes on methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", includes = {"publicMethod", "parentPublicMethod"})
class MyProperties extends Parent {

    public void setPublicMethod(String value) {}
    public void setAnotherPublicMethod(String value) {}
}

class Parent {
    public void setParentPublicMethod(String value) {}
    public void setAnotherParentPublicMethod(String value) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setParentPublicMethod"
        beanDefinition.injectedMethods[1].name == "setPublicMethod"
    }

    void "test excludes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", excludes = {"anotherPublicField", "anotherParentPublicField"})
class MyProperties extends Parent {
    public String publicField;
    public String anotherPublicField;
}

class Parent {
    public String parentPublicField;
    public String anotherParentPublicField;
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedFields.size() == 2
        beanDefinition.injectedFields[0].name == "parentPublicField"
        beanDefinition.injectedFields[1].name == "publicField"
    }

    void "test excludes on methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", excludes = {"anotherPublicMethod", "anotherParentPublicMethod"})
class MyProperties extends Parent {

    public void setPublicMethod(String value) {}
    public void setAnotherPublicMethod(String value) {}
}

class Parent {
    public void setParentPublicMethod(String value) {}
    public void setAnotherParentPublicMethod(String value) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setParentPublicMethod"
        beanDefinition.injectedMethods[1].name == "setPublicMethod"
    }

    void "test excludes on configuration builder"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties(value = "foo", excludes = {"engine", "engine2"})
class MyProperties extends Parent {

    @ConfigurationBuilder(prefixes = "with")
    Engine.Builder engine = Engine.builder();

    private Engine.Builder engine2 = Engine.builder();

    @ConfigurationBuilder(configurationPrefix = "two", prefixes = "with")
    public void setEngine2(Engine.Builder engine3) {
        this.engine2 = engine3;
    }

    public Engine.Builder getEngine2() {
        return engine2;
    }
}

class Parent {
    void setEngine(Engine.Builder engine) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.isEmpty()
        beanDefinition.injectedFields.isEmpty()

        when:
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'foo.manufacturer':'Subaru',
                'foo.two.manufacturer':'Subaru'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        ((Engine.Builder) bean.engine).build().manufacturer == 'Subaru'
        ((Engine.Builder) bean.getEngine2()).build().manufacturer == 'Subaru'
    }

    void "test name is correct with inner classes of non config props class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test\$TestNestedConfig", '''
package test;

import io.micronaut.context.annotation.*;

class Test {

    @ConfigurationProperties("test")
    static class TestNestedConfig {
        private String val;

        public String getVal() {
            return val;
        }

        public void setVal(String val) {
            this.val = val;
        }
    }

}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].annotationMetadata.getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "test.val"
    }

    void "test property names with numbers"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AwsConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("aws")
class AwsConfig {

    private String disableEc2Metadata;
    private String disableEcMetadata;
    private String disableEc2instanceMetadata;

    public String getDisableEc2Metadata() {
        return disableEc2Metadata;
    }

    public void setDisableEc2Metadata(String disableEc2Metadata) {
        this.disableEc2Metadata = disableEc2Metadata;
    }

    public String getDisableEcMetadata() {
        return disableEcMetadata;
    }

    public void setDisableEcMetadata(String disableEcMetadata) {
        this.disableEcMetadata = disableEcMetadata;
    }

    public String getDisableEc2instanceMetadata() {
        return disableEc2instanceMetadata;
    }

    public void setDisableEc2instanceMetadata(String disableEc2instanceMetadata) {
        this.disableEc2instanceMetadata = disableEc2instanceMetadata;
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2-metadata"
        beanDefinition.injectedMethods[1].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec-metadata"
        beanDefinition.injectedMethods[2].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2instance-metadata"
    }

    void "test inner interface EachProperty list = true"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Parent$Child$Intercepted', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;

import jakarta.inject.Inject;
import java.util.List;

@ConfigurationProperties("parent")
class Parent {

    private final List<Child> children;

    @Inject
    public Parent(List<Child> children) {
        this.children = children;
    }

    public List<Child> getChildren() {
        return children;
    }

    @EachProperty(value = "children", list = true)
    interface Child {
        String getPropA();
        String getPropB();
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(ConfigurationReader.class, "prefix").get() == "parent.children[*]"
        beanDefinition.getRequiredMethod("getPropA").getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "parent.children[*].prop-a"
    }

    void "test config props with post construct first in file"() {
        given:
        BeanContext context = buildContext("test.EntityProperties", """
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;

@ConfigurationProperties("app.entity")
public class EntityProperties {

    private String prop;

    @PostConstruct
    public void init() {
        System.out.println("prop = " + prop);
    }

    public String getProp() {
        return prop;
    }

    public void setProp(String prop) {
        this.prop = prop;
    }
}
""")

        when:
        context.getBean(context.classLoader.loadClass("test.EntityProperties"))

        then:
        noExceptionThrown()
    }
}
