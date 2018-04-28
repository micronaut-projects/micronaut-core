/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.context.exceptions.ConfigurationException
import io.micronaut.core.naming.NameUtils
import spock.lang.Specification

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
        env.getProperty("user", String).isPresent()

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
        env.getProperty("test.foo", Map.class).get() == [bar:"10", baz:"20"]

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
        env= env.refresh()

        then:
        env.getProperty("test.foo.bar", Integer).get() == 30
        env.getRequiredProperty("test.foo.bar", Integer) == 30
        env.getProperty("test.foo.bar", Integer, 20) == 30

        cleanup:
        System.setProperty("test.foo.bar", "")
        System.setProperty("test.bar.foo", "")
        System.setProperty("test.foo.baz", "")
    }

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
        File config = File.createTempFile("config-file", ".properties")
        config.write("foo.baz=50")
        System.setProperty("micronaut.config.files", "${config.absolutePath}")

        and:
        env = new DefaultEnvironment("test").start()

        then:
        env.getProperty("foo.baz", Integer).get() == 50

        when:"nothing is passed to micronaut.config.files"
        System.setProperty("micronaut.config.files", "")

        then: "should start normally"
        new DefaultEnvironment("test").start()
    }
}
