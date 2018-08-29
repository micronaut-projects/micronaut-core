package io.micronaut.security.ldap.ssl;

import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.ssl.SslBuilder;
import io.micronaut.http.ssl.SslConfiguration;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.security.GeneralSecurityException;
import java.util.Optional;

public class LdapClientSslBuilder extends SslBuilder<SSLSocketFactory> {

    public LdapClientSslBuilder(SslConfiguration sslConfiguration) {
        super(sslConfiguration, new ResourceResolver());
    }

    @Override
    public Optional<SSLSocketFactory> build() {

        Optional<String> protocol = ssl.getProtocol();
        if (protocol.isPresent()) {
            try {
                SSLContext sslContext = SSLContext.getInstance(ssl.getProtocol().get());
                sslContext.init(getKeyManagerFactory().getKeyManagers(),null,
                        null);
                return Optional.ofNullable(sslContext.getSocketFactory());
            } catch (GeneralSecurityException e) {
                //
            }
        }

        return Optional.empty();
    }
}
