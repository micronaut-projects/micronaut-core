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

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.DevelopmentMode;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.livereload.LiveReloadServer;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Adds the LiveReload client script to HTML pages, and a no-store cache header so that a refresh
 * always reaches the server. Pages with a Content-Security-Policy are left alone, as the script
 * would be blocked; extension users turn the injection off in the manifest.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@ServerFilter("/**")
@Requires(classes = ServerFilter.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
public final class LiveReloadScriptFilter {

    private static final String BODY_END = "</body>";

    private final DevRuntime runtime;
    @Nullable
    private final DevStaticHtml staticHtml;

    LiveReloadScriptFilter(DevRuntime runtime, @Nullable DevStaticHtml staticHtml) {
        this.runtime = runtime;
        this.staticHtml = staticHtml;
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
        if (response.getHeaders().contains(HttpHeaders.CONTENT_SECURITY_POLICY)) {
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
        } else if (contentType == null || !MediaType.TEXT_HTML.equals(contentType.getName())) {
            return;
        } else if (body instanceof CharSequence sequence) {
            html = sequence.toString();
        } else if (body instanceof byte[] bytes) {
            html = new String(bytes, charset);
        } else {
            return;
        }
        String injected = inject(html, server.port());
        if (injected == null) {
            return;
        }
        response.getHeaders().set(HttpHeaders.CACHE_CONTROL, "no-store");
        if (body instanceof byte[] || !(body instanceof CharSequence)) {
            response.body(injected.getBytes(charset));
        } else {
            response.body(injected);
        }
    }

    /**
     * The page with the script tag before its closing body tag.
     *
     * @param html The page
     * @param port The server's port
     * @return The page with the tag, or null if the page has no body end to put it before
     */
    @Nullable
    static String inject(String html, int port) {
        int end = html.toLowerCase(Locale.ROOT).lastIndexOf(BODY_END);
        if (end < 0) {
            return null;
        }
        return html.substring(0, end) + LiveReloadServer.scriptTag(port) + html.substring(end);
    }
}
