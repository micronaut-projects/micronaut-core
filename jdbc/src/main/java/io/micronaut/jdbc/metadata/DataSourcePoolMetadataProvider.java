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
 * Provide a {@link DataSourcePoolMetadata} based on a {@link javax.sql.DataSource}.
 *
 * @author Stephane Nicoll
 * @author Christian Oestreich
 * @since 2.0.0
 */
@FunctionalInterface
public interface DataSourcePoolMetadataProvider {

    /**
     * Return the {@link DataSourcePoolMetadata} instance able to manage the specified
     * {@link javax.sql.DataSource} or {@code null} if the given data source could not be handled.
     *
     * @return the data source pool metadata
     */
    DataSourcePoolMetadata getDataSourcePoolMetadata();
}
