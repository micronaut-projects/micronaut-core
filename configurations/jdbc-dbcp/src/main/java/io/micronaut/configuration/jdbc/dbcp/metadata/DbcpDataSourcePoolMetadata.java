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

package io.micronaut.configuration.jdbc.dbcp.metadata;

import io.micronaut.jdbc.metadata.AbstractDataSourcePoolMetadata;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * {@link io.micronaut.jdbc.metadata.DataSourcePoolMetadata} for a DBCP {@link BasicDataSource}.
 *
 * @author Stephane Nicoll
 * @author Christian Oestreich
 * @since 1.0.0
 */
public class DbcpDataSourcePoolMetadata
        extends AbstractDataSourcePoolMetadata<BasicDataSource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbcpDataSourcePoolMetadata.class);

    /**
     * DBCP typed {@link io.micronaut.jdbc.metadata.DataSourcePoolMetadata} object.
     *
     * @param dataSource The datasource
     */
    public DbcpDataSourcePoolMetadata(BasicDataSource dataSource) {
        super(dataSource);
    }

    @Override
    public Integer getIdle() {
        return getConnectionPool()
                .map(GenericObjectPool::getNumIdle)
                .orElse(0);
    }

    @Override
    public Integer getActive() {
        return getConnectionPool()
                .map(GenericObjectPool::getNumActive)
                .orElse(0);
    }

    @Override
    public Integer getMax() {
        return getDataSource().getMaxTotal();
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
        return Optional.ofNullable(getDataSource().getDefaultAutoCommit()).orElse(false);
    }

    private Optional<GenericObjectPool> getConnectionPool() {
        return Optional.ofNullable(extractPool());
    }

    /**
     * Method to get the private property pool from {@link BasicDataSource}.  If this is exposed in the future, this will change.
     *
     * @return The {@link GenericObjectPool}
     */
    private GenericObjectPool extractPool() {
        GenericObjectPool pool = null;
        Field poolField;
        try {
            poolField = BasicDataSource.class.getDeclaredField("connectionPool");
            if (poolField != null) {
                poolField.setAccessible(true);
                pool = (GenericObjectPool) poolField.get(this.getDataSource());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Could not get pool from dbcp dataSource", e);
        }

        return pool;
    }
}
