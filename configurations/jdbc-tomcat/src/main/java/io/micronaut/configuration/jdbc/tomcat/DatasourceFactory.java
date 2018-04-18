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

package io.micronaut.configuration.jdbc.tomcat;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import org.apache.tomcat.jdbc.pool.DataSource;

/**
 * Creates a tomcat data source for each configuration bean.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Factory
public class DatasourceFactory {

    /**
     * @param datasourceConfiguration A {@link DatasourceConfiguration}
     * @return An Apache Tomcat {@link DataSource}
     */
    @EachBean(DatasourceConfiguration.class)
    @Bean(preDestroy = "close")
    public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
        return new DataSource(datasourceConfiguration);
    }
}
