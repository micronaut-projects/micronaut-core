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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.exceptions.HttpStatusException;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Reads, decodes, rewrites and encodes again the body of a message for the body filters.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class BodyCodings {

    private BodyCodings() {
    }

    /**
     * Read a body up to a limit, decode it, pass it through the rewriters and encode it again.
     * The headers get the new {@code Content-Length}, and lose {@code Transfer-Encoding}.
     *
     * @param factory    The body factory
     * @param body       The body, claimed
     * @param maxBytes   The limit of the bytes, encoded and decoded
     * @param headers    The headers of the message
     * @param exchange   The exchange
     * @param rewriters  The rewriters, in order
     * @param tooLarge   The status of a body over the limit
     * @return The stage of the new body
     */
    static CompletionStage<CloseableAvailableByteBody> rewrite(ByteBodyFactory factory, CloseableByteBody body, long maxBytes, MutableHttpHeaders headers,
                                                              ProxyExchange exchange, List<BodyRewriter> rewriters, HttpStatus tooLarge) {
        String encoding = headers.get(HttpHeaders.CONTENT_ENCODING);
        return factory.limit(body, maxBytes).buffer().handle((bytes, failure) -> {
            if (failure != null) {
                throw tooLargeOr(failure, tooLarge, maxBytes);
            }
            try (bytes) {
                // a request whose coding cannot be read is the client's error, a response the upstream's
                HttpStatus undecodable = tooLarge == HttpStatus.BAD_GATEWAY ? HttpStatus.BAD_GATEWAY : HttpStatus.UNSUPPORTED_MEDIA_TYPE;
                return decode(bytes.toByteArray(), encoding, maxBytes, tooLarge, undecodable);
            }
        }).thenCompose(decoded -> {
            CompletionStage<byte[]> stage = CompletableFuture.completedFuture(decoded);
            for (BodyRewriter rewriter : rewriters) {
                stage = stage.thenCompose(current -> {
                    try {
                        return rewriter.rewrite(exchange, headers, current);
                    } catch (Exception e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
            }
            return stage;
        }).thenApply(rewritten -> {
            byte[] encoded = encode(rewritten, headers.get(HttpHeaders.CONTENT_ENCODING));
            headers.remove(HttpHeaders.TRANSFER_ENCODING);
            headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(encoded.length));
            return factory.adapt(encoded);
        });
    }

    private static RuntimeException tooLargeOr(Throwable failure, HttpStatus tooLarge, long maxBytes) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ContentLengthExceededException) {
            return new HttpStatusException(tooLarge, "The body is larger than the limit of " + maxBytes + " bytes of the body filters");
        }
        return cause instanceof RuntimeException re ? re : new CompletionException(cause);
    }

    /**
     * @param bytes    The bytes as sent
     * @param encoding The {@code Content-Encoding}, or {@code null}
     * @param maxBytes The limit of the decoded bytes
     * @param tooLarge The status of a body over the limit
     * @param undecodable The status of a body whose coding cannot be decoded
     * @return The decoded bytes
     */
    static byte[] decode(byte[] bytes, @Nullable String encoding, long maxBytes, HttpStatus tooLarge, HttpStatus undecodable) {
        String coding = coding(encoding);
        if (coding == null || bytes.length == 0) {
            return bytes;
        }
        try (InputStream in = switch (coding) {
            case "gzip", "x-gzip" -> new GZIPInputStream(new ByteArrayInputStream(bytes));
            case "deflate" -> new InflaterInputStream(new ByteArrayInputStream(bytes));
            default -> throw new HttpStatusException(undecodable, "The body filters cannot decode the Content-Encoding " + encoding);
        }) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(bytes.length * 2);
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) >= 0) {
                if (out.size() + (long) n > maxBytes) {
                    throw new HttpStatusException(tooLarge, "The decoded body is larger than the limit of " + maxBytes + " bytes of the body filters");
                }
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new HttpStatusException(undecodable == HttpStatus.BAD_GATEWAY ? HttpStatus.BAD_GATEWAY : HttpStatus.BAD_REQUEST, "The body cannot be decoded: " + e.getMessage());
        }
    }

    /**
     * @param bytes    The decoded bytes
     * @param encoding The {@code Content-Encoding} to apply, or {@code null}
     * @return The encoded bytes
     */
    static byte[] encode(byte[] bytes, @Nullable String encoding) {
        String coding = coding(encoding);
        if (coding == null) {
            return bytes;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, bytes.length / 2));
        try (OutputStream encoder = switch (coding) {
            case "gzip", "x-gzip" -> new GZIPOutputStream(out);
            case "deflate" -> new DeflaterOutputStream(out);
            default -> throw new HttpStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "The body filters cannot encode the Content-Encoding " + encoding);
        }) {
            encoder.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private static @Nullable String coding(@Nullable String encoding) {
        if (encoding == null) {
            return null;
        }
        String coding = encoding.strip().toLowerCase(Locale.ROOT);
        return coding.isEmpty() || coding.equals("identity") ? null : coding;
    }
}
