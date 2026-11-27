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
package io.micronaut.http.ssl;

/**
 * Policy for post-quantum TLS key exchange, see {@link SslConfiguration#getPqcPolicy()}.
 *
 * <p>Post-quantum key exchange groups such as {@code X25519MLKEM768} are only negotiated over
 * TLS 1.3.</p>
 *
 * @since 5.3.0
 */
public enum PqcPolicy {
    /**
     * Apply the configured key exchange groups, or the TLS implementation defaults, without any
     * post-quantum requirement. A post-quantum group is used when both peers support it and it
     * is enabled.
     */
    NONE,
    /**
     * Require at least one post-quantum group and keep classical groups as a fallback, so peers
     * without post-quantum support can still connect. Without configured groups, this enables
     * {@code X25519MLKEM768} first, followed by {@code x25519}, {@code secp256r1} and
     * {@code secp384r1}.
     */
    RELAXED,
    /**
     * Only allow post-quantum groups. Peers without post-quantum support fail the handshake.
     * Without configured groups, this enables {@code X25519MLKEM768}, {@code SecP256r1MLKEM768}
     * and {@code SecP384r1MLKEM1024}, as far as the TLS implementation supports them.
     */
    STRICT
}
