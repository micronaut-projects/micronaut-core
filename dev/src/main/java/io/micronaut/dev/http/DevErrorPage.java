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
import io.micronaut.http.server.exceptions.response.ErrorContext;
import io.micronaut.http.server.exceptions.response.HtmlErrorResponseBodyProvider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a failed compilation the way the server renders any other error page, through the
 * {@link HtmlErrorResponseBodyProvider} in use, so that the page looks like the default 404.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(classes = HtmlErrorResponseBodyProvider.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
final class DevErrorPage {

    private final HtmlErrorResponseBodyProvider bodyProvider;

    DevErrorPage(HtmlErrorResponseBodyProvider bodyProvider) {
        this.bodyProvider = bodyProvider;
    }

    /**
     * The page for a failure.
     *
     * @param request The request
     * @param response The 503 response the page goes into
     * @param failure The failure
     * @return The HTML
     */
    String render(HttpRequest<?> request, HttpResponse<?> response, CompileFailure failure) {
        List<String> messages = new ArrayList<>();
        messages.add("The " + failure.kind().name().toLowerCase(java.util.Locale.ROOT) + " sources do not compile; the previous version keeps running until they do.");
        for (CompileDiagnostic diagnostic : failure.diagnostics()) {
            StringBuilder line = new StringBuilder(diagnostic.severity().name()).append(": ");
            if (diagnostic.file() != null) {
                line.append(diagnostic.file()).append(':').append(diagnostic.line()).append(": ");
            }
            line.append(diagnostic.message());
            messages.add(line.toString());
        }
        return bodyProvider.body(ErrorContext.builder(request).errorMessages(messages).build(), response);
    }
}
