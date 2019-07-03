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
import io.micronaut.core.naming.NameUtils
import spock.lang.Ignore
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

/**
 * Created by graemerocher on 12/06/2017.
 */
class DefaultEnvironmentSpec extends Specification {

    void "test environment system property resolve"() {

        given:
        System.setProperty("test.foo.bar", "10")
        Environment env = new DefaultEnvironment("test").start()
        expect:
        env.getProperty("test.foo.bar", Integer).get() == 10
        env.getRequiredProperty("test.foo.bar", Integer) == 10
        env.getProperty("test.foo.bar", Integer, 20) == 10

        cleanup:
        System.setProperty("test.foo.bar", "")
    }

    void "test environment sub property resolve"() {

        given:
        System.setProperty("test.foo.bar", "10")
        System.setProperty("test.bar.foo", "30")
        System.setProperty("test.foo.baz", "20")
        Environment env = new DefaultEnvironment("test").start()

        expect:
        env.getProperty("test.foo", Map.class).get() == [bar: "10", baz: "20"]

        cleanup:
        System.setProperty("test.foo.bar", "")
        System.setProperty("test.bar.foo", "")
        System.setProperty("test.foo.baz", "")
    }

    void "test environment system property refresh"() {

        when:
        System.setProperty("test.foo.bar", "10")
        Environment env = new DefaultEnvironment("test").start()

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

        cleanup:
        System.setProperty("test.foo.bar", "")
        System.setProperty("test.bar.foo", "")
        System.setProperty("test.foo.baz", "")
    }

    @Ignore //TODO: Ignoring temporarily
    @RestoreSystemProperties
    void "test getting environments from a system property"() {
        when:
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "foo ,x")
        Environment env = new DefaultEnvironment("test").start()

        then:
        env.activeNames.contains("foo")
        env.activeNames.contains("x")
        //coming from application-foo.yml
        env.getProperty("foo", String).get() == "bar"

        when:
        System.setProperty(Environment.ENVIRONMENTS_PROPERTY, "")
        env = new DefaultEnvironment("test").start()

        then:
        !env.activeNames.contains("foo")
        !env.activeNames.contains("x")
        !env.containsProperty("foo")
    }

    void "test system property source loader"() {
        given: "a configuration property file is passed from system properties"
        File configPropertiesFile = File.createTempFile("default", ".properties")
        configPropertiesFile.write("foo=bar")
        System.setProperty("micronaut.config.files", "${configPropertiesFile.absolutePath}")

        when: "load the property sources"
        Environment env = new DefaultEnvironment("test").start()

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

        when: "file from system property source loader override the key"
        System.setProperty("foo.baz", "10")
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=50")
        System.setProperty("micronaut.config.files", "${configPropertiesFile.absolutePath}")

        and:
        env = new DefaultEnvironment("test").start()

        then:
        env.getProperty("foo.baz", Integer).get() == 50

        when: "nothing is passed to micronaut.config.files"
        System.setProperty("micronaut.config.files", "")

        then: "should start normally"
        new DefaultEnvironment("test").start()

        when: "file is is passed as file:path"
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=100")
        System.setProperty("micronaut.config.files", "file:${configPropertiesFile.absolutePath}")

        and:
        env = new DefaultEnvironment("test").start()

        then: "property is set"
        env.getProperty("foo.baz", Integer).get() == 100

        cleanup:
        System.clearProperty("foo")
        System.clearProperty("foo.bar")
        System.clearProperty("foo.baz")
        System.clearProperty("micronaut.config.files")

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

        when: "file from system property source loader override the key"
        System.setProperty("foo.baz", "10")
        configPropertiesFile = File.createTempFile("config-file", ".properties")
        configPropertiesFile.write("foo.baz=50")

        and:
        env = startEnv("${configPropertiesFile.absolutePath}")

        then:
        env.getProperty("foo.baz", Integer).get() == 50

        expect: "when nothing is passed to micronaut.config.files then should start normally"
        startEnv("")

        when: "file is is passed as file:path"
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
    def "constructor(String... names) should preserve order specified in micronaut.environments system property"() {
        given: "set environments system property"
        System.setProperty('micronaut.environments', 'cloud, ec2, foo, bar, foo,baz,ec2,cloud,cloud')

        and: "setup environment"
        def env = new DefaultEnvironment("x", "x", "y")

        when: "create environment and fetch active env names"
        def envNames = env.getActiveNames().toList()

        then: "env names should be in the same order as defined in micronaut.environment variable, with test env first"
        envNames == ["test", "cloud", "ec2", "foo", "bar", "baz", "x", "y"]
    }

    @RestoreSystemProperties
    void "test environments supplied should be a higher priority than deduced and system property"() {
        when:
        def env = new DefaultEnvironment()

        then:
        env.activeNames.size() == 1
        env.activeNames[0] == "test"

        when:
        env = new DefaultEnvironment("explicit")

        then:
        env.activeNames.size() == 2
        env.activeNames[0] == "test"
        env.activeNames[1] == "explicit"

        when:
        System.setProperty("micronaut.environments", "system,property")
        env = new DefaultEnvironment("explicit")

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
        ApplicationContext ctx = ApplicationContext.build().deduceEnvironment(false).start()

        then:
        !ctx.environment.activeNames.contains(Environment.TEST)

        cleanup:
        ctx.close()
    }
    // end::disableEnvDeduction[]

    @RestoreSystemProperties
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
        Environment env = new DefaultEnvironment("test").start()
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

    private static Environment startEnv(String files) {
        new DefaultEnvironment("test") {
            protected String readPropertySourceListKeyFromEnvironment() {
                files
            }
        }.start()
    }
}
