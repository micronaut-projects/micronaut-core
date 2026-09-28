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
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.server.types.files.FileCustomizableResponseType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * Reads the HTML of a file the static resource resolver answers with, a {@link SystemFile} or a
 * {@link StreamedFile}, so that the LiveReload script can be injected into it. Only in development
 * mode, where reading a static page into memory costs nothing that matters.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(classes = SystemFile.class)
@Requires(condition = DevelopmentMode.Active.class)
@Requires(beans = DevRuntime.class)
final class DevStaticHtml {

    /**
     * The HTML of a file body, when the file is an HTML page served inline.
     *
     * @param body The response body
     * @param response The response, which the file populates with its headers first
     * @return The page, or null when the body is not an HTML page or is an attachment
     */
    @Nullable
    Page read(Object body, MutableHttpResponse<?> response) {
        if (!(body instanceof FileCustomizableResponseType file)) {
            return null;
        }
        // the file sets its headers when written; set now, they tell whether it is an attachment to download,
        // which must stay the file it is, and the writer setting them again later is harmless
        file.process(response);
        if (response.getHeaders().contains(HttpHeaders.CONTENT_DISPOSITION)) {
            return null;
        }
        try {
            if (body instanceof SystemFile systemFile) {
                if (!isHtml(systemFile.getFile().getName(), systemFile.getMediaType())) {
                    return null;
                }
                Charset charset = charsetOf(systemFile.getMediaType());
                return new Page(Files.readString(systemFile.getFile().toPath(), charset), charset);
            }
            if (body instanceof StreamedFile streamedFile) {
                // a streamed file names no file: the media type the resolver derived from the extension says what it is
                if (!isHtml(null, streamedFile.getMediaType())) {
                    return null;
                }
                Charset charset = charsetOf(streamedFile.getMediaType());
                try (InputStream in = streamedFile.getInputStream()) {
                    return new Page(new String(in.readAllBytes(), charset), charset);
                }
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static Charset charsetOf(@Nullable MediaType mediaType) {
        // the file's own media type is the only word on its encoding: the response has no content type yet
        return mediaType != null ? mediaType.getCharset().orElse(StandardCharsets.UTF_8) : StandardCharsets.UTF_8;
    }

    private static boolean isHtml(@Nullable String name, @Nullable MediaType mediaType) {
        if (mediaType != null && MediaType.TEXT_HTML.equals(mediaType.getName())) {
            return true;
        }
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".html") || lower.endsWith(".htm");
    }

    /**
     * @param body The response body
     * @return Whether the body is a file the resolver answers with
     */
    static boolean isFile(Object body) {
        return body instanceof SystemFile || body instanceof StreamedFile;
    }

    /**
     * A page read from a file, with the charset it was read with.
     *
     * @param html The page
     * @param charset The charset
     */
    record Page(String html, Charset charset) {
    }
}
