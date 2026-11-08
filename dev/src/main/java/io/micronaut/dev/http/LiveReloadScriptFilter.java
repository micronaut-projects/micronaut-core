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
import io.micronaut.core.type.Argument;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.livereload.LiveReloadScript;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.RouteInfo;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
public final class LiveReloadScriptFilter {

    private static final String BODY_END = "</body>";
    // a policy a page declares itself applies as the header does; http-equiv as a whole attribute name, not data-http-equiv
    private static final Pattern CSP_META = Pattern.compile("<meta\\s(?:[^>]*\\s)?http-equiv\\s*=\\s*[\"']?\\s*content-security-policy\\s*[\"'\\s/>]", Pattern.CASE_INSENSITIVE);

    private final DevRuntime runtime;
    @Nullable
    private final DevStaticHtml staticHtml;
    // resolved on the first page a writer renders, never for a response that is not one
    private final BeanProvider<MessageBodyHandlerRegistry> bodyHandlers;

    LiveReloadScriptFilter(DevRuntime runtime, @Nullable DevStaticHtml staticHtml, BeanProvider<MessageBodyHandlerRegistry> bodyHandlers) {
        this.runtime = runtime;
        this.staticHtml = staticHtml;
        this.bodyHandlers = bodyHandlers;
    }

    /**
     * Injects the script into an HTML response.
     *
     * @param request The request
     * @param response The response
     */
    @ResponseFilter
    public void inject(HttpRequest<?> request, MutableHttpResponse<?> response) {
        LiveReloadServer server = runtime.liveReload().orElse(null);
        if (server == null || !runtime.manifest().liveReload().injectScript()) {
            return;
        }
        if (response.getHeaders().contains(HttpHeaders.CONTENT_SECURITY_POLICY) || isEncoded(response)) {
            return;
        }
        Object body = response.getBody().orElse(null);
        if (body == null) {
            return;
        }
        MediaType contentType = response.getContentType().orElse(null);
        // bytes are decoded and encoded again with the charset the response declares, not a guess
        Charset charset = contentType != null ? contentType.getCharset().orElse(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
        String html;
        boolean rendered = false;
        if (staticHtml != null && DevStaticHtml.isFile(body)) {
            // a page the static resource resolver serves has no content type yet: the file says what it is
            DevStaticHtml.Page page = staticHtml.read(body, response);
            if (page == null) {
                return;
            }
            html = page.html();
            charset = page.charset();
            response.contentType(new MediaType(MediaType.TEXT_HTML, java.util.Map.of(MediaType.CHARSET_PARAMETER, charset.name())));
            // the file's stream is consumed: whatever the injection decides, the response now carries the bytes read
            body = html.getBytes(charset);
            response.body(body);
        } else {
            if (contentType == null) {
                // the server sets the type the route produces as it writes the response, after the filters
                contentType = producedType(response);
                if (contentType == null) {
                    return;
                }
                charset = contentType.getCharset().orElse(StandardCharsets.UTF_8);
            }
            if (!MediaType.TEXT_HTML.equals(contentType.getName())) {
                return;
            }
            if (body instanceof CharSequence sequence) {
                html = sequence.toString();
            } else if (body instanceof byte[] bytes) {
                html = new String(bytes, charset);
            } else {
                // a page a body writer renders, as a view is: rendered here, so that the script goes into it
                byte[] page = render(request, response, body, contentType);
                if (page == null) {
                    return;
                }
                html = new String(page, charset);
                rendered = true;
            }
            response.contentType(contentType);
        }
        String injected = inject(html, server.port());
        if (injected == null) {
            if (rendered) {
                // sent as rendered here rather than rendered twice
                replaceBody(response, html, charset);
            }
            return;
        }
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        replaceBody(response, injected, charset);
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
     * view a views filter put in the response, whose writer renders the template into the response's stream. Only
     * asked for an HTML response while the script is injected; a body no writer takes is left alone.
     *
     * @param request The request
     * @param response The response
     * @param body The body
     * @param contentType The HTML content type of the response
     * @return The page, or null if no writer renders the body
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private byte @Nullable [] render(HttpRequest<?> request, MutableHttpResponse<?> response, Object body, MediaType contentType) {
        MessageBodyHandlerRegistry registry = bodyHandlers.orElse(null);
        if (registry == null) {
            return null;
        }
        Argument<Object> type = (Argument) Argument.of(body.getClass());
        MessageBodyWriter<Object> writer = registry.findWriter(type, List.of(contentType)).orElse(null);
        if (writer == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // a writer may read the request from the context, as the views' writer does to resolve the locale
        ServerRequestContext.with(request, () -> writer.writeTo(type, contentType, body, response.getHeaders(), out));
        return out.toByteArray();
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
            response.contentType(new MediaType(contentType.getName(), java.util.Map.of(MediaType.CHARSET_PARAMETER, charset.name())));
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
        int end = html.toLowerCase(Locale.ROOT).lastIndexOf(BODY_END);
        if (end < 0) {
            return null;
        }
        return html.substring(0, end) + LiveReloadScript.tag(port) + html.substring(end);
    }
}
