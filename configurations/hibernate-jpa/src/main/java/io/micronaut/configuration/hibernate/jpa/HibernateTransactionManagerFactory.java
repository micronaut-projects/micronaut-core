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
package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.hibernate.SessionFactory;
import org.springframework.orm.hibernate5.HibernateTransactionManager;

import javax.sql.DataSource;

/**
 * Sets up the default hibernate transaction manager
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@EachBean(DataSource.class)
@Requires(classes = HibernateTransactionManager.class)
public class HibernateTransactionManagerFactory {

    @Bean
    @Requires(classes = HibernateTransactionManager.class)
    HibernateTransactionManager hibernateTransactionManager(SessionFactory sessionFactory, DataSource dataSource) {
        HibernateTransactionManager hibernateTransactionManager = new HibernateTransactionManager(sessionFactory);
        hibernateTransactionManager.setDataSource(dataSource);
        return hibernateTransactionManager;
    }
}
