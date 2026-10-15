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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.ServiceHttpClientConfiguration;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

/**
 * The configuration of a service client, {@code micronaut.http.services.<id>}, that also carries the
 * JDK settings under {@code micronaut.http.services.<id>.jdk}. The JDK settings a service does not
 * set are those of the {@link DefaultJdkHttpClientConfiguration default client}.
 *
 * @since 5.3.0
 */
@EachProperty(ServiceHttpClientConfiguration.PREFIX)
@Replaces(ServiceHttpClientConfiguration.class)
@Experimental
public class ServiceJdkHttpClientConfiguration extends ServiceHttpClientConfiguration implements JdkHttpClientConfiguration {

    private final JdkConfiguration jdk;

    /**
     * @param serviceId                         The service id
     * @param connectionPoolConfiguration       The connection pool configuration
     * @param webSocketCompressionConfiguration The WebSocket compression configuration
     * @param http2Configuration                The HTTP/2 configuration
     * @param sslConfiguration                  The SSL configuration
     * @param outlierDetection                  The outlier detection configuration
     * @param defaultHttpClientConfiguration    The default HTTP client configuration
     * @param jdk                               The JDK settings of the service, if any
     * @param defaultJdk                        The JDK settings of the default client
     */
    @Inject
    public ServiceJdkHttpClientConfiguration(
        @Parameter String serviceId,
        @Nullable ServiceConnectionPoolConfiguration connectionPoolConfiguration,
        @Nullable ServiceWebSocketCompressionConfiguration webSocketCompressionConfiguration,
        ServiceHttpClientConfiguration.@Nullable ServiceHttp2ClientConfiguration http2Configuration,
        @Nullable ServiceSslClientConfiguration sslConfiguration,
        @Nullable ServiceOutlierDetectionConfiguration outlierDetection,
        HttpClientConfiguration defaultHttpClientConfiguration,
        @Nullable ServiceJdkConfiguration jdk,
        DefaultJdkHttpClientConfiguration.DefaultJdkConfiguration defaultJdk) {
        super(serviceId, connectionPoolConfiguration, webSocketCompressionConfiguration, http2Configuration,
            sslConfiguration, outlierDetection, defaultHttpClientConfiguration);
        this.jdk = jdk != null ? jdk : new JdkConfiguration(defaultJdk);
    }

    @Override
    public JdkConfiguration getJdk() {
        return jdk;
    }

    /**
     * The JDK settings of a service, under {@code micronaut.http.services.<id>.jdk}, initialized
     * from those of the default client.
     */
    @ConfigurationProperties("jdk")
    public static class ServiceJdkConfiguration extends JdkConfiguration {

        /**
         * Default settings.
         */
        public ServiceJdkConfiguration() {
        }

        /**
         * @param defaults The JDK settings of the default client
         */
        @Inject
        public ServiceJdkConfiguration(DefaultJdkHttpClientConfiguration.DefaultJdkConfiguration defaults) {
            super(defaults);
        }
    }
}
