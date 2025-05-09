package io.micronaut.runtime.context.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.runtime.context.scope.refresh.RefreshScope
import io.micronaut.scheduling.TaskExecutors
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.util.concurrent.Executor

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@RestoreSystemProperties
class RefreshScopeSpec extends Specification {

    void "RefreshScope bean is not loaded for function environment"() {
        when:
        ApplicationContext ctx = ApplicationContext.run(Environment.FUNCTION)

        then:
        !ctx.containsBean(RefreshScope)

        cleanup:
        ctx.close()
    }

    void "RefreshScope bean is loaded for function environment under test"() {
        when:
        ApplicationContext ctx = ApplicationContext.run(Environment.FUNCTION, Environment.TEST)

        then:
        ctx.containsBean(RefreshScope)

        cleanup:
        ctx.close()
    }

    void "RefreshScope bean is loaded by default"() {
        when:
        ApplicationContext ctx = ApplicationContext.run()

        then:
        ctx.containsBean(RefreshScope)

        cleanup:
        ctx.close()
    }

    void "RefreshScope bean is not loaded for android environment"() {
        when:
        ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).environments(Environment.ANDROID).start()

        then:
        !ctx.containsBean(RefreshScope)

        cleanup:
        ctx.close()
    }

    void "RefreshScope bean is loaded for android test environment"() {
        when: 'we specify android, but test is deduced via stack trace inspection'
        ApplicationContext ctx = ApplicationContext.run(Environment.ANDROID)

        then:
        ctx.containsBean(RefreshScope)

        cleanup:
        ctx.close()
    }

    void "test fire refresh event that refreshes all"() {
        given:
        System.setProperty("foo.bar", "test")
        ApplicationContext beanContext = ApplicationContext.builder().start()

        // override IO executor with synchronous impl
        beanContext.registerSingleton(Executor.class, new Executor() {
            @Override
            void execute(Runnable command) {
                command.run()
            }
        }, Qualifiers.byName(TaskExecutors.IO))
        RefreshScope refreshScope = beanContext.getBean(RefreshScope.class)

        when:
        RefreshBean bean = beanContext.getBean(RefreshBean)

        then:
        bean.testValue() == 'test'
        bean.testConfigProps() == 'test'
        refreshScope.refreshableBeans.size() == 1
        refreshScope.locks.size() == 1

        when:
        System.setProperty("foo.bar", "bar")
        Environment environment = beanContext.getEnvironment()
        environment.refresh()
        beanContext.publishEvent(new RefreshEvent())

        then:
        bean.testValue() == 'bar'
        bean.testConfigProps() == 'bar'
        refreshScope.refreshableBeans.size() == 1
        refreshScope.locks.size() == 1

        cleanup:
        beanContext?.stop()
    }

    void "test fire refresh event that refreshes environment diff"() {
        given:
        System.setProperty("foo.bar", "test")
        System.setProperty("foos.test.bar", "test")
        System.setProperty("foo-list[0].bar", "test")
        ApplicationContext beanContext = ApplicationContext.builder().start()

        // override IO executor with synchronous impl
        beanContext.registerSingleton(Executor.class, new Executor() {
            @Override
            void execute(Runnable command) {
                command.run()
            }
        }, Qualifiers.byName(TaskExecutors.IO))

        when:
        RefreshBean bean = beanContext.getBean(RefreshBean)

        then:
        bean.hashCode() == bean.hashCode()
        bean.testValue() == 'test'
        bean.testConfigProps() == 'test'
        bean.testEachProps() == 'test'
        bean.testListEachProps() == 'test'


        when:
        System.setProperty("foo.bar", "bar")
        System.setProperty("foos.test.bar", "bar")
        System.setProperty("foo-list[0].bar", "bar")
        Environment environment = beanContext.getEnvironment()
        Map<String, Object> previousValues = environment.refreshAndDiff()
        beanContext.publishEvent(new RefreshEvent(previousValues))

        then:
        bean.testValue() == 'bar'
        bean.testConfigProps() == 'bar'
        bean.testEachProps() == 'bar'
        bean.testListEachProps() == 'bar'

        cleanup:
        beanContext?.stop()
    }

    void "test refresh event includes external files"() {
        File file = File.createTempFile("temp-config", ".yml")
        file.write("foo.bar: test")
        System.setProperty("micronaut.config.files", file.absolutePath)

        ApplicationContext beanContext = ApplicationContext.builder().start()

        // override IO executor with synchronous impl
        beanContext.registerSingleton(Executor.class, new Executor() {
            @Override
            void execute(Runnable command) {
                command.run()
            }
        }, Qualifiers.byName(TaskExecutors.IO))

        when:
        RefreshBean bean = beanContext.getBean(RefreshBean)

        then:
        bean.testValue() == 'test'
        bean.testConfigProps() == 'test'

        when:
        file.write("foo.bar: bar")
        Environment environment = beanContext.getEnvironment()
        environment.refresh()
        beanContext.publishEvent(new RefreshEvent())

        then:
        bean.testValue() == 'bar'
        bean.testConfigProps() == 'bar'

        cleanup:
        beanContext?.stop()
        file.delete()
    }

    void "test refresh event includes external files with the bootstrap environment"() {
        File file = File.createTempFile("temp-config", ".yml")
        file.write("foo.bar: test")
        System.setProperty("micronaut.config.files", file.absolutePath)
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)

        ApplicationContext beanContext = ApplicationContext.builder(["bootstrap-env": true]).start()

        // override IO executor with synchronous impl
        beanContext.registerSingleton(Executor.class, new Executor() {
            @Override
            void execute(Runnable command) {
                command.run()
            }
        }, Qualifiers.byName(TaskExecutors.IO))

        when:
        RefreshBean bean = beanContext.getBean(RefreshBean)

        then:
        bean.testValue() == 'test'
        bean.testConfigProps() == 'test'

        when:
        file.write("foo.bar: bar")
        Environment environment = beanContext.getEnvironment()
        environment.refresh()
        beanContext.publishEvent(new RefreshEvent())

        then:
        bean.testValue() == 'bar'
        bean.testConfigProps() == 'bar'

        cleanup:
        beanContext?.stop()
        file.delete()
    }

    @Refreshable
    static class RefreshBean {

        final MyConfig config
        final List<EachConfig> configs
        final List<EachConfig> moreConfigs

        @Value('${foo.bar}')
        String foo

        RefreshBean(MyConfig config, List<EachConfig> configs, List<EachListConfig> moreConfigs) {
            this.config = config
            this.configs = configs
            this.moreConfigs = moreConfigs
        }

        String testValue() {
            return foo
        }

        String testConfigProps() {
            return config.bar
        }

        String testEachProps() {
            return configs[0].bar
        }

        String testListEachProps() {
            return moreConfigs[0].bar
        }
    }

    @Refreshable("foo")
    static class RefreshBean2 {

        final MyConfig config
        final SecondConfig secondConfig
        @Value('${foo.bar}')
        String foo

        RefreshBean2(MyConfig config, SecondConfig secondConfig1) {
            this.config = config
            this.secondConfig = secondConfig1
        }

        @Override
        int hashCode() {
            return super.hashCode()
        }

        String testValue() {
            return foo
        }

        String testConfigProps() {
            return config.bar
        }

        String testSecondConfigProps() {
            return config.bar
        }
    }

    @ConfigurationProperties('foo')
    static class MyConfig {
        String bar
    }

    @ConfigurationProperties('second')
    static class SecondConfig {
        String bar = "default"
    }

    @EachProperty('foos')
    static class EachConfig {
        String bar
    }

    @EachProperty(value = 'foo-list', list = true)
    static class EachListConfig {
        String bar
    }
}
