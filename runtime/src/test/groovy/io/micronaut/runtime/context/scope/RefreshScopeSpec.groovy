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
package io.micronaut.runtime.context.scope

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Value
import io.micronaut.context.env.Environment
import io.micronaut.core.util.StringUtils
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.runtime.context.scope.refresh.RefreshEvent
import io.micronaut.scheduling.TaskExecutors
import spock.lang.Specification

import java.util.concurrent.Executor

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class RefreshScopeSpec extends Specification {

    void "test fire refresh event that refreshes all"() {
        given:
        System.setProperty("foo.bar", "test")
        ApplicationContext beanContext = ApplicationContext.build().start()

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
        System.setProperty("foo.bar", "bar")
        Environment environment = beanContext.getEnvironment()
        environment.refresh()
        beanContext.publishEvent(new RefreshEvent())

        then:
        bean.testValue() == 'bar'
        bean.testConfigProps() == 'bar'

        cleanup:
        System.setProperty("foo.bar", "")
        beanContext?.stop()
    }

    void "test fire refresh event that refreshes environment diff"() {
        given:
        System.setProperty("foo.bar", "test")
        ApplicationContext beanContext = ApplicationContext.build().start()

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

        when:
        System.setProperty("foo.bar", "bar")
        Environment environment = beanContext.getEnvironment()
        Map<String, Object> previousValues = environment.refreshAndDiff()
        beanContext.publishEvent(new RefreshEvent(previousValues))

        then:
        bean.testValue() == 'bar'
        bean.testConfigProps() == 'bar'

        cleanup:
        System.setProperty("foo.bar", "")
        beanContext?.stop()
    }

    void "test refresh event includes external files"() {
        File file = File.createTempFile("temp-config", ".yml")
        file.write("foo.bar: test")
        System.setProperty("micronaut.config.files", file.absolutePath)

        ApplicationContext beanContext = ApplicationContext.build().start()

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
        System.setProperty("micronaut.config.files", "")
        beanContext?.stop()
        file.delete()
    }

    void "test refresh event includes external files with the bootstrap environment"() {
        File file = File.createTempFile("temp-config", ".yml")
        file.write("foo.bar: test")
        System.setProperty("micronaut.config.files", file.absolutePath)
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, StringUtils.TRUE)

        ApplicationContext beanContext = ApplicationContext.build(["bootstrap-env": true]).start()

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
        System.setProperty("micronaut.config.files", "")
        System.setProperty(Environment.BOOTSTRAP_CONTEXT_PROPERTY, "")
        beanContext?.stop()
        file.delete()
    }

    @Refreshable
    static class RefreshBean {

        final MyConfig config

        @Value('${foo.bar}')
        String foo

        RefreshBean(MyConfig config) {
            this.config = config
        }

        String testValue() {
            return foo
        }

        String testConfigProps() {
            return config.bar
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
}
