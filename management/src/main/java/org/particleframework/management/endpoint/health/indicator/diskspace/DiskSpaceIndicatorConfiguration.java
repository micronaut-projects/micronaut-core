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

import org.particleframework.context.annotation.ConfigurationProperties;
import org.particleframework.core.convert.format.ReadableBytes;
import org.particleframework.core.util.Toggleable;

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
    @ReadableBytes
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

    protected void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    protected void setPath(File path) {
        this.path = path;
    }

    protected void setThreshold(long threshold) {
        this.threshold = threshold;
    }
}
