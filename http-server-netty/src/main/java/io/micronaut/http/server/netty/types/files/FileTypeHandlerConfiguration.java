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
package io.micronaut.http.server.netty.types.files;

import io.micronaut.context.annotation.ConfigurationProperties;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Allows configuration of properties for the {@link FileTypeHandler}.
 *
 * @author James Kleeh
 * @since 1.0
 */
@ConfigurationProperties("netty.responses.file")
public class FileTypeHandlerConfiguration {

    /**
     * The default cache seconds.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int DEFAULT_CACHESECONDS = 60;

    private int cacheSeconds = DEFAULT_CACHESECONDS;
    private CacheControlConfiguration cacheControl = new CacheControlConfiguration();

    /**
     * @return the cache seconds
     */
    public int getCacheSeconds() {
        return cacheSeconds;
    }

    /**
     * Cache Seconds. Default value ({@value #DEFAULT_CACHESECONDS}).
     * @param cacheSeconds cache seconds
     */
    public void setCacheSeconds(int cacheSeconds) {
        this.cacheSeconds = cacheSeconds;
    }

    /**
     * @return The cache control configuration
     */
    public CacheControlConfiguration getCacheControl() {
        return cacheControl;
    }

    /**
     * Sets the cache control configuration.
     *
     * @param cacheControl The cache control configuration
     */
    public void setCacheControl(CacheControlConfiguration cacheControl) {
        this.cacheControl = cacheControl;
    }

    /**
     * Configuration for the Cache-Control header.
     */
    @ConfigurationProperties("cache-control")
    public static class CacheControlConfiguration {

        private static final boolean DEFAULT_PUBLIC_CACHE = false;

        private boolean publicCache = DEFAULT_PUBLIC_CACHE;

        /**
         * Sets whether the cache control is public. Default value ({@value #DEFAULT_PUBLIC_CACHE})
         *
         * @param publicCache Public cache value
         */
        public void setPublic(boolean publicCache) {
            this.publicCache = publicCache;
        }

        /**
         * @return True if the cache control should be public
         */
        @NonNull
        public boolean getPublic() {
            return publicCache;
        }
    }
}
