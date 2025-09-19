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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;

/**
 * Base configuration for certificate material loaded from files.
 * Holds common options shared by concrete file-backed providers.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
@Internal
abstract sealed class AbstractCertificateFileConfig implements Named permits FileCertificateProvider.Config, ResourceCertificateProvider.Config {
    final String name;

    @Nullable
    FileCertificateProvider.Format format;
    @Nullable
    String password;

    /**
     * @param name the configuration name that is used to look up the provider
     */
    AbstractCertificateFileConfig(@NonNull String name) {
        this.name = name;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    /**
     * Explicit format of the certificate material. If not set, Micronaut will first try to load
     * the file as a key store (JKS/PKCS12) and, if that fails, as PEM. A separate
     * {@code certificatePath} can only be used with PEM.
     *
     * @return the certificate file format or {@code null} to auto-detect
     */
    public @Nullable FileCertificateProvider.Format getFormat() {
        return format;
    }

    /**
     * Explicit format of the certificate material. If not set, Micronaut will first try to load
     * the file as a key store (JKS/PKCS12) and, if that fails, as PEM. A separate
     * {@code certificatePath} can only be used with PEM.
     *
     * @param format the certificate file format or {@code null} to auto-detect
     */
    public void setFormat(@Nullable FileCertificateProvider.Format format) {
        this.format = format;
    }

    /**
     * Password used to open the key store (JKS/PKCS12) or decrypt PEM private keys, if required.
     * @return the password or {@code null} if not required
     */
    public @Nullable String getPassword() {
        return password;
    }

    /**
     * Password used to open the key store (JKS/PKCS12) or decrypt PEM private keys, if required.
     * @param password the password or {@code null} if not required
     */
    public void setPassword(@Nullable String password) {
        this.password = password;
    }
}
