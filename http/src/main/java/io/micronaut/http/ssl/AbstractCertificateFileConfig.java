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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.Named;

abstract class AbstractCertificateFileConfig implements Named {
    final String name;

    @Nullable
    FileCertificateProvider.Format format;
    @Nullable
    String password;

    AbstractCertificateFileConfig(String name) {
        this.name = name;
    }

    @Override
    public @NonNull String getName() {
        return name;
    }

    public FileCertificateProvider.Format getFormat() {
        return format;
    }

    public void setFormat(FileCertificateProvider.Format format) {
        this.format = format;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
