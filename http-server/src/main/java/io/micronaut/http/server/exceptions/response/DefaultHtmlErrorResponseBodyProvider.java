/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.LocaleResolver;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.util.HtmlSanitizer;
import jakarta.inject.Singleton;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.micronaut.http.HttpStatus.*;

/**
 * It generates HTML error response page for a given {@link HttpStatus}.
 * @author Sergio del Amo
 * @since 4.7.0
 */
@Internal
@Singleton
@Requires(missingBeans = HtmlErrorResponseBodyProvider.class)
final class DefaultHtmlErrorResponseBodyProvider implements HtmlErrorResponseBodyProvider {
    private static final Map<Integer, String> DEFAULT_ERROR_BOLD = Map.of(
            NOT_FOUND.getCode(), "The page you were looking for doesn’t exist",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "The file or data you are trying to upload exceeds the allowed size"
    );

    private static final Map<Integer, String> DEFAULT_ERROR = Map.of(
            NOT_FOUND.getCode(), "You may have mistyped the address or the page may have moved",
            REQUEST_ENTITY_TOO_LARGE.getCode(), "Please try again with a smaller file"
    );

    private static final String CSS = """
                                  *, *::before, *::after {
                                    box-sizing: border-box;
                                  }
                                  * {
                                    margin: 0;
                                  }
                                  html {
                                    font-size: 16px;
                                  }
                                  h2 {
                                      margin-top: -0.95em;
                                      font-size: 6em;
                                      opacity: .2;
                                  }
                                  body {
                                    background: #2559a7;
                                    color: #FFF;
                                    display: grid;
                                    font-family: -apple-system, "Helvetica Neue", Helvetica, sans-serif;
                                    font-size: clamp(1rem, 2.5vw, 2rem);
                                    -webkit-font-smoothing: antialiased;
                                    font-style: normal;
                                    font-weight: 400;
                                    letter-spacing: -0.0025em;
                                    line-height: 1.4;
                                    min-height: 100vh;
                                    place-items: center;
                                    text-rendering: optimizeLegibility;
                                    -webkit-text-size-adjust: 100%;
                                  }
                                  a {
                                    color: inherit;
                                    font-weight: 700;
                                    text-decoration: underline;
                                    text-underline-offset: 0.0925em;
                                  }
                                  b, strong {
                                    font-weight: 700;
                                  }
                                  i, em {
                                    font-style: italic;
                                  }
                                  main {
                                    display: grid;
                                    gap: 1em;
                                    padding: 2em;
                                    place-items: center;
                                    text-align: center;
                                  }
                                  main header {
                                    width: min(100%, 18em);
                                  }
                                  main header svg {
                                    height: auto;
                                    max-width: 100%;
                                    width: 100%;
                                  }
                                  main article {
                                    margin-top: -0.95em;
                                    width: min(100%, 30em);
                                  }
                                  main article p {
                                    font-size: 75%;
                                  }
                                  main article br {
                                    display: none;
                                    @media(min-width: 48em) {
                                      display: inline;
                                    }
                                  }
            """;

    private final HtmlSanitizer htmlSanitizer;
    private final MessageSource messageSource;
    private final LocaleResolver<HttpRequest<?>> localeResolver;
    private final Map<HtmlErrorPage, String> cache = new ConcurrentHashMap<>();

    DefaultHtmlErrorResponseBodyProvider(HtmlSanitizer htmlSanitizer,
                                         MessageSource messageSource,
                                         LocaleResolver<HttpRequest<?>> localeResolver) {
        this.htmlSanitizer = htmlSanitizer;
        this.messageSource = messageSource;
        this.localeResolver = localeResolver;
    }

    @Override
    public String body(@NonNull ErrorContext errorContext, @NonNull HttpResponse<?> response) {
        HtmlErrorPage key = error(errorContext, response);
        return cache.computeIfAbsent(key, this::html);
    }

    private String html(@NonNull HtmlErrorPage htmlErrorPage) {
        final String errorTitleCode = htmlErrorPage.httpStatusCode() + ".error.title";
        final String errorTitle = messageSource.getMessage(errorTitleCode, htmlErrorPage.httpStatusReason(), htmlErrorPage.locale());
        String header = "<h1>" + errorTitle + "</h1>";
        header += "<h2>" + htmlErrorPage.httpStatusCode() + "</h1>";
        return MessageFormat.format("<!doctype html><html lang=\"en\"><head><title>{0} — {1}</title><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"initial-scale=1, width=device-width\"><meta name=\"robots\" content=\"noindex, nofollow\"><style>{2}</style></head><body><main><header>{3}</header><article>{4}</article></main></body></html>",
                htmlErrorPage.httpStatusCode(),
                errorTitle,
                CSS,
                header,
                article(htmlErrorPage));
    }

    private HtmlErrorPage error(@NonNull ErrorContext errorContext,
                                @NonNull HttpResponse<?> response) {
        int httpStatusCode = response.code();
        Locale locale = localeResolver.resolveOrDefault(errorContext.getRequest());
        final String errorBoldCode = httpStatusCode + ".error.bold";
        final String errorCode = httpStatusCode + ".error";
        String defaultErrorBold = DEFAULT_ERROR_BOLD.get(httpStatusCode);
        String defaultError = DEFAULT_ERROR.get(httpStatusCode);
        String errorBold = defaultErrorBold != null
                ? messageSource.getMessage(errorBoldCode, defaultErrorBold, locale)
                : messageSource.getMessage(errorBoldCode, locale).orElse(null);
        String error = defaultError != null
                ? messageSource.getMessage(errorCode, defaultError, locale)
                : messageSource.getMessage(errorCode, locale).orElse(null);
        String httpStatusReason = htmlSanitizer.sanitize(response.reason());

        List<String> messages = new ArrayList<>();
        for (io.micronaut.http.server.exceptions.response.Error e : errorContext.getErrors()) {
            if (StringUtils.isNotEmpty(e.getMessage()) && !e.getMessage().equalsIgnoreCase(httpStatusReason)) {
                messages.add(htmlSanitizer.sanitize(e.getMessage()));
            }
        }
        return new HtmlErrorPage(locale, httpStatusCode, httpStatusReason, error, errorBold, messages);
    }

    private String article(@NonNull HtmlErrorPage htmlErrorPage) {
        StringBuilder sb = new StringBuilder();

        for (String message : htmlErrorPage.messages) {
            sb.append(message);
            sb.append("<br/>");
        }
        String error = htmlErrorPage.error();
        String errorBold = htmlErrorPage.errorBold();
        if (error != null || errorBold != null) {
            sb.append("<p>");
            if (errorBold != null) {
                sb.append("<strong>");
                sb.append(errorBold);
                sb.append("</strong>. ");
            }
            if (error != null) {
                sb.append(error);
                sb.append(".");
            }
            sb.append("</p>");
        }
        return sb.toString();
    }

    private record HtmlErrorPage(Locale locale,
                                 int httpStatusCode,
                                 String httpStatusReason,
                                 String error,
                                 String errorBold,
                                 List<String> messages) {
    }
}
