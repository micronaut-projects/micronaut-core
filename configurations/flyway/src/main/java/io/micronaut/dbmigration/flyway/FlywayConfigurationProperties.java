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

package io.micronaut.dbmigration.flyway;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.Toggleable;

import javax.annotation.Nullable;
import javax.sql.DataSource;

/**
 * Create a Flyway Configuration for each sub-property of flyway.*.
 *
 * @author Iván López
 * @since 1.1
 */
@EachProperty("flyway")
public class FlywayConfigurationProperties implements Toggleable {

    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ASYNC = false;

    private final DataSource dataSource;

    private final String nameQualifier;

    private boolean enabled = DEFAULT_ENABLED;

    private boolean async = DEFAULT_ASYNC;

    /**
     * @param dataSource DataSource with the same name qualifier.
     * @param name       The name qualifier.
     */
    public FlywayConfigurationProperties(@Nullable @Parameter DataSource dataSource,
                                         @Parameter String name) {
        this.dataSource = dataSource;
        this.nameQualifier = name;
    }

    /**
     * @return The {@link DataSource}
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * @return The qualifier associated with this flyway configuration
     */
    public String getNameQualifier() {
        return nameQualifier;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set whether this flyway configuration is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled true if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return Whether the flyway migrations should run asynchronously
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * Whether flyway migrations should run asynchronously.
     *
     * @param async true to run flyway migrations asynchronously
     */
    public void setAsync(boolean async) {
        this.async = async;
    }
}
