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
package io.micronaut.management.health.indicator.diskspace;

import io.micronaut.context.annotation.ConfigurationProperties;
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

    private boolean enabled = true;
    private File path = new File(".");
    private long threshold = 1024 * 1024 * 10; // 10MB

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public File getPath() {
        return path;
    }

    public long getThreshold() {
        return threshold;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void setPath(File path) {
        this.path = path;
    }

    void setThreshold(@ReadableBytes long threshold) {
        this.threshold = threshold;
    }
}
