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
package io.micronaut.configuration.jdbc.hikari.metadata;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.pool.HikariPool;
import io.micronaut.jdbc.metadata.AbstractDataSourcePoolMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Optional;

/**
 * {@link io.micronaut.jdbc.metadata.DataSourcePoolMetadata} for a Hikari {@link DataSource}.
 *
 * @author Stephane Nicoll
 * @author Christian Oestreich
 * @since 1.0.0
 */
public class HikariDataSourcePoolMetadata
        extends AbstractDataSourcePoolMetadata<HikariDataSource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(HikariDataSourcePoolMetadata.class);

    public HikariDataSourcePoolMetadata(HikariDataSource dataSource, String name) {
        super(dataSource, name);
    }

    @Override
    public Integer getIdle() {
        return getHikariPool()
                .map(HikariPool::getIdleConnections)
                .orElse(0);
    }

    @Override
    public Integer getActive() {
        return getHikariPool()
                .map(HikariPool::getActiveConnections)
                .orElse(0);
    }

    @Override
    public Integer getMax() {
        return getDataSource().getMaximumPoolSize();
    }

    @Override
    public Integer getMin() {
        return getDataSource().getMinimumIdle();
    }

    @Override
    public String getValidationQuery() {
        return getDataSource().getConnectionTestQuery();
    }

    @Override
    public Boolean getDefaultAutoCommit() {
        return getDataSource().isAutoCommit();
    }

    private Optional<HikariPool> getHikariPool() {
        return Optional.ofNullable(extractHikariPool());
    }

    private HikariPool extractHikariPool() {
        HikariPool pool = null;
        Field poolField;
        try {
            poolField = HikariDataSource.class.getDeclaredField("pool");
            if (poolField != null) {
                poolField.setAccessible(true);
                pool = (HikariPool) poolField.get(this.getDataSource());
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Could not get pool from hikari dataSource", e);
        }

        return pool;
    }

}
