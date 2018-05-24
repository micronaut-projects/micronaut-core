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
package io.micronaut.jdbc

import io.micronaut.context.exceptions.ConfigurationException
import spock.lang.Specification

class CalculatedSettingsSpec extends Specification {

    void "test getDriverClassName can't be found"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredDriverClassName() >> "foo"
            1 * getName() >> "bar"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        settings.getDriverClassName()

        then:
        def ex = thrown(ConfigurationException)
        ex.message == "Error configuring data source 'bar'. The driver class 'foo' was not found on the classpath"
    }

    void "test getDriverClassName is calculated from the URL"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredDriverClassName() >> null
            1 * getUrl() >> "jdbc:as400:asdfasdf"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String driverClassName = settings.getDriverClassName()

        then:
        driverClassName == "com.ibm.as400.access.AS400JDBCDriver"
    }

    void "test getDriverClassName will search for an embedded database on the classpath"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredDriverClassName() >> null
            1 * getUrl() >> null
        }
        URL h2Jar = this.class.classLoader.getResource("h2.jar")
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration, new URLClassLoader(h2Jar))

        when:
        String driverClassName = settings.getDriverClassName()

        then:
        driverClassName == "org.h2.Driver"
    }

    void "test getDriverClassName will throw an exception if no driver is found"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredDriverClassName() >> null
            1 * getUrl() >> null
            1 * getName() >> "bar"
        }
        URL mysqlJar = this.class.classLoader.getResource("mysql.jar") //mysql is not embedded
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration, new URLClassLoader(mysqlJar))

        when:
        settings.getDriverClassName()

        then:
        def ex = thrown(ConfigurationException)
        ex.message == "Error configuring data source 'bar'. No driver class name specified"
    }

    void "test getUrl will return the configured url"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredUrl() >> "foo"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String url = settings.getUrl()

        then:
        url == "foo"
    }

    void "test getUrl will look for an embedded database is found on the classpath"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredUrl() >> null
            1 * getName() >> "bar"
        }
        URL h2Jar = this.class.classLoader.getResource("h2.jar")
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration, new URLClassLoader(h2Jar))

        when:
        String url = settings.getUrl()

        then:
        url == "jdbc:h2:mem:bar;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    }

    void "test getUrl will use a default name"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredUrl() >> null
            1 * getName() >> null
        }
        URL h2Jar = this.class.classLoader.getResource("h2.jar")
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration, new URLClassLoader(h2Jar))

        when:
        String url = settings.getUrl()

        then:
        url == "jdbc:h2:mem:devDb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    }

    void "test getUrl will throw an exception if its not configured and a driver can't be found"() {
        given:
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredUrl() >> null
            1 * getName() >> "bar"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        settings.getUrl()

        then:
        def ex = thrown(ConfigurationException)
        ex.message == "Error configuring data source 'bar'. No URL specified"
    }

    void "test getUsername returns the configured username"() {
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredUsername() >> "user"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String username = settings.getUsername()

        then:
        username == "user"
    }

    void "test getUsername returns 'sa' if the driver is embedded"() {
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredUsername() >> null
            1 * getDriverClassName() >> driver
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String username = settings.getUsername()

        then:
        username == "sa"

        where:
        driver                                 | _
        "org.h2.Driver"                        | _
        "org.apache.derby.jdbc.EmbeddedDriver" | _
        "org.hsqldb.jdbc.JDBCDriver"           | _
    }

    void "test getPassword returns the configured password"() {
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredPassword() >> "pw"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String password = settings.getPassword()

        then:
        password == "pw"
    }

    void "test getPassword returns an empty string if the driver is embedded"() {
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredPassword() >> null
            1 * getDriverClassName() >> driver
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String password = settings.getPassword()

        then:
        password == ""

        where:
        driver                                 | _
        "org.h2.Driver"                        | _
        "org.apache.derby.jdbc.EmbeddedDriver" | _
        "org.hsqldb.jdbc.JDBCDriver"           | _
    }

    void "test getValidationQuery returns the configured value"() {
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredValidationQuery() >> "x"
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String validationQuery = settings.getValidationQuery()

        then:
        validationQuery == "x"
    }

    void "test getValidationQuery searches databases based on the url"() {
        BasicJdbcConfiguration basicConfiguration = Mock(BasicJdbcConfiguration) {
            1 * getConfiguredValidationQuery() >> null
            1 * getConfiguredUrl() >> url
        }
        CalculatedSettings settings = new CalculatedSettings(basicConfiguration)

        when:
        String validationQuery = settings.getValidationQuery()

        then:
        validationQuery == query

        where:
        url              | query
        "jdbc:as400:x"   | "SELECT 1 FROM SYSIBM.SYSDUMMY1"
        "jdbc:mariadb:x" | "SELECT 1"
        "jdbc:oracle:x"  | "SELECT 1 FROM DUAL"
    }
}
