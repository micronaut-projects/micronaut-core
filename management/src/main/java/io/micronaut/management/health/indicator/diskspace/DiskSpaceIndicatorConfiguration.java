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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.convert.format.ReadableBytes;
import io.micronaut.core.util.Toggleable;

import java.io.File;

/**
 * <p>Specific configuration properties for the disk space health indicator.</p>
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties("endpoints.health.disk-space")
public class DiskSpaceIndicatorConfiguration implements Toggleable {

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default path value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final String DEFAULT_PATH = ".";

    /**
     * The default threshold value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final long DEFAULT_THRESHOLD = 1024L * 1024 * 10; // 10MB

    private boolean enabled = DEFAULT_ENABLED;
    private File path = new File(DEFAULT_PATH);
    private long threshold = DEFAULT_THRESHOLD;

    /**
     * @return Whether the health indicator is enabled
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Default value ({@value #DEFAULT_ENABLED}).
     * @param enabled Enable the health indication endpoint
     */
    protected void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return The file path to monitor for disk space
     */
    public File getPath() {
        return path;
    }

    /**
     * Default value ({@value #DEFAULT_PATH}).
     * @param path The file path
     */
    protected void setPath(File path) {
        this.path = path;
    }

    /**
     * @return The threshold
     */
    public long getThreshold() {
        return threshold;
    }

    /**
     * Default value ({@value #DEFAULT_THRESHOLD} => 10MB).
     * @param threshold Set the threshold
     */
    protected void setThreshold(@ReadableBytes long threshold) {
        this.threshold = threshold;
    }
}
