/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.ResourceResolver;
import org.reactivestreams.Publisher;

import java.io.InputStream;
import java.security.KeyStore;

/**
 * Certificate provider that loads certificate material from a resource loader.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@EachBean(ResourceCertificateProvider.Config.class)
@BootstrapContextCompatible
public final class ResourceCertificateProvider implements CertificateProvider {
    private final String name;
    private final @NonNull KeyStore ks;

    ResourceCertificateProvider(
        @NonNull Config config,
        @NonNull ResourceResolver resourceLoader
    ) throws Exception {
        name = config.name;
        byte[] bytes;
        try (InputStream stream = resourceLoader.getResourceAsStream(config.getResource()).orElseThrow(() -> new ConfigurationException("Resource unavailable: " + config.getResource()))) {
            bytes = stream.readAllBytes();
        }
        ks = FileCertificateProvider.load(config, bytes, null);
    }

    @Override
    public @NonNull Publisher<@NonNull KeyStore> getKeyStore() {
        return Publishers.just(ks);
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    /**
     * Configuration for resource-based certificate material. Supports JKS/PKCS12 and PEM.
     */
    @EachProperty(CONFIG_PREFIX + ".resource")
    @BootstrapContextCompatible
    public static final class Config extends AbstractCertificateFileConfig {
        private String resource;

        public Config(@Parameter @NonNull String name) {
            super(name);
        }

        /**
         * Micronaut resource location of the certificate material to load, for example {@code classpath:certs/server.p12}
         * or {@code file:/etc/ssl/server.pem}. The actual format and password handling are controlled by the common
         * properties in {@link AbstractCertificateFileConfig} (e.g. {@code format}, {@code password}).
         * @return the resource location of the certificate material
         */
        public @NonNull String getResource() {
            return resource;
        }

        /**
         * Micronaut resource location of the certificate material to load, for example {@code classpath:certs/server.p12}
         * or {@code file:/etc/ssl/server.pem}. The actual format and password handling are controlled by the common
         * properties in {@link AbstractCertificateFileConfig} (e.g. {@code format}, {@code password}).
         * @param resource the resource location of the certificate material
         */
        public void setResource(@NonNull String resource) {
            this.resource = resource;
        }
    }
}
