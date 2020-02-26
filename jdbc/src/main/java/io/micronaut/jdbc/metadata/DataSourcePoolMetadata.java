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
package io.micronaut.jdbc.metadata;

/**
 * Provides access meta-data that is commonly available from most pooled
 * {@link javax.sql.DataSource} implementations.
 *
 * @param <T> datasource {@link javax.sql.DataSource}
 * @author Stephane Nicoll
 * @author Christian Oestreich
 * @since 2.0.0
 */
public interface DataSourcePoolMetadata<T extends javax.sql.DataSource> {

    /**
     * Allow implementations to get the datasource for usage in metrics, etc.
     *
     * @return the datasource
     */
    T getDataSource();

    /**
     * Return the number of idle connections in the pool.
     *
     * @return the idle value
     */
    Integer getIdle();

    /**
     * Return the usage of the pool as value between 0 and 1 (or -1 if the pool is not
     * limited).
     * <ul>
     * <li>1 means that the maximum number of connections have been allocated</li>
     * <li>0 means that no connection is currently active</li>
     * <li>-1 means there is not limit to the number of connections that can be allocated
     * </li>
     * </ul>
     * This may also return {@code null} if the data source does not provide the necessary
     * information to compute the poll usage.
     *
     * @return the usage value or {@code null}
     */
    Float getUsage();

    /**
     * Return the current number of active connections that have been allocated from the
     * data source or {@code null} if that information is not available.
     *
     * @return the number of active connections or {@code null}
     */
    Integer getActive();

    /**
     * Return the maximum number of active connections that can be allocated at the same
     * time or {@code -1} if there is no limit. Can also return {@code null} if that
     * information is not available.
     *
     * @return the maximum number of active connections or {@code null}
     */
    Integer getMax();

    /**
     * Return the minimum number of idle connections in the pool or {@code null} if that
     * information is not available.
     *
     * @return the minimum number of active connections or {@code null}
     */
    Integer getMin();

    /**
     * Return the query to use to validate that a connection is valid or {@code null} if
     * that information is not available.
     *
     * @return the validation query or {@code null}
     */
    String getValidationQuery();

    /**
     * The default auto-commit state of connections created by this pool. If not set
     * ({@code null}), default is JDBC driver default (If set to null then the
     * java.sql.Connection.setAutoCommit(boolean) method will not be called.)
     *
     * @return the default auto-commit state or {@code null}
     */
    Boolean getDefaultAutoCommit();

}
