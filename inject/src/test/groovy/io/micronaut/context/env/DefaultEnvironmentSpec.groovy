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
import io.micronaut.context.ApplicationContextConfiguration
import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.naming.NameUtils
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Created by graemerocher on 12/06/2017.
 */
@RestoreSystemProperties
class DefaultEnvironmentSpec extends Specification {

    void "test environment system property resolve"() {
        given:
        System.setProperty("test.foo.bar", "10")
        Environment env = new DefaultEnvironment({ ["test"] }).start()

        expect:
        env.getProperty("test.foo.bar", Integer).get() == 10
        env.getRequiredProperty("test.foo.bar", Integer) == 10
        env.getProperty("test.foo.bar", Integer, 20) == 10
    }

    void "test environment sub property resolve"() {
        given:
        System.setProperty("test.foo.bar", "10")
        System.setProperty("test.bar.foo", "30")
        System.setProperty("test.foo.baz", "20")
        Environment env = new DefaultEnvironment({ ["test"] }).start()

        expect:
        env.getProperty("test.foo", Map.class).get() == [bar: "10", baz: "20"]
    }

    void "test environment system property refresh"() {
        when:
        System.setProperty("test.foo.bar", "10")
        Environment env = new DefaultEnvironment({ ["test"] }).start()

        then:
        env.getProperty("test.foo.bar", Integer).get() == 10
        env.getRequiredProperty("test.foo.bar", Integer) == 10
        env.getProperty("test.foo.bar", Integer, 20) == 10

        when:
        System.setProperty("test.foo.bar", "30")
        env = env.refresh()

        then:
        env.getProperty("test.foo.bar", Integer).get() == 30
        env.getRequiredProperty("test.foo.bar", Integer) == 30
        env.getProperty("test.foo.bar", Integer, 20) == 30
    }

    void "test getting environments from a system property"() {
        when:
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "foo ,x")
        Environment env = new DefaultEnvironment({ ["test"] }).start()

        then:
        env.activeNames.contains("foo")
        env.activeNames.contains("x")
        //coming from application-foo.yml
        env.getProperty("foo", String).get() == "bar"

