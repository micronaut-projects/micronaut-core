/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.client;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;

/**
 * Configuration used by the {@link ProxyHttpClient}.
 *
 * @author Tim Yates
 * @since 3.3.0
 */
@ConfigurationProperties(ProxyHttpClientConfiguration.PREFIX)
@BootstrapContextCompatible
public class ProxyHttpClientConfiguration {

    /**
     * The prefix used to resolve this configuration.
     */
    public static final String PREFIX = "micronaut.http.client.proxy";
    public static final HostMode DEFAULT_HOST_HEADER_MODE = HostMode.REPLACE;

    private HostMode hostMode = DEFAULT_HOST_HEADER_MODE;

    /**
     * The behaviour with the Host header in proxied requests. Defaults to {@link HostMode#REPLACE}.
     * @return The current host mode.
     */
    public HostMode getHostMode() {
        return hostMode;
    }

    /**
     * Sets the behaviour for the Host header in proxied requests.  Defaults to {@link HostMode#REPLACE}.
     * @param hostMode a valid {@link HostMode}.
     */
    public void setHostMode(HostMode hostMode) {
        this.hostMode = hostMode;
    }

    /**
     * What to do with Host header when proxying a request.
     */
    public enum HostMode {
        /**
         * Simply replace the Host header with the host[:port] of the target system.
         */
        REPLACE,

        /**
         * Keep the existing header. This was the default behaviour pre 3.3.0.
         */
        KEEP,

        /**
         * As {@link #REPLACE}, but put the original Host header value (if any) into an {@code X-Forwarded-Host} header.
         */
        X_FORWARDED_HOST,

        /**
         * As {@link #REPLACE}, but append the original Host header value (if any) to the end of a {@code Forwarded} header.
         */
        FORWARDED
    }
}
