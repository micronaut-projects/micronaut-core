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
package io.micronaut.jdbc;

import javax.sql.DataSource;

/**
 * Resolves the underlying target data source.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface DataSourceResolver {
    /**
     * The default implementation.
     */
    DataSourceResolver DEFAULT = new DataSourceResolver() {
    };

    /**
     * Resolves the underlying target data source in the case it has been wrapped by proxying / instrumentation logic.
     *
     * @param dataSource The data source
     * @return The unwrapped datasource
     */
    default DataSource resolve(DataSource dataSource) {
        return dataSource;
    }
}
