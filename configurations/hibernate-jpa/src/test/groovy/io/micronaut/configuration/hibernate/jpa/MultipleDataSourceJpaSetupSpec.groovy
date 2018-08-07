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

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.spring.tx.annotation.Transactional
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.springframework.orm.hibernate5.HibernateTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import javax.persistence.EntityManager
import javax.persistence.PersistenceContext

/**
 * @author graemerocher
 * @since 1.0
 */
class MultipleDataSourceJpaSetupSpec extends Specification{

    @Shared @AutoCleanup ApplicationContext applicationContext = ApplicationContext.run(
            'datasources.default.url':'jdbc:h2:mem:mydb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
            'datasources.other.url':'jdbc:h2:mem:OTHERDB;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE',
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

    void "test multiple data source transactional"() {
        given:
        MultipleDataSourceService service = applicationContext.getBean(MultipleDataSourceService)
        MutipleDataSourceJavaService javaService = applicationContext.getBean(MutipleDataSourceJavaService)

        expect:"Methods that retrieve the current session don't throw an exception"
        service.testContextOther()
        service.testCurrent()
        service.testViaSF()
        service.testOther()
        service.testEM()
        service.testContext()
        javaService.testCurrent()
        javaService.testCurrentFromField()
    }

    @Singleton
    static class MultipleDataSourceService {
        @Inject
        @CurrentSession
        Session session

        @Inject
        @CurrentSession
        EntityManager em

        @Inject
        @CurrentSession("other")
        Session otherSession

        @Inject
        @Named("other")
        SessionFactory sessionFactory

        @PersistenceContext
        Session contextSession

        @PersistenceContext(name = "other")
        Session contextOther

        @Transactional
        boolean testCurrent() {
            session.clear()
            return true
        }

        @Transactional
        boolean testContext() {
            contextSession.clear()
            return true
        }

        @Transactional("other")
        boolean testContextOther() {
            contextOther.clear()
            return true
        }

        @Transactional
        boolean testEM() {
            em.clear()
            return true
        }

        @Transactional("other")
        boolean testOther() {
            otherSession.clear()
            return true
        }

        @Transactional("other")
        boolean testViaSF() {
            sessionFactory.currentSession.clear()
            return true
        }
    }
}
