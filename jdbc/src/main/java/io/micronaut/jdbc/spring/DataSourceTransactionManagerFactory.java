/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jdbc.spring;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;
import io.micronaut.core.annotation.Internal;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.inject.Singleton;
import javax.sql.DataSource;

/**
 * Configures a {@link DataSourceTransactionManager} for each configured JDBC {@link DataSource}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@Requires(classes = DataSourceTransactionManager.class)
@Requires(condition = HibernatePresenceCondition.class)
@Internal
public class DataSourceTransactionManagerFactory {

    /**
     * For each {@link DataSource} add a {@link DataSourceTransactionManager} bean.
     *
     * @param dataSource The data source
     * @return The bean to add
     */
    @EachBean(DataSource.class)
    DataSourceTransactionManager dataSourceTransactionManager(
            DataSource dataSource) {
        DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(dataSource);
        dataSourceTransactionManager.afterPropertiesSet();
        return dataSourceTransactionManager;
    }

    /**
     * For each data source, wrap in a {@link TransactionAwareDataSourceProxy}.
     * @return The listener that will wrap each data source
     */
    @Singleton
    TransactionAwareDataSourceListener transactionAwareDataSourceListener() {
        return new TransactionAwareDataSourceListener();
    }

    /**
     * A {@link BeanCreatedEventListener} that wraps each data source in a transaction aware proxy.
     */
    private static class TransactionAwareDataSourceListener implements BeanCreatedEventListener<DataSource> {
        @Override
        public DataSource onCreated(BeanCreatedEvent<DataSource> event) {
            DataSource dataSource = event.getBean();
            if (dataSource instanceof TransactionAwareDataSourceProxy) {
                return dataSource;
            } else {
                return new TransactionAwareDataSourceProxy(dataSource);
            }
        }
    }
}
