/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.http.HttpVersion;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * A class to build a key store and a trust store for use in adding SSL support to a server.
 *
 * @param <T> The server specific type to be built
 * @author James Kleeh
 * @since 1.0
 */
public abstract class SslBuilder<T> {

    private final ResourceResolver resourceResolver;

    /**
     * @param resourceResolver The resource resolver
     */
    public SslBuilder(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return Builds the SSL configuration wrapped inside an optional
     */
    public abstract Optional<T> build(SslConfiguration ssl);

    /**
     * @param ssl The ssl configuration
     * @param httpVersion  The http version
     * @return Builds the SSL configuration wrapped inside an optional
     */
    public abstract Optional<T> build(SslConfiguration ssl, HttpVersion httpVersion);

    /**
     * @param ssl The ssl configuration
     *
     * @return The {@link TrustManagerFactory}
     */
    protected TrustManagerFactory getTrustManagerFactory(SslConfiguration ssl) {
        Optional<KeyStore> store;
        try {
            store = getTrustStore(ssl);
        } catch (SslConfigurationException e) {
            throw e;
        } catch (Exception e) {
            throw new SslConfigurationException(e);
        }
        return getTrustManagerFactory(store.orElse(null));
    }

    /**
     * @param store The key store
     *
     * @return The {@link TrustManagerFactory}
     */
    protected TrustManagerFactory getTrustManagerFactory(KeyStore store) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory
                .getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(store);
            return trustManagerFactory;
        } catch (SslConfigurationException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return An optional {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected Optional<KeyStore> getTrustStore(SslConfiguration ssl) throws Exception {
        SslConfiguration.TrustStoreConfiguration trustStore = ssl.getTrustStore();
        Optional<String> path = trustStore.getPath();
        if (path.isPresent()) {
            return Optional.of(loadCompat(new KeyStoreBasedCertificateSpec(
                trustStore.getType().orElse(null),
                trustStore.getPassword().orElse(null),
                trustStore.getProvider().orElse(null),
                path.get()
            )));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return The {@link KeyManagerFactory}
     */
    protected KeyManagerFactory getKeyManagerFactory(SslConfiguration ssl) {
        try {
            Optional<KeyStore> keyStore = getKeyStore(ssl);
            KeyManagerFactory keyManagerFactory = KeyManagerFactory
                .getInstance(KeyManagerFactory.getDefaultAlgorithm());
            Optional<String> password = ssl.getKey().getPassword();
            char[] keyPassword = password.map(String::toCharArray).orElse(null);
            Optional<String> pwd = ssl.getKeyStore().getPassword();
            if (keyPassword == null && pwd.isPresent()) {
                keyPassword = pwd.get().toCharArray();
            }
            keyManagerFactory.init(keyStore.orElse(null), keyPassword);
            return keyManagerFactory;
        } catch (Exception ex) {
            throw new SslConfigurationException(ex);
        }
    }

    /**
     * @param ssl The ssl configuration
     *
     * @return An optional {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected Optional<KeyStore> getKeyStore(SslConfiguration ssl) throws Exception {
        SslConfiguration.KeyStoreConfiguration keyStore = ssl.getKeyStore();
        Optional<String> path = keyStore.getPath();
        if (path.isPresent()) {
            if (keyStore.getKeyPath() != null || keyStore.getCertificatePath() != null) {
                throw new SslConfigurationException("Cannot specify key store path and key-path or certificate-path at the same time");
            }
            return Optional.of(loadCompat(new KeyStoreBasedCertificateSpec(
                keyStore.getType().orElse(null),
                keyStore.getPassword().orElse(null),
                keyStore.getProvider().orElse(null),
                path.get()
            )));
        } else if (keyStore.getKeyPath() != null) {
            if (keyStore.getCertificatePath() == null) {
                throw new SslConfigurationException("Must also specify certificate-path");
            }
            return Optional.of(loadCompat(new PemBasedCertificateSpec(
                keyStore.getType().orElse(null),
                keyStore.getPassword().orElse(null),
                keyStore.getProvider().orElse(null),
                keyStore.getKeyPath(),
                keyStore.getCertificatePath()
            )));
        } else if (keyStore.getCertificatePath() != null) {
            throw new SslConfigurationException("Must also specify key-path");
        } else {
            return Optional.empty();
        }
    }

    private KeyStore loadCompat(CertificateSpec spec) throws Exception {
        if (spec instanceof KeyStoreBasedCertificateSpec ks && ks.getProvider() == null) {
            // we need to call the old method to make sure we hit any overrides
            return load(Optional.ofNullable(ks.getType()), ks.getPath(), Optional.ofNullable(ks.getPassword()));
        } else {
            return load(spec);
        }
    }

    /**
     * @param optionalType     The optional type
     * @param resource         The resource
     * @param optionalPassword The optional password
     * @return A {@link KeyStore}
     * @throws Exception if there is an error
     * @deprecated Please override {@link #load(CertificateSpec)} instead
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @Deprecated(forRemoval = true)
    protected KeyStore load(Optional<String> optionalType,
                            String resource,
                            Optional<String> optionalPassword) throws Exception {
        return load(new KeyStoreBasedCertificateSpec(optionalType.orElse(null), optionalPassword.orElse(null), null, resource));
    }

    private static Supplier<SslConfigurationException> resourceNotFound(String resource) {
        return () -> new SslConfigurationException("The resource " + resource + " could not be found");
    }

    /**
     * @param spec The configured certificate spec
     * @return A {@link KeyStore}
     * @throws Exception if there is an error
     */
    protected KeyStore load(CertificateSpec spec) throws Exception {
        if (spec instanceof KeyStoreBasedCertificateSpec ks) {
            KeyStore store = createEmptyKeyStore(spec.provider, spec.type == null ? "JKS" : spec.type);

            InputStream stream = resourceResolver.getResourceAsStream(ks.path)
                .orElseThrow(resourceNotFound(ks.path));
            try {
                store.load(stream, spec.password == null ? null : spec.password.toCharArray());
            } catch (IOException e) {
                if (!(e.getCause() instanceof UnrecoverableKeyException)) {
                    try {
                        if (spec.type == null) {
                            // we can't add passwordless keys to a JKS key store
                            store = createEmptyKeyStore(spec.provider, "PKCS12");
                        }

                        loadPem(ks.path, spec.password, spec.provider, store);
                    } catch (PemParser.NotPemException f) {
                        // probably should have been loaded as KS
                        e.addSuppressed(new Exception("Also tried and failed to load the input as PEM", f));
                        throw e;
                    } catch (Exception f) {
                        // probably should have been loaded as PEM
                        f.addSuppressed(new Exception("Also tried and failed to load the input as a key store", e));
                        throw f;
                    }
                } else {
                    throw e;
                }
            }
            return store;
        } else if (spec instanceof PemBasedCertificateSpec pem) {
            List<Object> keyItems;
            try (InputStream s = resourceResolver.getResourceAsStream(pem.keyPath).orElseThrow(resourceNotFound(pem.keyPath))) {
                keyItems = new PemParser(pem.provider, pem.password)
                    .loadPem(new String(s.readAllBytes(), StandardCharsets.UTF_8));
            }
            List<Object> certItems;
            try (InputStream s = resourceResolver.getResourceAsStream(pem.certificatePath).orElseThrow(resourceNotFound(pem.certificatePath))) {
                certItems = new PemParser(pem.provider, pem.password)
                    .loadPem(new String(s.readAllBytes(), StandardCharsets.UTF_8));
            }

            if (keyItems.size() != 1) {
                throw new SslConfigurationException("key-path contained more than one PEM object. It should only contain the private key.");
            }
            if (!(keyItems.get(0) instanceof PrivateKey pk)) {
                throw new SslConfigurationException("key-path contained a certificate instead of a private key.");
            }
            KeyStore store = createEmptyKeyStore(spec.provider, spec.type == null ? "PKCS12" : spec.type);
            store.load(null, null);
            store.setKeyEntry(
                "key",
                pk,
                null,
                certificates(certItems).toArray(new X509Certificate[0])
            );
            return store;
        } else {
            throw new AssertionError("Weird CertificateSpec");
        }
    }

    private static @NonNull KeyStore createEmptyKeyStore(@Nullable String provider, String type) throws KeyStoreException, NoSuchProviderException {
        return provider == null ? KeyStore.getInstance(type) : KeyStore.getInstance(type, provider);
    }

    private void loadPem(@NonNull String resource, @Nullable String password, @Nullable String provider, KeyStore store) throws IOException, GeneralSecurityException, PemParser.NotPemException {
        List<Object> items;
        try (InputStream s = resourceResolver.getResourceAsStream(resource).orElseThrow(resourceNotFound(resource))) {
            items = new PemParser(provider, password)
                .loadPem(new String(s.readAllBytes(), StandardCharsets.UTF_8));
        }
        if (items.get(0) instanceof PrivateKey pk) {
            X509Certificate[] certs = certificates(items.subList(1, items.size())).toArray(new X509Certificate[0]);
            store.load(null, null);
            store.setKeyEntry("key", pk, null, certs);
        } else if (items.get(0) instanceof X509Certificate) {
            store.load(null, null);
            List<X509Certificate> certificates = certificates(items);
            for (int i = 0; i < certificates.size(); i++) {
                store.setCertificateEntry("cert" + i, certificates.get(i));
            }
        } else {
            throw new SslConfigurationException("Unrecognized PEM entries");
        }
    }

    static List<X509Certificate> certificates(List<Object> pemObjects) {
        for (Object pemObject : pemObjects) {
            if (!(pemObject instanceof X509Certificate)) {
                throw new SslConfigurationException("PEM must only contain the private key and a certificate chain");
            }
        }
        //noinspection unchecked,rawtypes
        return (List) pemObjects;
    }

    /**
     * Specification for building a {@link KeyStore}, either as a key store or a trust store.
     *
     * @since 4.10.0
     */
    protected abstract static sealed class CertificateSpec {
        final String type;
        final String password;
        final String provider;

        private CertificateSpec(String type, String password, String provider) {
            this.type = type;
            this.password = password;
            this.provider = provider;
        }

        /**
         * {@link KeyStore} type, e.g. JKS or PKCS12.
         *
         * @return Key store type
         */
        @Nullable
        public String getType() {
            return type;
        }

        /**
         * Key store password.
         *
         * @return The password
         */
        @Nullable
        public String getPassword() {
            return password;
        }

        /**
         * JCA provider for creating the key store and other objects.
         *
         * @return The JCA provider
         */
        @Nullable
        public String getProvider() {
            return provider;
        }
    }

    /**
     * Certificate spec based on {@link SslConfiguration.KeyStoreConfiguration#getPath()} or
     * {@link SslConfiguration.TrustStoreConfiguration#getPath()}. Note that the path can still
     * point to a PEM.
     *
     * @since 4.10.0
     */
    protected static final class KeyStoreBasedCertificateSpec extends CertificateSpec {
        final String path;

        private KeyStoreBasedCertificateSpec(String type, String password, String provider, String path) {
            super(type, password, provider);
            this.path = path;
        }

        /**
         * The path to the JKS, PKCS12 or PEM file.
         *
         * @return The path
         */
        @NonNull
        public String getPath() {
            return path;
        }
    }

    /**
     * Certificate spec based on {@link SslConfiguration.KeyStoreConfiguration#getKeyPath()} and
     * {@link SslConfiguration.KeyStoreConfiguration#getCertificatePath()}, both of which must
     * contain a PEM.
     *
     * @since 4.10.0
     */
    protected static final class PemBasedCertificateSpec extends CertificateSpec {
        final String keyPath;
        final String certificatePath;

        private PemBasedCertificateSpec(String type, String password, String provider, String keyPath, String certificatePath) {
            super(type, password, provider);
            this.keyPath = keyPath;
            this.certificatePath = certificatePath;
        }

        /**
         * The path to the PEM file containing the private key.
         *
         * @return The path
         */
        @NonNull
        public String getKeyPath() {
            return keyPath;
        }

        /**
         * The path to the PEM file containing the certificate chain.
         *
         * @return The path
         */
        @NonNull
        public String getCertificatePath() {
            return certificatePath;
        }
    }
}
