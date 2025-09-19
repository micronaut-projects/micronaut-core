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
import io.micronaut.core.naming.Named;
import org.reactivestreams.Publisher;

import java.security.KeyStore;

/**
 * Provides access to certificate material as {@link KeyStore} instances that can be
 * consumed by SSL context builders. Implementations may actively refresh and emit
 * new keystores when underlying sources change.
 *
 * @author Jonas Konrad
 * @since 4.10.0
 */
public interface CertificateProvider extends Named {
    String CONFIG_PREFIX = "micronaut.certificate";

    /**
     * Publisher that emits the key store containing private key and/or certificates.
     * <b>To avoid weird initialization issues, it is highly recommended to return a publisher here
     * that produces a key store immediately upon subscription, on the subscribing thread. This
     * avoids race conditions where e.g. a server starts up before a key store is available, and
     * there is a short interval where SSL connections will fail.</b>
     *
     * @return a publisher of {@link KeyStore} updates
     */
    @NonNull
    Publisher<@NonNull KeyStore> getKeyStore();

    /**
     * Publisher that emits the trust store with trusted certificates. By default,
     * this returns {@link #getKeyStore()}.
     *
     * @return a publisher of {@link KeyStore} updates for trust material
     */
    @NonNull
    default Publisher<@NonNull KeyStore> getTrustStore() {
        return getKeyStore();
    }
}
