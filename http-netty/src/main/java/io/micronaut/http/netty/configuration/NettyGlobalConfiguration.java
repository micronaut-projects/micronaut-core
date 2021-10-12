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
package io.micronaut.http.netty.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Nullable;
import io.netty.util.ResourceLeakDetector;

/**
 * Allows configuring Netty global properties.
 *
 * @author Denis Stepannov
 * @since 2.5.0
 */
@ConfigurationProperties("netty")
public class NettyGlobalConfiguration {

    private ResourceLeakDetector.Level resourceLeakDetectorLevel;

    /**
     * Sets the resource leak detection level.
     *
     * @param resourceLeakDetectorLevel the resource leak detection level
     */
    public void setResourceLeakDetectorLevel(ResourceLeakDetector.Level resourceLeakDetectorLevel) {
        this.resourceLeakDetectorLevel = resourceLeakDetectorLevel;
    }

    /**
     * Provides the value set for the resource leak detection.
     *
     * @return the resource leak detection level
     */
    @Nullable
    public ResourceLeakDetector.Level getResourceLeakDetectorLevel() {
        return resourceLeakDetectorLevel;
    }

}
