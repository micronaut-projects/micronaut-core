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

package io.micronaut.security.handlers;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.http.HttpStatus;
import io.micronaut.security.config.SecurityConfigurationProperties;

import javax.annotation.Nonnull;

/**
 * {@link ConfigurationProperties} implementation of {@link io.micronaut.security.handlers.RedirectRejectionHandlerConfiguration}.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@ConfigurationProperties(RedirectRejectionHandlerConfigurationProperties.PREFIX)
public class RedirectRejectionHandlerConfigurationProperties implements RedirectRejectionHandlerConfiguration {

    public static final String PREFIX = SecurityConfigurationProperties.PREFIX + ".redirect";

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default http status used for redirection.
     */
    @SuppressWarnings("WeakerAccess")
    public static final HttpStatus DEFAULT_REDIRECTIONHTTPSTATUS = HttpStatus.SEE_OTHER;

    private boolean enabled = DEFAULT_ENABLED;

    private HttpStatus httpStatus = DEFAULT_REDIRECTIONHTTPSTATUS;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables {@link io.micronaut.security.handlers.RedirectRejectionHandler}. Default value {@value #DEFAULT_ENABLED}.
     *
     * @param enabled True if enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nonnull
    @Override
    public HttpStatus getHttpStatus() {
        return this.httpStatus;
    }

    /**
     * The Http status used used for redirection. Defaults value (303).
     * @param httpStatus The Http status used used for redirection.
     */
    public void setHttpStatus(HttpStatus httpStatus) {
        if (httpStatus == null) {
            httpStatus = DEFAULT_REDIRECTIONHTTPSTATUS;
        } else {
            this.httpStatus = httpStatus;
        }
    }
}