        when:
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "")
        env = new DefaultEnvironment({ ["test"] }).start()

        then:
        !env.activeNames.contains("foo")
        !env.activeNames.contains("x")
        !env.containsProperty("foo")
    }

    void "test system property source loader"() {
        given: "a configuration property file is passed from system properties"
        File configPropertiesFile = File.createTempFile("default", ".properties")
        configPropertiesFile.write("foo=bar\n[baz]=bar2")
        System.setProperty("micronaut.config.files", "${configPropertiesFile.absolutePath}")

        when: "load the property sources"
        Environment env = new DefaultEnvironment({ ["test"] }).start()

        then: "should be loaded from property source"
        env.getProperty("foo", String).get() == "bar"

        when: "multiple files are passed from system properties"
        File anotherFile = File.createTempFile("config-file", ".properties")
        anotherFile.write("testname=DefaultEnvironmentSpec")
        System.setProperty("micronaut.config.files", "${configPropertiesFile.absolutePath},${anotherFile.absolutePath}")

        and: "load the property sources"
        env.refresh()

        then: "should load values from all files"
        env.getProperty("foo", String).get() == "bar"
        env.getProperty("testname", String).get() == "DefaultEnvironmentSpec"

        when: "propertySource loader does not exists"
        File unsupportedFile = File.createTempFile("unsupported", ".xml")
        anotherFile.write("""
<?xml version="1.0" encoding="UTF-8"?>
<foo>bar</foo>
""")
        System.setProperty("micronaut.config.files", "${unsupportedFile.absolutePath}")

        and: "load the property sources"
        env.refresh()

        then: "should throw exception"
        def e = thrown(ConfigurationException)
        e.message == "Unsupported properties file format: " + NameUtils.filename(unsupportedFile.absolutePath)

        when: "file from system property source loader does not override the key"
        System.setProperty("foo.baz", "10")
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=50")
        System.setProperty("micronaut.config.files", "${configPropertiesFile.absolutePath}")

        and:
        env = new DefaultEnvironment({ ["test"] }).start()

        then:
        env.getProperty("foo.baz", Integer).get() == 10

        when: "nothing is passed to micronaut.config.files"
        System.setProperty("micronaut.config.files", "")

        then: "should start normally"
        new DefaultEnvironment({ ["test"] }).start()

        when: "file is is passed as file:path"
        System.clearProperty("foo.baz")
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=100")
        System.setProperty("micronaut.config.files", "file:${configPropertiesFile.absolutePath}")

        and:
        env = new DefaultEnvironment({ ["test"] }).start()

        then: "property is set"
        env.getProperty("foo.baz", Integer).get() == 100
    }

    void "test system env source loader"() {
        given: "a configuration property file is passed from system properties"
        File configPropertiesFile = File.createTempFile("default", ".properties")
        configPropertiesFile.write("foo=bar")

        when: "load the property sources"
        Environment env = startEnv(configPropertiesFile.absolutePath)

        then: "should be loaded from property source"
        env.getProperty("foo", String).get() == "bar"

        when: "multiple files are passed from system properties"
        File anotherFile = File.createTempFile("config-file", ".properties")
        anotherFile.write("testname=DefaultEnvironmentSpec")

        and: "load the property sources"
        env = startEnv("${configPropertiesFile.absolutePath},${anotherFile.absolutePath}")

        then: "should load values from all files"
        env.getProperty("foo", String).get() == "bar"
        env.getProperty("testname", String).get() == "DefaultEnvironmentSpec"

        when: "propertySource loader does not exists"
        File unsupportedFile = File.createTempFile("unsupported", ".xml")
        anotherFile.write("""
 <?xml version="1.0" encoding="UTF-8"?>
 <foo>bar</foo>
 """)

        and: "load the property sources"
        startEnv("${unsupportedFile.absolutePath}")

        then: "should throw exception"
        def e = thrown(ConfigurationException)
        e.message == "Unsupported properties file format: " + NameUtils.filename(unsupportedFile.absolutePath)

        when: "file from system property source loader does not override the key"
        System.setProperty("foo.baz", "10")
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=50")

        and:
        env = startEnv("${configPropertiesFile.absolutePath}")

        then:
        env.getProperty("foo.baz", Integer).get() == 10

        expect: "when nothing is passed to micronaut.config.files then should start normally"
        startEnv("")

        when: "file is is passed as file:path"
        System.clearProperty("foo.baz")
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=100")

        and:
        env = startEnv("file:${configPropertiesFile.absolutePath}")

        then: "property is set"
        env.getProperty("foo.baz", Integer).get() == 100

        cleanup:
        System.clearProperty("foo")
        System.clearProperty("foo.bar")
        System.clearProperty("foo.baz")
        System.clearProperty("micronaut.config.files")
    }

    void "test loading property sources from both system and env"() {
        given: "a configuration property file is passed from system properties"
        File configPropertiesFile = File.createTempFile("default", ".properties")
        configPropertiesFile.write("foo.test=bar")

        and: "another file to be passed via env variable"
        File anotherFile = File.createTempFile("default-env", ".properties")
        anotherFile.write("foo.baz=12")

        when: "loading properties sources from both system properties and environment variables"
        System.setProperty("micronaut.config.files", configPropertiesFile.absolutePath)
        Environment env = startEnv(anotherFile.absolutePath)

        then: "properties should be set from both loaders"
        env.getProperty("foo.test", String).get() == "bar"
        env.getProperty("foo.baz", Integer).get() == 12

        when: "files in system properties and env variables have same key"
        configPropertiesFile = File.createTempFile("default-system", ".properties")
        configPropertiesFile.write("foo.baz=100")
        anotherFile = File.createTempFile("default-env", ".properties")
        anotherFile.write("foo.baz=112")

        System.setProperty("micronaut.config.files", configPropertiesFile.absolutePath)
        env = startEnv(anotherFile.absolutePath)

        then:
        env.getProperty("foo.baz", Integer).get() == 112

        cleanup:
        System.clearProperty("foo")
        System.clearProperty("foo.test")
        System.clearProperty("foo.baz")
        System.clearProperty("micronaut.config.files")
    }

    @RestoreSystemProperties
    void "test invalid config file location"() {
        when: "loading properties sources from both system properties and environment variables"
        System.setProperty("micronaut.config.files", "/does/not/exist.yaml")
        new DefaultEnvironment({ ["test"] }).start()

        then:
        def ex = thrown(ConfigurationException)
        ex.message == "Failed to read configuration file: /does/not/exist.yaml"
    }

    def "constructor(String... names) should preserve order specified in micronaut.environments system property"() {
        given: "set environments system property"
        System.setProperty('micronaut.environments', 'cloud, ec2, foo, bar, foo,baz,ec2,cloud,cloud')

        and: "setup environment"
        def env = new DefaultEnvironment({ ["x", "x", "y"] })

        when: "create environment and fetch active env names"
        def envNames = env.getActiveNames().toList()

        then: "env names should be in the same order as defined in micronaut.environment variable, with test env first"
        envNames == ["test", "cloud", "ec2", "foo", "bar", "baz", "x", "y"]
    }

    @RestoreSystemProperties
    void "test environments supplied should be a higher priority than deduced and system property"() {
        when:
        def env = new DefaultEnvironment({ [] })

        then:
        env.activeNames.size() == 1
        env.activeNames[0] == "test"

        when:
        env = new DefaultEnvironment({ ["explicit"] })

        then:
        env.activeNames.size() == 2
        env.activeNames[0] == "test"
        env.activeNames[1] == "explicit"

        when:
        System.setProperty("micronaut.environments", "system,property")
        env = new DefaultEnvironment({["explicit"]})

        then:
        env.activeNames.size() == 4
        env.activeNames[0] == "test"
        env.activeNames[1] == "system"
        env.activeNames[2] == "property"
        env.activeNames[3] == "explicit"
    }

    // tag::disableEnvDeduction[]
    void "test disable environment deduction via builder"() {
        when:
        ApplicationContext ctx = ApplicationContext.builder().deduceEnvironment(false).start()

        then:
        !ctx.environment.activeNames.contains(Environment.TEST)

        cleanup:
        ctx.close()
    }
    // end::disableEnvDeduction[]

    void "test disable environment deduction via system property"() {
        when:
        System.setProperty(Environment.CLOUD_PLATFORM_PROPERTY, "GOOGLE_COMPUTE")
        ApplicationContext ctx1 = ApplicationContext.run()

        then:
        ctx1.environment.activeNames.contains(Environment.GOOGLE_COMPUTE)

        when:
        System.setProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY, "false")
        ApplicationContext ctx2 = ApplicationContext.run()

        then:
        !ctx2.environment.activeNames.contains(Environment.GOOGLE_COMPUTE)

        cleanup:
        System.setProperty(Environment.DEDUCE_ENVIRONMENT_PROPERTY, "")
        System.setProperty(Environment.CLOUD_PLATFORM_PROPERTY, "")

        ctx1.close()
        ctx2.close()
    }

    void "test add and remove property sources"() {
        given:
        Environment env = new DefaultEnvironment({["test"]}).start()
        PropertySource propertySource = PropertySource.of("test", [foo: 'bar'])

        when:
        env.addPropertySource(propertySource)

        then:
        env.propertySources.contains(propertySource)

        when:
        env.removePropertySource(propertySource)

        then:
        !env.propertySources.contains(propertySource)
    }

    void "test custom property source is not removed after refresh"() {
        ApplicationContext context = ApplicationContext.builder(['static': true]).start()

        expect:
        context.getRequiredProperty("static", Boolean.class)

        when:
        context.getEnvironment().refresh()

        then:
        context.getRequiredProperty("static", Boolean.class)

        cleanup:
        context.close()
    }

    void "test custom property source is not removed after refresh 2"() {
        def env = new DefaultEnvironment({["test"]})
        env.addPropertySource(new MapPropertySource('static', [static: true]))
        env.start()

        expect:
        env.getRequiredProperty("static", Boolean.class)

        when:
        env.refresh()

        then:
        env.getRequiredProperty("static", Boolean.class)

        cleanup:
        env.close()
    }

    @RestoreSystemProperties
    void "test property source order"() {
        when:
        System.setProperty("micronaut.config.files", "classpath:config-files.yml,classpath:config-files2.yml")
        System.setProperty("config.prop", "system-property")
        Environment env = SystemLambda.withEnvironmentVariable("CONFIG_PROP", "env-var")
                .execute(() -> {
                    new DefaultEnvironment({["first", "second"]}).start()
                })

        then: "System properties have highest precedence"
        env.getRequiredProperty("config.prop", String.class) == "system-property"

        when:
        System.clearProperty("config.prop")
        env = SystemLambda.withEnvironmentVariable("CONFIG_PROP", "env-var")
                .execute(() -> {
                    new DefaultEnvironment({["first", "second"]}).start()
                })

        then: "Environment variables have next highest precedence"
        env.getRequiredProperty("config.prop", String.class) == "env-var"

        when:
        env = new DefaultEnvironment({["first", "second"]}).start()

        then: "Config files last in the list have precedence over those first in the list"
        env.getRequiredProperty("config.prop", String.class) == "config-files2.yml"

        when:
        System.setProperty("micronaut.config.files", "classpath:config-files.yml")
        env = new DefaultEnvironment({["first", "second"]}).start()

        then: "Config files have precedence over application-*.* files"
        env.getRequiredProperty("config.prop", String.class) == "config-files.yml"

        when:
        System.clearProperty("micronaut.config.files")
        env = new DefaultEnvironment({["first", "second"]}).start()

        then: "Environments last in the list have precedence over those first in the list"
        env.getRequiredProperty("config.prop", String.class) == "application-second.yml"

        when:
        env = new DefaultEnvironment({["first"]}).start()

        then: "Environments files have precedence over normal files"
        env.getRequiredProperty("config.prop", String.class) == "application-first.yml"

        when:
        env = new DefaultEnvironment({[]}).start()

        then: "Normal application files have the least precedence with config folder being last"
        env.getRequiredProperty("config.prop", String.class) == "application.yml"
        env.getRequiredProperty("config-folder-prop", String.class) == "abc"
    }

    void "test custom config locations"() {
        when:
            ApplicationContext applicationContext = ApplicationContext.builder()
                    .overrideConfigLocations("file:./custom-config/", "classpath:custom-config/")
                    .build()
                    .start()

        then: "Normal application files have the least precedence with config folder being last"
            applicationContext.getRequiredProperty("config.prop", String.class) == "file:./custom-config/application.yml"
            applicationContext.getRequiredProperty("custom-config-classpath", String.class) == "xyz"
            applicationContext.getRequiredProperty("custom-config-file", String.class) == "abc"
        cleanup:
            applicationContext.stop()
    }

    void "test custom config locations respect environment order"() {
        when:
            ApplicationContext applicationContext = ApplicationContext.builder()
                    .overrideConfigLocations("file:./custom-config/", "classpath:custom-config/")
                    .environments("env1", "env2")
                    .build()
                    .start()

        then: "Environment order establishes property value"
            applicationContext.getRequiredProperty("config.prop", String.class) == "file:./custom-config/application-env2.yml"
            applicationContext.getRequiredProperty("custom-config-classpath", String.class) == "xyz"
            applicationContext.getRequiredProperty("custom-config-file", String.class) == "env2"
        cleanup:
            applicationContext.stop()
    }

    void "test custom config locations respect environment order - reversed"() {
        when:
            ApplicationContext applicationContext = ApplicationContext.builder()
                    .overrideConfigLocations("file:./custom-config/", "classpath:custom-config/")
                    .environments("env2", "env1")
                    .build()
                    .start()

        then: "Environment order establishes property value"
            applicationContext.getRequiredProperty("config.prop", String.class) == "file:./custom-config/application-env1.yml"
            applicationContext.getRequiredProperty("custom-config-classpath", String.class) == "xyz"
            applicationContext.getRequiredProperty("custom-config-file", String.class) == "env1"
        cleanup:
            applicationContext.stop()
    }

    void "test custom config locations - envrionment variables take precedence"() {
        when:
            ApplicationContext applicationContext = SystemLambda.withEnvironmentVariable("CONFIG_PROP", "from-env").execute(() -> {
                ApplicationContext.builder()
                    .overrideConfigLocations("file:./custom-config/", "classpath:custom-config/")
                    .environments("env1", "env2")
                    .build()
                    .start()
            })

        then: "values passed as environment variables take precedence"
            applicationContext.getRequiredProperty("custom-config-classpath", String.class) == "xyz"
            applicationContext.getRequiredProperty("custom-config-file", String.class) == "env2"
            applicationContext.getRequiredProperty("config.prop", String.class) == "from-env"

        cleanup:
            applicationContext.stop()
    }

    void "test custom config locations - system properties take precedence over env"() {
        when:
        ApplicationContext applicationContext = SystemLambda.withEnvironmentVariable("CONFIG_PROP", "from-env").execute(() -> {
                ApplicationContext.builder()
                    .overrideConfigLocations("file:./custom-config/", "classpath:custom-config/")
                    .properties(["config.prop": "from-properties"])
                    .environments("env1", "env2")
                    .build()
                    .start()
            })

        then: "values from properties take precedence"
            applicationContext.getRequiredProperty("custom-config-classpath", String.class) == "xyz"
            applicationContext.getRequiredProperty("custom-config-file", String.class) == "env2"
            applicationContext.getRequiredProperty("config.prop", String.class) == "from-properties"

        cleanup:
            applicationContext.stop()
    }

    void "test specified names have precedence, even if deduced"() {
        when:
        Environment env = new DefaultEnvironment({[]}).start()

        then:
        env.activeNames == ["test"] as Set

        when:
        env = SystemLambda.withEnvironmentVariable("MICRONAUT_ENVIRONMENTS", "first,second,third")
        .execute(() -> {
            new DefaultEnvironment({[]}).start()
        })

        then: // env has priority over deduced
        env.activeNames == ["test", "first", "second", "third"] as Set

        when:
        env = SystemLambda.withEnvironmentVariable("MICRONAUT_ENVIRONMENTS", "first,second,third")
                .execute(() -> {
                    new DefaultEnvironment({["specified"]}).start()
                })

        then: // specified has priority over env
        env.activeNames == ["test", "first", "second", "third", "specified"] as Set

        when:
        env = SystemLambda.withEnvironmentVariable("MICRONAUT_ENVIRONMENTS", "first,second,third")
                .execute(() -> {
                    new DefaultEnvironment({["second"]}).start()
                })

        then: // specified has priority over env, even if already set in env
        env.activeNames == ["test", "first", "third", "second"] as Set
    }

    void "test the default environment is applied"() {
        when: 'environment deduction is on'
        Environment env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return []
            }

            @Override
            List<String> getDefaultEnvironments() {
                return ['default']
            }
        }).start()

        then: 'an environment is deduced so the default is not applied'
        env.activeNames == ['test'] as Set

        when: 'environment deduction is off'
        env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return []
            }

            @Override
            List<String> getDefaultEnvironments() {
                return ['default']
            }

            @Override
            Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false)
            }
        }).start()

        then: 'the default is applied'
        env.activeNames == ['default'] as Set

        when: 'an environment is specified'
        env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return ['foo']
            }

            @Override
            List<String> getDefaultEnvironments() {
                return ['default']
            }

            @Override
            Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false)
            }
        }).start()

        then: 'the default environment is not applied'
        env.activeNames == ['foo'] as Set

        when: 'an environment is specified through env var'
        SystemLambda.withEnvironmentVariable("MICRONAUT_ENVIRONMENTS", "bar")
                .execute(() -> {
                    env = new DefaultEnvironment(new ApplicationContextConfiguration() {
                        @Override
                        List<String> getEnvironments() {
                            return []
                        }

                        @Override
                        List<String> getDefaultEnvironments() {
                            return ['default']
                        }

                        @Override
                        Optional<Boolean> getDeduceEnvironments() {
                            return Optional.of(false)
                        }
                    }).start()
                })

        then: 'the default environment is not applied'
        env.activeNames == ['bar'] as Set

        when: 'an environment is specified through a system prop'
        System.setProperty('micronaut.environments', 'xyz')
        env = new DefaultEnvironment(new ApplicationContextConfiguration() {
            @Override
            List<String> getEnvironments() {
                return []
            }

            @Override
            List<String> getDefaultEnvironments() {
                return ['default']
            }

            @Override
            Optional<Boolean> getDeduceEnvironments() {
                return Optional.of(false)
            }
        }).start()

        then: 'the default environment is not applied'
        env.activeNames == ['xyz'] as Set
    }

    private static Environment startEnv(String files) {
        new DefaultEnvironment({["test"]}) {
            @Override
            protected String readPropertySourceListKeyFromEnvironment() {
                files
            }
        }.start()
    }
}
