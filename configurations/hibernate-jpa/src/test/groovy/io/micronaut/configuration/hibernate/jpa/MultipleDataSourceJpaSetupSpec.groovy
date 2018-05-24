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
package io.micronaut.configuration.hibernate.jpa

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import org.hibernate.SessionFactory
import org.springframework.orm.hibernate5.HibernateTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * @author graemerocher
 * @since 1.0
 */
class MultipleDataSourceJpaSetupSpec extends Specification{

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run(
            'datasources.default.name':'mydb',
            'datasources.other.name':'otherdb',
            'jpa.other.packages-to-scan':'io.micronaut.configuration.hibernate.jpa.other',
            'jpa.default.properties.hibernate.hbm2ddl.auto':'create-drop'
    )

    void "test multiple data sources setup"() {
        given:
        SessionFactory defaultSessionFactory = applicationContext.getBean(SessionFactory)
        HibernateTransactionManager defaultTxManager = applicationContext.getBean(HibernateTransactionManager)

        SessionFactory otherSessionFactory = applicationContext.getBean(SessionFactory, Qualifiers.byName("other"))
        HibernateTransactionManager otherTxManager = applicationContext.getBean(HibernateTransactionManager, Qualifiers.byName("other"))

        expect:
        defaultSessionFactory != otherSessionFactory
        defaultSessionFactory.getMetamodel().entity(Book)
        otherSessionFactory.getMetamodel().entities.isEmpty()
        defaultTxManager.sessionFactory == defaultSessionFactory
        otherTxManager.sessionFactory == otherSessionFactory
        defaultSessionFactory.jdbcServices.jdbcEnvironment.currentCatalog.toString() == "MYDB"
        otherSessionFactory.jdbcServices.jdbcEnvironment.currentCatalog.toString() == "OTHERDB"
    }
}
