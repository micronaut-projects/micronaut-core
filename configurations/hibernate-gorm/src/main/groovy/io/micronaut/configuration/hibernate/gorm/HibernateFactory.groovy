/*
 * Copyright 2017 original authors
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
package io.micronaut.configuration.hibernate.gorm

import grails.gorm.annotation.Entity
import groovy.transform.CompileStatic
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import org.grails.datastore.mapping.services.Service
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.connections.HibernateConnectionSource
import org.hibernate.SessionFactory
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.spring.core.env.PropertyResolverAdapter
import org.springframework.transaction.PlatformTransactionManager

import javax.inject.Singleton
import javax.sql.DataSource
import java.util.stream.Stream

/**
 * <p>A factory for configuring GORM for Hibernate 5 within Micronaut</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Factory
@CompileStatic
class HibernateFactory {

    final ApplicationContext applicationContext

    HibernateFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    @Bean(preDestroy = "close")
    @Context
    HibernateDatastore hibernateDatastore() {
        Stream<Class> entities = applicationContext.environment.scan(Entity)
        Class[] classes = entities.toArray() as Class[]
        HibernateDatastore datastore = new HibernateDatastore(new PropertyResolverAdapter(applicationContext, applicationContext),classes)
        for(o in datastore.getServices()) {
            applicationContext.registerSingleton(o)
        }
        for(o in datastore.getServices()) {
            applicationContext.inject(o)
        }

        return datastore
    }

    @Bean
    @Singleton
    SessionFactory sessionFactory(HibernateDatastore hibernateDatastore) {
        hibernateDatastore.getSessionFactory()
    }

    @Bean
    @Singleton
    DataSource dataSource(HibernateDatastore hibernateDatastore) {
        ((HibernateConnectionSource)hibernateDatastore.getConnectionSources().defaultConnectionSource).getDataSource()
    }

    @Bean
    @Singleton
    PlatformTransactionManager transactionManager(HibernateDatastore hibernateDatastore) {
        hibernateDatastore.getTransactionManager()
    }

}
