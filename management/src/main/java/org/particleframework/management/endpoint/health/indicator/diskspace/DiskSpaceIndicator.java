/*
 * Copyright 2017 original authors
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
package org.particleframework.management.endpoint.health.indicator.diskspace;

import org.particleframework.context.annotation.Requires;
import org.particleframework.management.endpoint.health.HealthStatus;
import org.particleframework.management.endpoint.health.indicator.AbstractHealthIndicator;

import javax.inject.Singleton;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>A {@link org.particleframework.management.endpoint.health.indicator.HealthIndicator} used to display
 * information about the disk space of the server. Returns {@link HealthStatus#DOWN} if the free space
 * is less than the configured threshold.</p>
 *
 * @see DiskSpaceIndicatorConfiguration#threshold
 *
 * @author James Kleeh
 * @since 1.0
 */
@Singleton
@Requires(endpoint = "endpoints.health")
@Requires(property = "endpoints.health.disk-space.enabled", notEquals = "false")
public class DiskSpaceIndicator extends AbstractHealthIndicator {

    protected static final String NAME = "diskSpace";
    private DiskSpaceIndicatorConfiguration configuration;

    DiskSpaceIndicator(DiskSpaceIndicatorConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    protected Object getHealthInformation() {
        File path = configuration.path;
        long threshold = configuration.threshold;
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
