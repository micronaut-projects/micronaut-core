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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.client.DefaultHttpClientConfiguration;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.ssl.ClientSslConfiguration;
import io.micronaut.runtime.ApplicationConfiguration;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

/**
 * The configuration of the default JDK client: bound to {@code micronaut.http.client} as
 * {@link DefaultHttpClientConfiguration} is, sharing its connection pool, WebSocket compression,
 * HTTP/2 and SSL settings, with the JDK settings under {@code micronaut.http.client.jdk}.
 *
 * <p>It extends {@link HttpClientConfiguration} rather than {@link DefaultHttpClientConfiguration},
 * and is not primary: the configuration of the Netty client replaces
 * {@link DefaultHttpClientConfiguration} and its subclasses when both clients are present, and
 * this one stays for the JDK client registry, which injects it by type.</p>
 *
 * @since 5.3.0
 */
@ConfigurationProperties(DefaultHttpClientConfiguration.PREFIX)
@BootstrapContextCompatible
@Experimental
public class DefaultJdkHttpClientConfiguration extends HttpClientConfiguration implements JdkHttpClientConfiguration {

    private final DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration connectionPoolConfiguration;
    private final DefaultHttpClientConfiguration.DefaultWebSocketCompressionConfiguration webSocketCompressionConfiguration;
    private final DefaultHttpClientConfiguration.DefaultHttp2ClientConfiguration http2Configuration;
    private final DefaultJdkConfiguration jdk;

    /**
     * Default configuration.
     */
    public DefaultJdkHttpClientConfiguration() {
        this.connectionPoolConfiguration = new DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration();
        this.webSocketCompressionConfiguration = new DefaultHttpClientConfiguration.DefaultWebSocketCompressionConfiguration();
        this.http2Configuration = new DefaultHttpClientConfiguration.DefaultHttp2ClientConfiguration();
        this.jdk = new DefaultJdkConfiguration();
    }

    /**
     * @param connectionPoolConfiguration       The connection pool configuration
     * @param webSocketCompressionConfiguration The WebSocket compression configuration
     * @param http2Configuration                The HTTP/2 configuration
     * @param sslConfiguration                  The SSL configuration
     * @param applicationConfiguration          The application configuration
     * @param jdk                               The JDK settings
     */
    @Inject
    public DefaultJdkHttpClientConfiguration(DefaultHttpClientConfiguration.DefaultConnectionPoolConfiguration connectionPoolConfiguration,
                                             DefaultHttpClientConfiguration.DefaultWebSocketCompressionConfiguration webSocketCompressionConfiguration,
                                             DefaultHttpClientConfiguration.DefaultHttp2ClientConfiguration http2Configuration,
                                             @Nullable ClientSslConfiguration sslConfiguration,
                                             ApplicationConfiguration applicationConfiguration,
                                             DefaultJdkConfiguration jdk) {
        super(applicationConfiguration);
        this.connectionPoolConfiguration = connectionPoolConfiguration;
        this.webSocketCompressionConfiguration = webSocketCompressionConfiguration;
        this.http2Configuration = http2Configuration;
        if (sslConfiguration != null) {
            setSslConfiguration(sslConfiguration);
        }
        this.jdk = jdk;
    }

    @Override
    public ConnectionPoolConfiguration getConnectionPoolConfiguration() {
        return connectionPoolConfiguration;
    }

    @Override
    public WebSocketCompressionConfiguration getWebSocketCompressionConfiguration() {
        return webSocketCompressionConfiguration;
    }

    @Override
    public Http2ClientConfiguration getHttp2Configuration() {
        return http2Configuration;
    }

    @Override
    public DefaultJdkConfiguration getJdk() {
        return jdk;
    }

    /**
     * The JDK settings of the default client, under {@code micronaut.http.client.jdk}.
     */
    @ConfigurationProperties("jdk")
    @BootstrapContextCompatible
    public static class DefaultJdkConfiguration extends JdkConfiguration {
    }
}
