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
import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Holds requests while a reload is in progress, and answers them with the diagnostics while the last
 * compilation failed: a page for a browser, a structured 503 for anything else. The stale generation
 * keeps serving what compiles.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@ServerFilter("/**")
@Requires(classes = ServerFilter.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
public final class DevGateFilter {

    /**
     * The keys of the JSON body a client that is not a browser receives.
     */
    static final String KEY_MESSAGE = "message";
    static final String KEY_KIND = "kind";
    static final String KEY_AT = "at";
    static final String KEY_DIAGNOSTICS = "diagnostics";
    static final String KEY_SEVERITY = "severity";
    static final String KEY_FILE = "file";
    static final String KEY_LINE = "line";
    static final String KEY_COLUMN = "column";

    private final DevRuntime runtime;
    @Nullable
    private final DevErrorPage errorPage;

    DevGateFilter(DevRuntime runtime, @Nullable DevErrorPage errorPage) {
        this.runtime = runtime;
        this.errorPage = errorPage;
    }

    /**
     * Waits for a reload in progress, then lets the request through or answers it with the failure.
     *
     * @param request The request
     * @return The failure response, or null to proceed
     */
    @RequestFilter
    public CompletableFuture<@Nullable HttpResponse<?>> gate(HttpRequest<?> request) {
        return runtime.whenReady().thenApply(ignored -> runtime.lastFailure().map(failure -> respond(request, failure)).orElse(null));
    }

    private HttpResponse<?> respond(HttpRequest<?> request, CompileFailure failure) {
        boolean html = request.getHeaders().accept().stream().anyMatch(type -> type.getName().equals(MediaType.TEXT_HTML));
        if (html) {
            HttpResponse<?> unavailable = HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE);
            // the page the server shows for any other error, when the server is here to render it
            String body = errorPage != null ? errorPage.render(request, unavailable, failure) : page(failure);
            return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.TEXT_HTML_TYPE).body(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(KEY_MESSAGE, "Compilation failed");
        body.put(KEY_KIND, failure.kind().name().toLowerCase(java.util.Locale.ROOT));
        body.put(KEY_AT, failure.at().toString());
        List<Map<String, Object>> diagnostics = new ArrayList<>();
        for (CompileDiagnostic diagnostic : failure.diagnostics()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put(KEY_SEVERITY, diagnostic.severity().name());
            entry.put(KEY_MESSAGE, diagnostic.message());
            if (diagnostic.file() != null) {
                entry.put(KEY_FILE, diagnostic.file().toString());
                entry.put(KEY_LINE, diagnostic.line());
                entry.put(KEY_COLUMN, diagnostic.column());
            }
            diagnostics.add(entry);
        }
        body.put(KEY_DIAGNOSTICS, diagnostics);
        return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE).contentType(MediaType.APPLICATION_JSON_TYPE).body(body);
    }

    /**
     * The page used when no server-side error page provider is available.
     */
    static String page(CompileFailure failure) {
        StringBuilder page = new StringBuilder("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>Compilation failed</title>")
            .append("<style>body{font-family:ui-monospace,Menlo,monospace;margin:2rem;background:#1e1e1e;color:#ddd}h1{color:#f66}pre{background:#111;padding:1rem;overflow:auto}.file{color:#8cf}</style>")
            .append("</head><body><h1>Compilation failed</h1><p>The previous version keeps running. Fix the ")
            .append(escape(failure.kind().name().toLowerCase(java.util.Locale.ROOT)))
            .append(" sources and the page reloads.</p>");
        for (CompileDiagnostic diagnostic : failure.diagnostics()) {
            page.append("<pre>");
            if (diagnostic.file() != null) {
                page.append("<span class=\"file\">").append(escape(diagnostic.file().toString())).append(':').append(diagnostic.line()).append("</span>\n");
            }
            page.append(escape(diagnostic.severity().name())).append(": ").append(escape(diagnostic.message())).append("</pre>");
        }
        return page.append("</body></html>").toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
