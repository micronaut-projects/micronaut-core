/*
 * Copyright 2012-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.configuration.jdbc.tomcat.metadata;

import io.micronaut.jdbc.metadata.AbstractDataSourcePoolMetadata;
import org.apache.tomcat.jdbc.pool.ConnectionPool;
import org.apache.tomcat.jdbc.pool.DataSource;

import java.util.Optional;

/**
 * {@link io.micronaut.jdbc.metadata.DataSourcePoolMetadata} for a Tomcat DataSource.
 *
 * @author Stephane Nicoll
 * @author Christian Oestreich
 * @since 1.0.0
 */
public class TomcatDataSourcePoolMetadata
        extends AbstractDataSourcePoolMetadata<DataSource> {

    private final ConnectionPool connectionPool;

    public TomcatDataSourcePoolMetadata(DataSource dataSource, String name) {
        super(dataSource, name);
        this.connectionPool = dataSource.getPool();
    }

    @Override
    public Integer getIdle() {
        return Optional.ofNullable(connectionPool).map(ConnectionPool::getIdle).orElse(0);
    }

    @Override
    public Integer getActive() {
        return Optional.ofNullable(connectionPool).map(ConnectionPool::getActive).orElse(0);
    }

    public long getBorrowed() {
        return Optional.ofNullable(connectionPool).map(ConnectionPool::getBorrowedCount).orElse(0L);
    }

    public long getReleasedCount() {
        return Optional.ofNullable(connectionPool).map(ConnectionPool::getReleasedCount).orElse(0L);
    }

    @Override
    public Integer getMax() {
        return getDataSource().getMaxActive();
    }

    @Override
    public Integer getMin() {
        return getDataSource().getMinIdle();
    }

    @Override
    public String getValidationQuery() {
        return getDataSource().getValidationQuery();
    }

    @Override
    public Boolean getDefaultAutoCommit() {
        return getDataSource().isDefaultAutoCommit();
    }
}