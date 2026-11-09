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
package io.micronaut.dev.http;

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.type.Argument;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.livereload.LiveReloadScript;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.http.CaseInsensitiveMutableHttpHeaders;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

/**
 * Adds the LiveReload client script to HTML pages, and a no-store cache header so that a refresh
 * always reaches the server. Pages with a Content-Security-Policy, in a header or in a {@code <meta http-equiv>}
 * element, are left alone, as the script would be blocked; extension users turn the injection off in the manifest.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@ServerFilter("/**")
@Requires(classes = ServerFilter.class)
@DevelopmentActive
@Requires(beans = DevRuntime.class)
public final class LiveReloadScriptFilter implements Ordered {

    private static final String BODY_END = "</body>";
    // a policy a page declares itself applies as the header does; http-equiv as a whole attribute name, not data-http-equiv
    private static final Pattern CSP_META = Pattern.compile("<meta\\s(?:[^>]*\\s)?http-equiv\\s*=\\s*[\"']?\\s*content-security-policy\\s*[\"'\\s/>]", Pattern.CASE_INSENSITIVE);

    private final DevRuntime runtime;
    @Nullable
    private final DevStaticHtml staticHtml;
    // resolved on the first page a writer renders, never for a response that is not one
    private final BeanProvider<MessageBodyHandlerRegistry> bodyHandlers;
    // where a blocking writer renders a page, as the server writes it there
    private final BeanProvider<ExecutorService> blockingExecutor;

    LiveReloadScriptFilter(DevRuntime runtime, @Nullable DevStaticHtml staticHtml, BeanProvider<MessageBodyHandlerRegistry> bodyHandlers,
                           @Named(TaskExecutors.BLOCKING) BeanProvider<ExecutorService> blockingExecutor) {
        this.runtime = runtime;
        this.staticHtml = staticHtml;
        this.bodyHandlers = bodyHandlers;
        this.blockingExecutor = blockingExecutor;
    }

    /**
     * Ahead of the built-in filter phases, so that it sees the response after the filters of those phases and of the
     * default order: after a filter of the application or a module changed it, as views' filter swaps the model for
     * the view to render, and after a policy header was set. A filter ordered ahead of this one still sees the
     * response after it.
     *
     * @return The order
     */
    @Override
    public int getOrder() {
        // ahead of the response-header populator too, which has FIRST.before() and may add a policy
        return ServerFilterPhase.FIRST.before() - 1;
    }

    /**
     * Injects the script into an HTML response. A page a blocking writer renders, as a view is, is rendered on the
     * blocking executor, as the server would write it there; any other response is handled on the spot. The response
     * to a HEAD request is handled as the one to a GET, so that it has the same length and cache header.
     *
     * @param request The request
     * @param response The response
     * @return The response once a page a blocking writer renders has the script, null when it was handled on the spot
     */
    @ResponseFilter
    @SuppressWarnings("unchecked")
    public @Nullable CompletionStage<@Nullable MutableHttpResponse<?>> inject(HttpRequest<?> request, MutableHttpResponse<?> response) {
        LiveReloadServer server = runtime.liveReload().orElse(null);
        if (server == null || !runtime.manifest().liveReload().injectScript()) {
            return null;
        }
        if (response.getHeaders().contains(HttpHeaders.CONTENT_SECURITY_POLICY) || isEncoded(response)) {
            return null;
        }
        if (request.getMethod() != HttpMethod.HEAD || response.getBody().isPresent()) {
            return inject(request, response, server.port());
        }
        // the route executor moved the body of a HEAD response aside, and the server writes it to give the headers a
        // GET gets: the page is handled here as for a GET, then put back aside
        Object headBody = RouteAttributes.getHeadBody(response).orElse(null);
        if (headBody == null || headBody instanceof ReferenceCounted || headBody instanceof BodyElements<?>) {
            // released or discarded already: the server does not write it either
            return null;
        }
        ((MutableHttpResponse<Object>) response).body(headBody);
        CompletionStage<@Nullable MutableHttpResponse<?>> rendered = inject(request, response, server.port());
        if (rendered == null) {
            setAside(response);
            return null;
        }
        return rendered.thenApply(handled -> {
            setAside(response);
            return handled;
        });
    }

    /**
     * Moves the body of the response to a HEAD request back aside, where the server writes it from to give the
     * headers, without sending it.
     *
     * @param response The response
     */
    private static void setAside(MutableHttpResponse<?> response) {
        Object body = response.getBody().orElse(null);
        response.body(null);
        if (body != null) {
            RouteAttributes.setHeadBody(response, body);
        }
    }

    /**
     * Injects the script into an HTML response, whose body is the one a GET request gets.
     *
     * @param request The request
     * @param response The response
     * @param port The LiveReload server's port
     * @return The response once a page a blocking writer renders has the script, null when it was handled on the spot
     */
    private @Nullable CompletionStage<@Nullable MutableHttpResponse<?>> inject(HttpRequest<?> request, MutableHttpResponse<?> response, int port) {
        Object body = response.getBody().orElse(null);
        if (body == null) {
            return null;
        }
        MediaType contentType = response.getContentType().orElse(null);
        // bytes are decoded and encoded again with the charset the response declares, not a guess
        Charset charset = contentType != null ? contentType.getCharset().orElse(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
        String html;
        if (staticHtml != null && DevStaticHtml.isFile(body)) {
            // a page the static resource resolver serves has no content type yet: the file says what it is
            DevStaticHtml.Page page = staticHtml.read(body, response);
            if (page == null) {
                return null;
            }
            html = page.html();
            charset = page.charset();
            response.contentType(new MediaType(MediaType.TEXT_HTML, Map.of(MediaType.CHARSET_PARAMETER, charset.name())));
            // the file's stream is consumed: whatever the injection decides, the response now carries the bytes read
            body = html.getBytes(charset);
            response.body(body);
        } else {
            if (contentType == null) {
                // the server sets the type the route produces as it writes the response, after the filters
                contentType = producedType(response);
                if (contentType == null) {
                    return null;
                }
                charset = contentType.getCharset().orElse(StandardCharsets.UTF_8);
            }
            if (!MediaType.TEXT_HTML.equals(contentType.getName())) {
                return null;
            }
            if (body instanceof CharSequence sequence) {
                html = sequence.toString();
            } else if (body instanceof byte[] bytes) {
                html = new String(bytes, charset);
            } else {
                // a page a body writer renders, as a view is: rendered here, so that the script goes into it
                return render(request, response, body, contentType, charset, port);
            }
            response.contentType(contentType);
        }
        String injected = inject(html, port);
        if (injected != null) {
            response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
            replaceBody(response, injected, charset);
        }
        return null;
    }

    /**
     * The type a route produces when it produces only one, which the server gives a response that has none when it
     * writes it. A route producing several negotiates one with the request as it writes the response: it is left alone.
     *
     * @param response The response
     * @return The type, or null if the response did not come from a route that declares exactly one
     */
    private static @Nullable MediaType producedType(MutableHttpResponse<?> response) {
        List<MediaType> produces = RouteAttributes.getRouteInfo(response).map(RouteInfo::getProduces).orElse(List.of());
        return produces.size() == 1 ? produces.get(0) : null;
    }

    /**
     * Renders a body of another type than text with the writer the server would use for it, such as the model and
     * view a views filter put in the response, whose writer renders the template into the response's stream, and
     * sends the page as rendered: with the script when it has a body end, byte for byte as written otherwise. The
     * writer is chosen as the server chooses it: the one the response carries, unless it cannot write the body, then
     * the registry's for the body's class. A blocking writer renders on the blocking executor. A body no writer takes
     * is left alone.
     *
     * @param request The request
     * @param response The response
     * @param body The body
     * @param contentType The HTML content type of the response
     * @param charset The charset of the page
     * @param port The LiveReload server's port
     * @return The response once rendered on the blocking executor, null if it was rendered here or left alone
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable CompletionStage<@Nullable MutableHttpResponse<?>> render(HttpRequest<?> request, MutableHttpResponse<?> response, Object body,
                                                                               MediaType contentType, Charset charset, int port) {
        if (isStream(body)) {
            return null;
        }
        MessageBodyHandlerRegistry registry = bodyHandlers.orElse(null);
        if (registry == null) {
            return null;
        }
        List<MediaType> types = List.of(contentType);
        Argument<Object> type = (Argument) RouteAttributes.getRouteInfo(response).map(RouteInfo::getResponseBodyType).orElse(Argument.of(body.getClass()));
        MessageBodyWriter<Object> writer = (MessageBodyWriter<Object>) response.getBodyWriter().orElse(null);
        if (writer == null) {
            writer = registry.findWriter(type, types).orElse(null);
        }
        if (writer == null || !type.isInstance(body) || !writer.isWriteable(type, contentType)) {
            // the body is not of the type the route declares, as a model and view a filter swapped in is not
            type = (Argument) Argument.of(body.getClass());
            writer = registry.findWriter(type, types).orElse(null);
            if (writer == null) {
                return null;
            }
        }
        Argument<Object> bodyType = type;
        MessageBodyWriter<Object> bodyWriter = writer;
        Runnable page = () -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            // written against a copy of the headers, so that a writer that gives up partway leaves the response as it was
            MutableHttpHeaders headers = copyOf(response.getHeaders());
            try {
                // a writer may read the request from the context, as the views' writer does to resolve the locale
                ServerRequestContext.with(request, () -> bodyWriter.writeTo(bodyType, contentType, body, headers, out));
            } catch (UnsupportedOperationException e) {
                // a writer that only writes through the server's own response path
                return;
            }
            // the headers the writer set, the type and its charset included, as the server would send them
            replaceHeaders(response, headers);
            // the type the writer set, as the server would send it; the route's otherwise
            MediaType written = response.getContentType().orElse(contentType);
            response.contentType(written);
            Charset writtenCharset = written.getCharset().orElse(charset);
            // a policy the writer declared would block the script, as a page that declared one before is left alone; an
            // encoded page cannot be spliced into, and what the writer marked as something else than a page is no page
            String injected = response.getHeaders().contains(HttpHeaders.CONTENT_SECURITY_POLICY) || isEncoded(response)
                || !MediaType.TEXT_HTML.equals(written.getName()) ? null : inject(out.toString(writtenCharset), port);
            if (injected != null) {
                response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
                // sent as rendered here rather than rendered twice
                replaceBody(response, injected, writtenCharset);
            } else {
                // without the script, the bytes as written and the writer's own type, which may declare no charset
                byte[] bytes = out.toByteArray();
                response.body(bytes);
                response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
                response.contentLength(bytes.length);
            }
        };
        if (!writer.isBlocking()) {
            page.run();
            return null;
        }
        PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        return CompletableFuture.supplyAsync(() -> {
            try (PropagatedContext.Scope ignored = propagatedContext.propagate()) {
                page.run();
            }
            return response;
        }, blockingExecutor.get());
    }

    /**
     * Whether the body is streamed, or read from a file or a buffer, rather than rendered: the server writes it through
     * its own response path, as it comes, and the page is not rendered here.
     *
     * @param body The body
     * @return Whether it is streamed
     */
    private static boolean isStream(Object body) {
        return body instanceof InputStream || body instanceof File || body instanceof ByteBody || body instanceof ByteBuffer<?>
            || body instanceof FileCustomizableResponseType || body instanceof BodyElements<?> || Publishers.isConvertibleToPublisher(body);
    }

    /**
     * A copy of the headers, which a writer can change without changing the response.
     *
     * @param headers The headers
     * @return The copy
     */
    private static MutableHttpHeaders copyOf(MutableHttpHeaders headers) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        for (String name : headers.names()) {
            values.put(name, headers.getAll(name));
        }
        return new CaseInsensitiveMutableHttpHeaders(values, ConversionService.SHARED);
    }

    /**
     * Gives the response the headers a writer left, those it removed included.
     *
     * @param response The response
     * @param written The headers the writer wrote to
     */
    private static void replaceHeaders(MutableHttpResponse<?> response, MutableHttpHeaders written) {
        MutableHttpHeaders headers = response.getHeaders();
        for (String name : List.copyOf(headers.names())) {
            if (!written.contains(name)) {
                headers.remove(name);
            }
        }
        for (String name : written.names()) {
            List<String> values = written.getAll(name);
            if (!values.equals(headers.getAll(name))) {
                headers.remove(name);
                for (String value : values) {
                    headers.add(name, value);
                }
            }
        }
    }

    /**
     * Whether the body is compressed or otherwise encoded, which the script cannot be spliced into.
     *
     * @param response The response
     * @return True if a content coding other than identity applies
     */
    static boolean isEncoded(MutableHttpResponse<?> response) {
        String encoding = response.getHeaders().get(HttpHeaders.CONTENT_ENCODING);
        return encoding != null && !encoding.isBlank() && !"identity".equalsIgnoreCase(encoding.trim());
    }

    /**
     * Replaces the body with the page carrying the script, and the framing headers with ones that describe it: a
     * length set by a controller or the static resource resolver is that of the page without the script, and a
     * transfer coding set for that page no longer applies. The page is sent as the bytes counted, in the charset the
     * content type now declares, so that no writer encodes it again with another charset.
     *
     * @param response The response
     * @param injected The page with the script
     * @param charset The charset the page is encoded with
     */
    static void replaceBody(MutableHttpResponse<?> response, String injected, Charset charset) {
        byte[] bytes = injected.getBytes(charset);
        MediaType contentType = response.getContentType().orElse(MediaType.TEXT_HTML_TYPE);
        if (contentType.getCharset().isEmpty()) {
            response.contentType(new MediaType(contentType.getName(), Map.of(MediaType.CHARSET_PARAMETER, charset.name())));
        }
        response.body(bytes);
        response.getHeaders().remove(HttpHeaders.TRANSFER_ENCODING);
        response.contentLength(bytes.length);
    }

    /**
     * The page with the script tag before its closing body tag.
     *
     * @param html The page
     * @param port The server's port
     * @return The page with the tag, or null if the page has no body end to put it before, or declares a
     *         Content-Security-Policy in a {@code <meta>} element that would block the script
     */
    @Nullable
    static String inject(String html, int port) {
        if (CSP_META.matcher(html).find()) {
            return null;
        }
        int end = bodyEnd(html);
        if (end < 0) {
            return null;
        }
        return html.substring(0, end) + LiveReloadScript.tag(port) + html.substring(end);
    }

    /**
     * The index of the last closing body tag, whatever its case. The page itself is searched: a copy changed to lower
     * case can be longer than the page, as U+0130 becomes two characters, and its index would not be the page's.
     *
     * @param html The page
     * @return The index, or -1 if the page has none
     */
    static int bodyEnd(String html) {
        for (int i = html.lastIndexOf('<'); i >= 0; i = html.lastIndexOf('<', i - 1)) {
            if (html.regionMatches(true, i, BODY_END, 0, BODY_END.length())) {
                return i;
            }
        }
        return -1;
    }
}
