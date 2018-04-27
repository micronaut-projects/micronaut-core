/*
 * Copyright 2018 original authors
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
package io.micronaut.configuration.hibernate.jpa

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import org.hibernate.SessionFactory
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.sql.DataSource

/**
 * @author graemerocher
 * @since 1.0
 */
class MultipleDataSourceJpaSetupSpec extends Specification{

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run(
            'datasources.default.name':'mydb',
            'datasources.other.name':'otherdb',
            'jpa.properties.hibernate.hbm2ddl.auto':'create-drop'
    )

    void "test multiple data sources setup"() {
        given:
        SessionFactory defaultSessionFactory = applicationContext.getBean(SessionFactory)
        SessionFactory otherSessionFactory = applicationContext.getBean(SessionFactory, Qualifiers.byName("other"))

        expect:
        defaultSessionFactory != otherSessionFactory
        defaultSessionFactory.jdbcServices.jdbcEnvironment.currentCatalog.toString() == "MYDB"
        otherSessionFactory.jdbcServices.jdbcEnvironment.currentCatalog.toString() == "OTHERDB"
    }
}
