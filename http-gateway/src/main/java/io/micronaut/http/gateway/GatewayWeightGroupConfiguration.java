/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.gateway;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

/**
 * The settings of a group of weighted routes, see {@link GatewayWeight}:
 * {@code micronaut.gateway.weight-groups.<group>.*}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@EachProperty("micronaut.gateway.weight-groups")
public class GatewayWeightGroupConfiguration {
    private final String group;
    @Nullable
    private String overrideHeader;
    @Nullable
    private String stickyHeader;

    /**
     * @param group The name of the group
     */
    public GatewayWeightGroupConfiguration(@Parameter String group) {
        this.group = group;
    }

    /**
     * @return The name of the group
     */
    public String getGroup() {
        return group;
    }

    /**
     * A header whose value is the id of a route of the group forces that route, e.g. to try a
     * canary, also one of weight {@code 0}. A value that is not a route of the group is ignored.
     *
     * @return The name of the header, or {@code null} for none
     */
    public @Nullable String getOverrideHeader() {
        return overrideHeader;
    }

    /**
     * @param overrideHeader See {@link #getOverrideHeader()}
     */
    public void setOverrideHeader(@Nullable String overrideHeader) {
        this.overrideHeader = overrideHeader;
    }

    /**
     * A header whose value keeps a client on the same route of the group, e.g. a user id: the
     * route is chosen by a hash of the value instead of at random. A request without it is
     * chosen at random.
     *
     * @return The name of the header, or {@code null} for none
     */
    public @Nullable String getStickyHeader() {
        return stickyHeader;
    }

    /**
     * @param stickyHeader See {@link #getStickyHeader()}
     */
    public void setStickyHeader(@Nullable String stickyHeader) {
        this.stickyHeader = stickyHeader;
    }
}
