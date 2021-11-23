package io.micronaut.http.ssl;

/**
 * Base class for {@link SslConfiguration} extensions for SSL clients.
 *
 * @since 3.2
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
