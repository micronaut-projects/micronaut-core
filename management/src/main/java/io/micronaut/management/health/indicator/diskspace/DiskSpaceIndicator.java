/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.health.indicator.diskspace;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.endpoint.health.HealthEndpoint;
import io.micronaut.management.health.indicator.AbstractHealthIndicator;

import javax.inject.Singleton;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>A {@link io.micronaut.management.health.indicator.HealthIndicator} used to display information about the disk
 * space of the server. Returns {@link HealthStatus#DOWN} if the free space is less than the configured threshold.</p>
 *
 * @author James Kleeh
 * @see DiskSpaceIndicatorConfiguration#threshold
 * @since 1.0
 */
@Singleton
@Requires(property = HealthEndpoint.PREFIX + ".disk-space.enabled", notEquals = StringUtils.FALSE)
@Requires(beans = HealthEndpoint.class)
public class DiskSpaceIndicator extends AbstractHealthIndicator<Map<String, Object>> {

    protected static final String NAME = "diskSpace";

    private final DiskSpaceIndicatorConfiguration configuration;

    /**
     * @param configuration The disk space indicator configuration
     */
    DiskSpaceIndicator(DiskSpaceIndicatorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @SuppressWarnings("MagicNumber")
    @Override
    protected Map<String, Object> getHealthInformation() {
        File path = configuration.getPath();
        long threshold = configuration.getThreshold();
        long freeSpace = path.getUsableSpace();
        Map<String, Object> detail = new LinkedHashMap<>(3);

        if (freeSpace >= threshold) {
            healthStatus = HealthStatus.UP;
            detail.put("total", path.getTotalSpace());
            detail.put("free", freeSpace);
            detail.put("threshold", threshold);
        } else {
            healthStatus = HealthStatus.DOWN;
            detail.put("error", String.format(
                "Free disk space below threshold. "
                    + "Available: %d bytes (threshold: %d bytes)",
                freeSpace, threshold));
        }

        return detail;
    }
}
