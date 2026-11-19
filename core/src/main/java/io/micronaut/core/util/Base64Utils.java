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
package io.micronaut.core.util;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.NonNull;

import java.util.Base64;

/**
 * Protection against Java4Shell (CVE-2026-114938): Base64 decoding that keeps attacker supplied input
 * handled by this library from reaching the vulnerable JDK Base64 decode intrinsic.
 *
 * <p>This is hardening, not a fix. The fix is upgrading the JDK, or running with
 * {@code -XX:-UseBASE64Intrinsics}. This class only closes the paths through which Micronaut Core
 * decodes Base64, such as the {@code Authorization} header bound to a {@code BasicAuth} argument.</p>
 *
 * <p>Affected JDK versions corrupt the heap when the AVX-512 VBMI Base64 decode intrinsic receives input
 * whose final, incomplete four character unit ends with padding. The JDK rejects such input with an
 * {@link IllegalArgumentException}, but only after the out of bounds write. This class rejects that input
 * before it is passed to the JDK.</p>
 *
 * <p>The guard assumes, as the published analysis states, that the intrinsic derives its output length
 * from trailing padding only: input that does not end in {@code =} takes a path that does not underflow.
 * Revisit this check if a later advisory widens the trigger.</p>
 *
 * <p>Only the basic and URL decoders are wrapped. The MIME decoder skips characters outside the alphabet,
 * so the length based check would reject valid MIME input.</p>
 *
 * @author Sergio del Amo
 * @since 5.2.26
 * @see <a href="https://calif.io/java4shell/">Java4Shell (CVE-2026-114938)</a>
 * @see <a href="https://openjdk.org/groups/vulnerability/advisories/2026-11-17">OpenJDK Vulnerability Advisory 2026/11/17</a>
 */
@Internal
public final class Base64Utils {

    /**
     * Number of Base64 characters that encode three bytes.
     */
    private static final int UNIT_LENGTH = 4;

    private static final char PADDING = '=';

    private Base64Utils() {
    }

    /**
     * Decodes a String with the {@link Base64#getDecoder() basic Base64 decoder}.
     *
     * <p>Input that ends with padding but whose length is not a multiple of four is rejected before
     * it is passed to the JDK. The JDK decoder rejects that input as well, so the set of accepted inputs
     * and the decoded bytes are the same as with {@link Base64.Decoder#decode(String)}; only the
     * exception message differs.</p>
     *
     * @param src the Base64 encoded String to decode
     * @return A newly-allocated byte array containing the decoded bytes.
     * @throws IllegalArgumentException if {@code src} is not valid Base64
     */
    public static byte @NonNull [] decode(@NonNull String src) {
        rejectIncompletePaddedUnit(src);
        return Base64.getDecoder().decode(src);
    }

    /**
     * Decodes a String with the {@link Base64#getUrlDecoder() URL and filename safe Base64 decoder}.
     *
     * <p>Input that ends with padding but whose length is not a multiple of four is rejected before
     * it is passed to the JDK. The JDK decoder rejects that input as well, so the set of accepted inputs
     * and the decoded bytes are the same as with {@link Base64.Decoder#decode(String)}; only the
     * exception message differs.</p>
     *
     * @param src the URL safe Base64 encoded String to decode
     * @return A newly-allocated byte array containing the decoded bytes.
     * @throws IllegalArgumentException if {@code src} is not valid URL safe Base64
     */
    public static byte @NonNull [] decodeUrl(@NonNull String src) {
        rejectIncompletePaddedUnit(src);
        return Base64.getUrlDecoder().decode(src);
    }

    private static void rejectIncompletePaddedUnit(String src) {
        if (!src.isEmpty() && src.charAt(src.length() - 1) == PADDING && src.length() % UNIT_LENGTH != 0) {
            throw new IllegalArgumentException("Invalid Base64 padding");
        }
    }
}
