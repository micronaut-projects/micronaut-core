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
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.client.HttpClientConfiguration;

/**
 * Implemented by the {@link HttpClientConfiguration} subclasses that carry the settings only the
 * JDK client uses, under the {@code jdk} namespace of the client configuration:
 * {@code micronaut.http.client.jdk.*} for the {@link DefaultJdkHttpClientConfiguration default
 * client} and {@code micronaut.http.services.<id>.jdk.*} for a
 * {@link ServiceJdkHttpClientConfiguration service client}. A JDK client given any other
 * {@link HttpClientConfiguration} uses the default {@link JdkConfiguration settings}.
 *
 * @since 5.3.0
 */
@Experimental
public interface JdkHttpClientConfiguration {

    /**
     * @return The settings only the JDK client uses
     */
    JdkConfiguration getJdk();

    /**
     * The settings under the {@code jdk} namespace of a client configuration, only used by the JDK
     * client. All default to {@code false}, which keeps the legacy behavior of the JDK client.
     *
     * @since 5.3.0
     */
    @Experimental
    class JdkConfiguration {
        private boolean applyRequestTimeout;
        private boolean useMicronautRedirects;
        private boolean decodeErrorType;

        /**
         * Default settings, preserving the legacy behavior of the JDK client.
         */
        public JdkConfiguration() {
        }

        /**
         * @param copy The settings to copy
         */
        public JdkConfiguration(JdkConfiguration copy) {
            applyRequestTimeout = copy.applyRequestTimeout;
            useMicronautRedirects = copy.useMicronautRedirects;
            decodeErrorType = copy.decodeErrorType;
        }

        /**
         * Whether the JDK client applies the {@link HttpClientConfiguration#getRequestTimeout() request
         * timeout} to an exchange, as the Netty client does. Configured by
         * {@code micronaut.http.client.jdk.apply-request-timeout}. Default {@code false}: the JDK
         * client only applies the {@link HttpClientConfiguration#getReadTimeout() read timeout}, until
         * the response headers arrive.
         *
         * @return Whether the JDK client applies the request timeout
         */
        public boolean isApplyRequestTimeout() {
            return applyRequestTimeout;
        }

        /**
         * @param applyRequestTimeout Whether the JDK client applies the request timeout, see {@link #isApplyRequestTimeout()}
         */
        public void setApplyRequestTimeout(boolean applyRequestTimeout) {
            this.applyRequestTimeout = applyRequestTimeout;
        }

        /**
         * Whether the JDK client follows redirects as the Netty client does, instead of letting the
         * JDK client follow them. Configured by {@code micronaut.http.client.jdk.use-micronaut-redirects}.
         * Default {@code false}. When enabled, the client filters run for each redirect, the
         * {@link HttpClientConfiguration#getMaxRedirects() maximum number of redirects} and the redirect
         * header settings apply, a 301 or 302 redirect is followed with a GET without the body, a
         * redirect from https to http is followed, and a redirect loop fails with an exception instead
         * of returning the last redirect response.
         *
         * @return Whether the JDK client follows redirects as the Netty client does
         */
        public boolean isUseMicronautRedirects() {
            return useMicronautRedirects;
        }

        /**
         * @param useMicronautRedirects Whether the JDK client follows redirects as the Netty client does, see {@link #isUseMicronautRedirects()}
         */
        public void setUseMicronautRedirects(boolean useMicronautRedirects) {
            this.useMicronautRedirects = useMicronautRedirects;
        }

        /**
         * Whether the {@link io.micronaut.http.client.exceptions.HttpClientResponseException} of an
         * error status of an exchange of the JDK client decodes the error body into the error type of
         * the exchange, as the Netty client does. Configured by
         * {@code micronaut.http.client.jdk.decode-error-type}. Default {@code false}: the response of
         * the exception decodes its body into the body type of the exchange, and the error type is not
         * used.
         *
         * @return Whether the JDK client decodes the error body into the error type
         */
        public boolean isDecodeErrorType() {
            return decodeErrorType;
        }

        /**
         * @param decodeErrorType Whether the JDK client decodes the error body into the error type, see {@link #isDecodeErrorType()}
         */
        public void setDecodeErrorType(boolean decodeErrorType) {
            this.decodeErrorType = decodeErrorType;
        }
    }
}
