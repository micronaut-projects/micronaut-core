/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.http.ssl;

import org.jspecify.annotations.Nullable;

import javax.net.ssl.HostnameVerifier;

/**
 * Base class for {@link SslConfiguration} extensions for SSL clients.
 *
 * @since 3.2.0
 * @author Jonas Konrad
 */
public abstract class AbstractClientSslConfiguration extends SslConfiguration {

    /**
     * The default endpoint identification value.
     *
     * @since 5.3.0
     */
    public static final boolean DEFAULT_ENDPOINT_IDENTIFICATION = true;

    private boolean insecureTrustAllCertificates;
    private boolean endpointIdentification = DEFAULT_ENDPOINT_IDENTIFICATION;
    private @Nullable HostnameVerifier hostnameVerifier;

    /**
     * @return Whether the client should disable checking of the remote SSL certificate. Only applies if no trust store
     * is configured. <b>Note: This makes the SSL connection insecure, and should only be used for testing. If you are
     * using a self-signed certificate, set up a trust store instead.</b>
     */
    public boolean isInsecureTrustAllCertificates() {
        return insecureTrustAllCertificates;
    }

    /**
     * @param insecureTrustAllCertificates Whether the client should disable checking of the remote SSL certificate.
     *                                     Only applies if no trust store is configured.
     *                                     <b>Note: This makes the SSL connection insecure, and should only be used for
     *                                     testing. If you are using a self-signed certificate, set up a trust store
     *                                     instead.</b>
     */
    public void setInsecureTrustAllCertificates(boolean insecureTrustAllCertificates) {
        this.insecureTrustAllCertificates = insecureTrustAllCertificates;
    }

    /**
     * @return Whether the client checks that the server certificate matches the host name (HTTPS endpoint
     * identification). Default value ({@value #DEFAULT_ENDPOINT_IDENTIFICATION}).
     * @since 5.3.0
     */
    public boolean isEndpointIdentification() {
        return endpointIdentification;
    }

    /**
     * Whether the client checks that the server certificate matches the host name (HTTPS endpoint identification).
     * Disable it to verify the host name some other way, for example with a {@link javax.net.ssl.HostnameVerifier}
     * after the handshake. <b>Note: Without any host name check the connection is open to man-in-the-middle
     * attacks.</b> Default value ({@value #DEFAULT_ENDPOINT_IDENTIFICATION}).
     *
     * @param endpointIdentification Whether to check the host name of the server certificate
     * @since 5.3.0
     */
    public void setEndpointIdentification(boolean endpointIdentification) {
        this.endpointIdentification = endpointIdentification;
    }

    /**
     * A {@link HostnameVerifier} the client calls with the host name and the {@link javax.net.ssl.SSLSession} once
     * the TLS handshake completes. The connection fails with a {@link javax.net.ssl.SSLPeerUnverifiedException} when
     * it rejects the host. It runs in addition to endpoint identification, so to accept a host the default check
     * rejects, also disable {@link #setEndpointIdentification(boolean) endpoint identification}. Only set
     * programmatically. Not supported for HTTP/3.
     *
     * @return the verifier, or {@code null} for none
     * @since 5.3.0
     */
    public @Nullable HostnameVerifier getHostnameVerifier() {
        return hostnameVerifier;
    }

    /**
     * A {@link HostnameVerifier} the client calls with the host name and the {@link javax.net.ssl.SSLSession} once
     * the TLS handshake completes. The connection fails with a {@link javax.net.ssl.SSLPeerUnverifiedException} when
     * it rejects the host. Not supported for HTTP/3.
     *
     * @param hostnameVerifier the verifier, or {@code null} for none
     * @since 5.3.0
     */
    public void setHostnameVerifier(@Nullable HostnameVerifier hostnameVerifier) {
        this.hostnameVerifier = hostnameVerifier;
    }
}
