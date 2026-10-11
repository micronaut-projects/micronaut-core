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
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.RequestAdmission;
import io.micronaut.core.annotation.Internal;
import io.micronaut.dev.CompileFailure;
import io.micronaut.dev.DevRuntime;
import io.micronaut.dev.compile.CompileDiagnostic;
import io.micronaut.dev.management.DevEndpoint;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteAttributes;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Holds requests while a reload is in progress, through the runtime's {@link RequestAdmission}, which server runtimes
 * holding requests at their own level follow too, up to its {@link RequestAdmission#holdTimeout()}
 * and then answers them with a 503 and a {@code Retry-After}; answers them with the diagnostics while the last
 * compilation failed: a page for a browser, a structured 503 for anything else. The stale generation
 * keeps serving what compiles. The development endpoint is never answered with the failure: reloading through it is
 * how to recover when the watcher missed the corrected edit.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@ServerFilter("/**")
@Requires(classes = ServerFilter.class)
@DevelopmentActive
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

    /**
     * What a request held longer than the hold is told: the reload finishes in a moment.
     */
    static final String RETRY_AFTER_SECONDS = "1";

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
        RequestAdmission admission = runtime.requestAdmission();
        Supplier<@Nullable HttpResponse<?>> then = () -> answer(request, runtime.lastFailure().orElse(null), errorPage);
        if (admission.isAdmitted()) {
            return CompletableFuture.completedFuture(then.get());
        }
        return hold(admission.whenAdmitted().toCompletableFuture(), admission.holdTimeout(), then);
    }

    /**
     * Holds a request until it is admitted, then answers it with what the supplier gives, null to proceed; a request
     * held longer than the hold is answered with a 503 the client can retry, never left hanging.
     */
    static CompletableFuture<@Nullable HttpResponse<?>> hold(CompletableFuture<Void> admitted, Duration hold, Supplier<@Nullable HttpResponse<?>> then) {
        if (admitted.isDone()) {
            return CompletableFuture.completedFuture(then.get());
        }
        // held until the batch is done, or until a restart drains this generation, which serves it before it stops
        CompletableFuture<@Nullable HttpResponse<?>> response = new CompletableFuture<>();
        admitted.whenComplete((ignored, error) -> {
            try {
                response.complete(then.get());
            } catch (RuntimeException e) {
                // the request's own error handling answers it, now rather than at the end of the hold
                response.completeExceptionally(e);
            }
        });
        response.completeOnTimeout(unavailable(hold), hold.toMillis(), TimeUnit.MILLISECONDS);
        return response;
    }

    private static HttpResponse<?> unavailable(Duration hold) {
        return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
            .contentType(MediaType.TEXT_PLAIN_TYPE)
            .body("The application is reloading and did not finish within " + hold.toMillis() + " ms; retry shortly.");
    }

    /**
     * Answers a request with the last compilation failure, or lets it through: always when nothing failed, and for the
     * routes of the development endpoint even when something did, since its status and its reload are how a developer
     * recovers when the watcher missed the corrected edit.
     *
     * @param request The request
     * @param failure The last compilation failure, null when the last compilation succeeded
     * @param errorPage The server's error page, if there is one
     * @return The failure response, or null to proceed
     */
    static @Nullable HttpResponse<?> answer(HttpRequest<?> request, @Nullable CompileFailure failure, @Nullable DevErrorPage errorPage) {
        if (failure == null || isDevEndpoint(request)) {
            return null;
        }
        return respond(request, failure, errorPage);
    }

    /**
     * Whether the request was routed to the development endpoint. The route decides rather than the path, so the
     * endpoint is recognised wherever {@code endpoints.all.path} or {@code endpoints.dev.path} put it, and an
     * application route that happens to live under {@code /dev} keeps the gate.
     */
    private static boolean isDevEndpoint(HttpRequest<?> request) {
        return RouteAttributes.getRouteMatch(request)
            .filter(MethodBasedRouteMatch.class::isInstance)
            .map(match -> ((MethodBasedRouteMatch<?, ?>) match).getDeclaringType() == DevEndpoint.class)
            .orElse(false);
    }

    /**
     * The answer to a request while the last compilation failed: a page for a browser, a structured 503 for anything else.
     *
     * @param request The request
     * @param failure The failure
     * @param errorPage The server's error page, if there is one
     * @return The response
     */
    static HttpResponse<?> respond(HttpRequest<?> request, CompileFailure failure, @Nullable DevErrorPage errorPage) {
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
