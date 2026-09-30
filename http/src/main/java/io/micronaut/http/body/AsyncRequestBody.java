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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.OptionalLong;
import java.util.concurrent.CompletionStage;

/**
 * The body of a request received by the server, read asynchronously, once: a parameter of a
 * controller method or of a request filter method. Nothing is decoded before the method asks for
 * it, and the method chooses how: decoded to a type, as text or bytes, element by element, as a
 * form, part by part, written to a file, or taken as is to pass it on.
 *
 * <pre>{@code
 * @Post("/people/import")
 * CompletionStage<HttpResponse<?>> importPeople(AsyncRequestBody body) {
 *     return body.elements(Person.class)
 *         .forEach(people::saveAsync)
 *         .thenApply(done -> HttpResponse.accepted());
 * }
 * }</pre>
 *
 * <p>What a read left open, e.g. the parts of a form that were not read, or a file that is still
 * written, is released when the method completed, normally or exceptionally: when the value or the
 * stage it returned completed, before the response is written or, for a filter method, before the
 * filter chain continues. A read the method did not wait for is aborted then, so the work on the
 * body must be part of the stage the method returns. A controller method that answers with a
 * streamed response, e.g. a {@code Publisher} of many items, keeps the body until the stream
 * ends: completes, fails or is cancelled, e.g. the client disconnected, so the stream can be
 * made of the reads of the body. The reads of the {@link #copy() copies} of the body are released
 * with it.</p>
 *
 * <h2>One read</h2>
 * <p>The body is read once: the first of {@link #body(Argument)}, {@link #text()},
 * {@link #bytes(int)}, {@link #elements(Argument)}, {@link #transferTo(Path)}, {@link #form()},
 * {@link #parts()}, {@link #takeBody()} and {@link #discardBody()} (and their overloads) owns the
 * body, and a second one fails with an {@link IllegalStateException} that names the first. A
 * body that is not read is discarded when the request ends. To read the body more than once,
 * read a {@link #copy()}.</p>
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
 * {@code micronaut.server.max-request-size}. There is no {@code Publisher} in this API: a method
 * that needs one can take the body with {@link #takeBody()}.</p>
 *
 * <h2>The body of the request</h2>
 * <p>It is the body of the request the route is invoked with, as the filters left it: the bytes
 * of its {@link io.micronaut.http.ServerHttpRequest#byteBody() byteBody()}, which keeps its
 * contract: the request owns the bytes, and a caller that reads them {@link ByteBody#split()
 * splits} them first, like a filter does. {@link #takeBody()} moves the body to the handler.
 * Reading the body decodes no body for the request: its
 * {@link io.micronaut.http.HttpRequest#getBody() getBody()} does not change.</p>
 *
 * <h2>Copies</h2>
 * <p>{@link #copy()} returns another {@code AsyncRequestBody} over a split of the bytes of the
 * body: reading the copy does not consume the body, which the route, or another reader, can still
 * read in any way. The copy has its own one read, and so does the body it was copied from.</p>
 *
 * <p>The bytes the copy reads are kept for the body until it is read, so a copy is held to the
 * limit of the server for buffered request content ({@code micronaut.server.max-request-buffer-size})
 * as a whole, like a {@code @Body} argument of a filter, and a body larger than the limit fails with
 * {@code 413}: the streaming reads of a copy, {@link #elements(Argument)}, {@link #parts()} and
 * {@link #transferTo(Path)}, buffer the body too. A copy is for looking at the body, not for
 * streaming a large upload. {@link #form()} of a copy is the one form of the request, shared with
 * the form arguments, which do not decode it a second time: the copy reads it from a split of the
 * bytes, unless a form argument read it before, so the route can still read the body in any way,
 * and the form is held to the limit as a whole like the other reads of a copy. {@link #parts()}
 * of a copy decodes the form a second time, from a split of the bytes.</p>
 *
 * <h2>Filters</h2>
 * <p>A request filter method can read the body with an {@code AsyncRequestBody} before the route
 * runs. The arguments of a filter look at the body, the reads of an {@code AsyncRequestBody}
 * consume it, and the reads of a copy look at it:</p>
 * <ul>
 *     <li>A {@code @Body} argument reads a buffered copy of the bytes, and the {@code FormData},
 *     {@code FileUpload} and {@code @Part} arguments read the one form of the request, which the
 *     filters and the route share: the route can still read the body.</li>
 *     <li>Every read of an {@code AsyncRequestBody}, {@link #body(Argument)}, {@link #text()},
 *     {@link #bytes(int)}, {@link #elements(Argument)}, {@link #transferTo(Path)}, {@link #form()},
 *     {@link #parts()}, {@link #takeBody()} and {@link #discardBody()}, consumes the body: what the
 *     filter does not read is discarded, and a route that reads the body after it, with an
 *     {@code AsyncRequestBody}, a {@code @Body} argument or a form argument ({@code FormData},
 *     {@code FileUpload}, {@code @Part}), fails with an {@link IllegalStateException} that names
 *     the read of the filter, answered with {@code 500}. A route that does not read the body
 *     answers. These reads are for a filter that consumes the body itself, e.g. to forward it or
 *     to reject the request.</li>
 *     <li>The reads of a {@link #copy()} leave the body for the route, which can read it in any
 *     way. For a form, a {@code FormData} argument or {@code copy().form()} is the shared form,
 *     which the form arguments of the route do not decode a second time. After a
 *     {@code FormData} argument the route reads the form, not the bytes; after
 *     {@code copy().form()} it can read the body in any way, since the copy keeps the bytes, and
 *     the body is held to the buffer limit as a whole. {@code copy().parts()} decodes the form a
 *     second time, for a filter that must stream through the parts and still leave the body for
 *     the route.</li>
 * </ul>
 * <p>A filter that reads the body or a copy of it buffers the body before the route can read it.</p>
 *
 * <p>A filter that continues with a mutable request whose body it set, see
 * {@link io.micronaut.http.MutableHttpRequest#body(Object)}, replaces the bytes of the request
 * with that body. A body set to {@code null} is no body: {@link #hasBody()} is {@code false},
 * {@link #body(Argument)} completes like it does for a request without a body, with {@code null}
 * for a nullable type and otherwise with the error of a missing {@code @Body} argument,
 * {@link #text()} is empty, {@link #bytes(int)} is an empty array, and {@link #takeBody()} is an
 * empty body. A body set to an object is read with {@link #body(Argument)}, which converts it like
 * a {@code @Body} argument of a controller: the readers of the bytes, {@link #text()},
 * {@link #bytes(int)}, {@link #elements(Argument)}, {@link #transferTo(Path)}, {@link #form()},
 * {@link #parts()} and {@link #takeBody()}, fail with an {@link IllegalStateException}. A filter
 * that continues with another {@link io.micronaut.http.ServerHttpRequest} replaces the bytes with
 * its {@link io.micronaut.http.ServerHttpRequest#byteBody() byteBody()}. The body is read once
 * either way.</p>
 *
 * <h2>{@code 100 Continue}</h2>
 * <p>A request that expects {@code 100 Continue} is answered with {@code 100 Continue} when the
 * body is first read. A handler that answers without reading the body, e.g. with {@code 401},
 * never receives it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncRequestBody {

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
     * Read the whole body as text, in the charset of the request (the charset of its content
     * type, else the default charset of the server), with the limit of the server
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
     * Read the whole body as text, in the given charset, whatever the content type of the
     * request says.
     *
     * @param maximumBytes The maximum number of bytes to read, not characters, zero or more
     * @param charset      The charset of the body
     * @return Completes with the body, or exceptionally with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException} when it is larger
     * than the limit
     * @throws IllegalArgumentException if the limit is negative
     * @throws IllegalStateException    if the body was already read
     * @throws NullPointerException     if the charset is {@code null}
     */
    CompletionStage<String> text(int maximumBytes, Charset charset);

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
     * <p>The readers that decode the elements of JSON one at a time are those of
     * {@code micronaut-http-netty}, which the Netty server includes. A server that is not the
     * Netty server, e.g. a servlet server, needs {@code micronaut-http-netty} on its runtime
     * classpath to read the elements of JSON: without it, the first {@link BodyElements#next()}
     * fails with an {@link UnsupportedOperationException} that says so.</p>
     *
     * <p>An element is decoded in memory: an element larger than the limit of the server for
     * buffered request content ({@code micronaut.server.max-request-buffer-size}) fails
     * {@link BodyElements#next()} with a
     * {@link io.micronaut.http.exceptions.ContentLengthExceededException}, like a body read
     * whole that is too large. The body as a whole is not limited by it, not even the bytes the
     * server received before the elements are read.</p>
     *
     * @param type The type of an element
     * @param <T>  The type of an element
     * @return The elements, read as they are asked for. They are closed when the method completed, or when the stream of its streamed response ended
     * @throws IllegalStateException    if the body was already read
     * @throws IllegalArgumentException if the type of an element is a reactive or asynchronous
     * type, or an {@link java.io.InputStream}: an element is decoded whole
     */
    <T> BodyElements<T> elements(Argument<T> type);

    /**
     * Write the body to a new file. The file must not exist, and its directory must exist, like
     * for {@link io.micronaut.http.form.FileUpload#transferTo(Path)}: the body is written to a
     * temporary file next to it that is moved to the destination once complete. The body is not
     * held in memory, so the limit of the server for buffered request content
     * ({@code micronaut.server.max-request-buffer-size}) does not apply.
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
     * <p>It is the one form of the request, which the {@code FormData}, {@code FileUpload} and
     * {@code @Part} arguments share, but like the other reads it consumes the body: in a filter,
     * the route can no longer read the form, unless the filter reads the form of a
     * {@link #copy()} or takes a {@code FormData} argument instead.</p>
     *
     * @return Completes with the form, or exceptionally with an
     * {@link io.micronaut.http.exceptions.HttpStatusException} with the status
     * {@code 415 Unsupported Media Type} when the request has no form body
     * @throws IllegalStateException if the body was already read
     */
    CompletionStage<FormData> form();

    /**
     * Read the submitted form part by part, as it arrives. The parts are closed when the method
     * completed, or, for a controller method that answers with a streamed response, when its
     * stream ended, see {@link FormParts}.
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

    /**
     * A copy of the body: another {@code AsyncRequestBody} over a split of the bytes, whose reads
     * do not consume this body. The copy has its own one read, and this body keeps its own, e.g.
     * for the route after a filter read the copy. See the <i>Copies</i> section above.
     *
     * <p>Nothing is read before the copy is: the bytes are split when it is read. The bytes it
     * reads are kept for this body, so the copy is held to
     * {@code micronaut.server.max-request-buffer-size} as a whole, even for
     * {@link #transferTo(Path)}, {@link #elements(Argument)}, {@link #form()} and {@link #parts()},
     * with {@code 413} for a larger body. {@link #form()} of a copy is the one form of the
     * request, read from a split of the bytes unless a form argument read it before, and
     * {@link #parts()} of a copy decodes the form again from a split of the bytes. A copy
     * of a body a filter cleared or replaced with an object is that body too: no body, or the
     * object. A copy of a body that was consumed cannot be read either.</p>
     *
     * @return The copy
     * @since 5.3.0
     */
    AsyncRequestBody copy();
}
