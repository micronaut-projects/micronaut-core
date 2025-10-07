package io.micronaut.inject.configuration

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class ConfigurationBuilderSpec extends AbstractTypeElementSpec {

    void "test enums"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;
import java.util.Set;

@ConfigurationProperties("test.props")
final class TestProps {
    @ConfigurationBuilder(prefixes = "set")
    Something builder = new Something();

    class Something {
        private Set<Foo> foos = Set.of();

        public Set<Foo> getFoos() {
            return foos;
        }

        public void setFoos(Set<Foo> foos) {
            this.foos = foos;
        }
    }


    enum Foo {
        BAR,
        BAZ
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.foos": "BAR"]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        testPropBean.builder.foos.size() == 1
        testPropBean.builder.foos.first() instanceof Enum

        cleanup:
        ctx.close()
    }

    void "test duplicate"() {
        when:
        ApplicationContext ctx = buildContext("test.MySQLClientConfiguration", '''
package test;

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.inject.configuration.MyOptions;

@ConfigurationProperties("dd")
class MySQLClientConfiguration {

    @ConfigurationBuilder
    protected MyOptions connectOptions = new MyOptions();

    @ConfigurationBuilder
    protected MyOptions poolOptions = new MyOptions();

    protected String uri;

    /**
     * @return The MySQL connection URI.
     */
    public String getUri() {
        return uri;
    }

    /**
     *
     * @return The options for configuring a connection.
     */
    public MyOptions getConnectOptions() {
        return connectOptions;
    }

    /**
     *
     * @return The options for configuring a connection pool.
     */
    public MyOptions getPoolOptions() {
        return poolOptions;
    }
}
''')
        then:
        noExceptionThrown()
    }

    void "test definition uses getter instead of field"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")
final class TestProps {
    @ConfigurationBuilder(prefixes = "with")
    private Engine.Builder builder = Engine.builder();

    public final Engine.Builder getBuilder() {
        return builder;
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.manufacturer": "Toyota"]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        ctx.getProperty("test.props.manufacturer", String).get() == "Toyota"
        testPropBean.getBuilder().build().getManufacturer() == "Toyota"

        cleanup:
        ctx.close()
    }

    void "test definition uses field when getter type doesn't match"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")
final class TestProps {
    @ConfigurationBuilder(prefixes = "with")
    protected Engine.Builder engine = Engine.builder();

    public final Engine getEngine() {
        return engine.build();
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.manufacturer": "Toyota"]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        ctx.getProperty("test.props.manufacturer", String).get() == "Toyota"
        testPropBean.getEngine().getManufacturer() == "Toyota"

        cleanup:
        ctx.close()
    }

    void "test private config field with no getter throws an error"() {
        when:
        ApplicationContext ctx = buildContext("test.TestProps", '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")
final class TestProps {
    @ConfigurationBuilder(prefixes = "with")
    private Engine.Builder builder = Engine.builder();
}
''')

        then:
        RuntimeException ex = thrown()
        ex.message.contains("ConfigurationBuilder applied to a non accessible (private or package-private/protected in a different package) field must have a corresponding non-private getter method.")
        ex.message.contains("private Engine.Builder builder = Engine.builder();")
    }

    void "test config field with setter abnormal parameter name"() {
        given:
        ApplicationContext ctx = buildContext("test.TestProps", '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties("test.props")
final class TestProps {
    Engine.Builder builder = Engine.builder();

    Engine.Builder getBuilder() {
        return this.builder;
    }

    @ConfigurationBuilder(prefixes = "with")
    void setBuilder(Engine.Builder p0) {
        this.builder = p0;
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["test.props.manufacturer": "Toyota"]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.TestProps")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        ctx.getProperty("test.props.manufacturer", String).get() == "Toyota"
        testPropBean.getBuilder().build().getManufacturer() == "Toyota"

        cleanup:
        ctx.close()
    }

    void "test configuration builder that are interfaces"() {
        given:
        ApplicationContext ctx = buildContext("test.PoolConfig", '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.AnnWithClass;

@ConfigurationProperties("pool")
final class PoolConfig {

    @ConfigurationBuilder(prefixes = {""})
    public ConnectionPool.Builder builder = DefaultConnectionPool.builder();

}

interface ConnectionPool {

    interface Builder {
        Builder maxConcurrency(Integer maxConcurrency);
        Builder foo(Foo foo);
        ConnectionPool build();
    }

    int getMaxConcurrency();
}

class DefaultConnectionPool implements ConnectionPool {
    private final int maxConcurrency;

    DefaultConnectionPool(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public static ConnectionPool.Builder builder() {
        return new DefaultBuilder();
    }

    @Override
    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    private static class DefaultBuilder implements ConnectionPool.Builder {

        private int maxConcurrency;

        private DefaultBuilder() {
        }

        @Override
        public ConnectionPool.Builder maxConcurrency(Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        @Override
        public ConnectionPool.Builder foo(Foo foo) {
            return this;
        }

        public ConnectionPool build() {
            return new DefaultConnectionPool(maxConcurrency);
        }
    }
}

@AnnWithClass(String.class)
interface Foo {
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of(["pool.max-concurrency": 123]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.PoolConfig")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()
        testPropBean.builder.build().getMaxConcurrency() == 123

        cleanup:
        ctx.close()
    }

    void "test @Inject with nested configurationProperties"() {
        given:
        ApplicationContext ctx = buildContext("test.NestedConfig", '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;

@ConfigurationProperties("product-aggregator")
class NestedConfig {

    @Inject
    LevelOne levelOnez = new LevelOne();

    public LevelOne getLevelOnez() {
        return levelOnez;
    }

    public void setLevelOnez(LevelOne levelOnez) {
        this.levelOnez = levelOnez;
    }

    @ConfigurationProperties("level-one")
    static class LevelOne {

        String levelOneValue;

        @Inject
        LevelTwo levelTwo = new LevelTwo();

        public String getLevelOneValue() {
            return levelOneValue;
        }

        public void setLevelOneValue(String levelOneValue) {
            this.levelOneValue = levelOneValue;
        }

        public LevelTwo getLevelTwo() {
            return levelTwo;
        }

        public void setLevelTwo(LevelTwo levelTwo) {
            this.levelTwo = levelTwo;
        }

        @ConfigurationProperties("level-two")
        static class LevelTwo {

            private String levelTwoValue;

            public String getLevelTwoValue() {
                return levelTwoValue;
            }

            public void setLevelTwoValue(String levelTwoValue) {
                this.levelTwoValue = levelTwoValue;
            }
        }
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of([
                'product-aggregator.level-one.level-one-value': 'ONE',
                'product-aggregator.level-one.level-two.level-two-value': 'TWO',
        ]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.NestedConfig")
        def testPropBean = ctx.getBean(testProps)
        def definition = ctx.getBeanDefinition(testProps)

        then:
        noExceptionThrown()

        definition.properties.injectedFields.size() == 1
        definition.properties.injectedFields[0].name == "levelOnez"

        testPropBean.getLevelOnez().getLevelOneValue() == "ONE"
        testPropBean.getLevelOnez().getLevelTwo()
        testPropBean.getLevelOnez().getLevelTwo().getLevelTwoValue() == "TWO"

        cleanup:
        ctx.close()
    }

    void "test @Inject with nested configurationProperties - method injection"() {
        given:
        ApplicationContext ctx = buildContext("test.NestedConfig", '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;import jakarta.inject.Inject;

@ConfigurationProperties("product-aggregator")
class NestedConfig {

    private LevelOne levelOnez = new LevelOne();

    public LevelOne getLevelOnez() {
        return levelOnez;
    }

    public void setLevelOnez(LevelOne levelOnez) {
        this.levelOnez = levelOnez;
    }

    @ConfigurationProperties("level-one")
    static class LevelOne {

        String levelOneValue;

        @Inject
        LevelTwo levelTwo = new LevelTwo();

        public String getLevelOneValue() {
            return levelOneValue;
        }

        public void setLevelOneValue(String levelOneValue) {
            this.levelOneValue = levelOneValue;
        }

        public LevelTwo getLevelTwo() {
            return levelTwo;
        }

        public void setLevelTwo(LevelTwo levelTwo) {
            this.levelTwo = levelTwo;
        }

        @ConfigurationProperties("level-two")
        static class LevelTwo {

            private String levelTwoValue;

            public String getLevelTwoValue() {
                return levelTwoValue;
            }

            public void setLevelTwoValue(String levelTwoValue) {
                this.levelTwoValue = levelTwoValue;
            }
        }
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of([
                'product-aggregator.level-one.level-one-value': 'ONE',
                'product-aggregator.level-one.level-two.level-two-value': 'TWO',
        ]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.NestedConfig")
        def testPropBean = ctx.getBean(testProps)

        then:
        noExceptionThrown()

        testPropBean.getLevelOnez().getLevelOneValue() == "ONE"
        testPropBean.getLevelOnez().getLevelTwo()
        testPropBean.getLevelOnez().getLevelTwo().getLevelTwoValue() == "TWO"

        cleanup:
        ctx.close()
    }

    void "test @Inject with nested configurationProperties - field only"() {
        given:
        ApplicationContext ctx = buildContext("test.NestedConfig", '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.inject.Inject;

@ConfigurationProperties("product-aggregator")
class NestedConfig {

    @Inject
    LevelOne levelOnez = new LevelOne();

    @ConfigurationProperties("level-one")
    static class LevelOne {

        String levelOneValue;

        @Inject
        LevelTwo levelTwo = new LevelTwo();

        public String getLevelOneValue() {
            return levelOneValue;
        }

        public void setLevelOneValue(String levelOneValue) {
            this.levelOneValue = levelOneValue;
        }

        public LevelTwo getLevelTwo() {
            return levelTwo;
        }

        public void setLevelTwo(LevelTwo levelTwo) {
            this.levelTwo = levelTwo;
        }

        @ConfigurationProperties("level-two")
        static class LevelTwo {

            private String levelTwoValue;

            public String getLevelTwoValue() {
                return levelTwoValue;
            }

            public void setLevelTwoValue(String levelTwoValue) {
                this.levelTwoValue = levelTwoValue;
            }
        }
    }
}
''')
        ctx.getEnvironment().addPropertySource(PropertySource.of([
                'product-aggregator.level-one.level-one-value': 'ONE',
                'product-aggregator.level-one.level-two.level-two-value': 'TWO',
        ]))

        when:
        Class<?> testProps = ctx.classLoader.loadClass("test.NestedConfig")
        def testPropBean = ctx.getBean(testProps)
        def definition = ctx.getBeanDefinition(testProps)

        then:
        noExceptionThrown()

        definition.properties.injectedFields.size() == 1
        definition.properties.injectedFields[0].name == "levelOnez"

        testPropBean.levelOnez.getLevelOneValue() == "ONE"
        testPropBean.levelOnez.getLevelTwo()
        testPropBean.levelOnez.getLevelTwo().getLevelTwoValue() == "TWO"

        cleanup:
        ctx.close()
    }
}
