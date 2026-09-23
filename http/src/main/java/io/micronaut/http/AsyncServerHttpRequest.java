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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * A request received by the server whose body is read asynchronously, by the handler, once: the
 * request an asynchronous handler function receives. Nothing is decoded before the handler asks
 * for it, and the handler chooses how: decoded to a type, as text or bytes, element by element,
 * as a form, part by part, written to a file, or taken as is to pass it on.
 *
 * <pre>{@code
 * routes.asyncPOST("/people", (request, pathVariables) -> request.body(Person.class)
 *     .thenApply(person -> HttpResponse.created(people.save(person))));
 * routes.asyncPOST("/people/import", (request, pathVariables) -> request.elements(Person.class)
 *     .forEach(people::saveAsync)
 *     .thenApply(done -> HttpResponse.accepted()));
 * }</pre>
 *
 * <h2>One read</h2>
 * <p>The body is read once: the first of {@link #body(Argument)}, {@link #text()},
 * {@link #bytes(int)}, {@link #elements(Argument)}, {@link #transferTo(Path)}, {@link #form()},
 * {@link #parts()}, {@link #takeBody()} and {@link #discardBody()} (and their overloads) owns the
 * body, and a second one fails with an {@link IllegalStateException} that names the first. A
 * body that is not read is discarded when the request ends.</p>
 *
 * <h2>Decoding and failures</h2>
 * <p>{@link #body(Argument)} decodes the body like a {@code @Body} argument of a controller: with
 * the same message body readers, the same media type negotiation, the same limit
 * ({@code micronaut.server.max-request-buffer-size}) and the same exceptions, so the error routes
 * answer a body that does not decode like they answer it for a controller, e.g. {@code 400} for
 * malformed JSON and {@code 413} for a body that is too large. The returned stages fail with
 * those exceptions; a stage that is returned by the handler reaches the error routes.</p>
 *
 * <h2>Streaming</h2>
 * <p>{@link #elements(Argument)} decodes a JSON array or a JSON stream one element at a time,
 * {@link #parts()} reads a form part by part, and {@link #transferTo(Path)} writes the body to a
 * file. Only what the handler asks for is read: the next element or part is received once the
 * previous one was handled. The limits of the server for buffered content apply to what is
 * decoded in memory, a single element or text field; the body as a whole is only limited by
 * {@code micronaut.server.max-request-size}. There is no {@code Publisher} in this API: a handler
 * that needs one can take the body with {@link #takeBody()}.</p>
 *
 * <h2>The body of the request</h2>
 * <p>{@link #byteBody()} keeps the contract of {@link ServerHttpRequest#byteBody()}: the request
 * owns the bytes, and a caller that reads them {@link ByteBody#split() splits} them first, like
 * a filter does. It is not the way for the handler to read the body: use {@link #takeBody()},
 * which moves the body to the handler. {@link #getBody()} is always empty: no binder decodes a
 * body for the handler.</p>
 *
 * <h2>Filters</h2>
 * <p>A filter that reads the body, e.g. with a {@code @Body} argument, reads a copy, and is not
 * the handler's read. It buffers the body before the handler can read it.</p>
 *
 * <h2>{@code 100 Continue}</h2>
 * <p>A request that expects {@code 100 Continue} is answered with {@code 100 Continue} when the
 * body is first read. A handler that answers without reading the body, e.g. with {@code 401},
 * never receives it.</p>
 *
 * @param <B> The body type of the request
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncServerHttpRequest<B> extends ServerHttpRequest<B> {

    /**
     * Whether the request has a body, without reading it: a body with a length other than zero,
     * or a body of unknown length.
     *
     * @return Whether the request has a body
     */
    boolean hasBody();

    /**
     * The size of the body the client declared, without reading it.
     *
     * @return The size in bytes, if known
     */
    OptionalLong expectedBodySize();

    /**
     * Decode the whole body to a type, like a {@code @Body} argument of a controller.
     *
     * @param type The type
     * @param <T>  The type
     * @return Completes with the body
     * @throws IllegalStateException    if the body was already read
     * @throws IllegalArgumentException if the type is a reactive type
     * @see #body(Argument)
     */
    <T> CompletionStage<@Nullable T> body(Class<T> type);

    /**
     * Decode the whole body to a type, like a {@code @Body} argument of a controller: with the
     * message body reader for the type and the {@code Content-Type} of the request, and the
     * annotations of the argument. A request without a body completes with {@code null} if the
     * type is {@link Argument#isNullable() nullable}, and fails like a missing {@code @Body}
     * argument otherwise ({@code 400}).
     *
     * <p>The body is buffered: it can be at most {@code micronaut.server.max-request-buffer-size}
     * bytes. Reactive types, such as {@code Publisher}, are not supported: to read a body
     * element by element, use {@link #elements(Argument)}, and to pass it on, {@link #takeBody()}.</p>
     *
     * @param type The type
     * @param <T>  The type
     * @return Completes with the body, {@code null} for a nullable type and a request without a
     * body, or exceptionally with the exception a controller's body binding fails with
     * @throws IllegalStateException    if the body was already read
     * @throws IllegalArgumentException if the type is a reactive or asynchronous type, or an
     * {@link java.io.InputStream}
     */
    <T> CompletionStage<@Nullable T> body(Argument<T> type);

    /**
     * Read the whole body as text, in the charset of the request, with the limit of the server
     * for buffered request content ({@code micronaut.server.max-request-buffer-size}).
     *
     * @return Completes with the body, or exceptionally with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when it is too large
     * @throws IllegalStateException if the body was already read
     */
    CompletionStage<String> text();

    /**
     * Read the whole body as text, in the charset of the request.
     *
     * @param maximumBytes The maximum number of bytes to read, not characters, zero or more
     * @return Completes with the body, or exceptionally with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when it is larger
     * than the limit
     * @throws IllegalArgumentException if the limit is negative
     * @throws IllegalStateException    if the body was already read
     */
    CompletionStage<String> text(int maximumBytes);

    /**
     * Read the whole body into memory.
     *
     * @param maximumBytes The maximum number of bytes to read, zero or more
     * @return Completes with the body, or exceptionally with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when it is larger
     * than the limit
     * @throws IllegalArgumentException if the limit is negative
     * @throws IllegalStateException    if the body was already read
     */
    CompletionStage<byte[]> bytes(int maximumBytes);

    /**
     * Decode the body element by element.
     *
     * @param type The type of an element
     * @param <T>  The type of an element
     * @return The elements, read as they are asked for
     * @throws IllegalStateException    if the body was already read
     * @throws IllegalArgumentException if the type of an element is a reactive or asynchronous
     * type, or an {@link java.io.InputStream}
     * @see #elements(Argument)
     */
    <T> BodyElements<T> elements(Class<T> type);

    /**
     * Decode the body element by element: a JSON array ({@code application/json}), whose
     * elements are decoded one at a time, or a JSON stream ({@code application/x-json-stream},
     * one JSON value per line). The next element is read when it is asked for. A request without
     * a body has no elements.
     *
     * <p>The format is chosen by the {@code Content-Type} of the request: a media type with no
     * reader that decodes elements one at a time fails the first {@link BodyElements#next()} with
     * an {@link io.micronaut.http.exceptions.HttpStatusException} with the status
     * {@code 415 Unsupported Media Type}.</p>
     *
     * @param type The type of an element
     * @param <T>  The type of an element
     * @return The elements, read as they are asked for. They are closed when the stage returned
     * by the handler route completes, or for a controller method, when the request ends
     * @throws IllegalStateException    if the body was already read
     * @throws IllegalArgumentException if the type of an element is a reactive or asynchronous
     * type, or an {@link java.io.InputStream}: an element is decoded whole
     */
    <T> BodyElements<T> elements(Argument<T> type);

    /**
     * Write the body to a new file. The file must not exist, and its directory must exist, like
     * for {@link io.micronaut.http.form.FileUpload#transferTo(Path)}: the body is written to a
     * temporary file next to it that is moved to the destination once complete.
     *
     * @param destination The file to create
     * @return Completes when the file was written
     * @throws IllegalStateException if the body was already read
     */
    CompletionStage<Void> transferTo(Path destination);

    /**
     * Read the whole submitted form, {@code application/x-www-form-urlencoded} or
     * {@code multipart/form-data}: text fields in memory, files stored with the
     * {@code micronaut.server.multipart} configuration. The files are owned by the request.
     *
     * @return Completes with the form, or exceptionally with an
     * {@link io.micronaut.http.exceptions.HttpStatusException} with the status
     * {@code 415 Unsupported Media Type} when the request has no form body
     * @throws IllegalStateException if the body was already read
     */
    CompletionStage<FormData> form();

    /**
     * Read the submitted form part by part, as it arrives. The parts are closed when the stage
     * returned by the handler route completes, or for a controller method, when the request ends.
     *
     * @return The parts of the form
     * @throws IllegalStateException if the body was already read
     * @throws io.micronaut.http.exceptions.HttpStatusException with the status
     * {@code 415 Unsupported Media Type} if the request has no form body
     */
    FormParts parts();

    /**
     * Take the body, unread and undecoded: the caller becomes responsible for it, and must
     * consume or close it, e.g. by sending it on with an HTTP client. Nothing is read before the
     * body is consumed.
     *
     * @return The body
     * @throws IllegalStateException if the body was already read
     */
    CloseableByteBody takeBody();

    /**
     * Discard the body without reading it.
     *
     * @return Completes when the body was discarded
     * @throws IllegalStateException if the body was already read
     */
    CompletionStage<Void> discardBody();
}
