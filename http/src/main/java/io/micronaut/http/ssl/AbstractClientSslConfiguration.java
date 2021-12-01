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

/**
 * Base class for {@link SslConfiguration} extensions for SSL clients.
 *
 * @since 3.2.0
 * @author Jonas Konrad
 */
public abstract class AbstractClientSslConfiguration extends SslConfiguration {

    private boolean insecureTrustAllCertificates;

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
}
