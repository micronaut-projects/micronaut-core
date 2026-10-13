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

import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.server.types.files.StreamedFile;
import io.micronaut.http.server.types.files.SystemFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevStaticHtmlTest {

    private static final String PAGE = "<html><body>café</body></html>";

    @TempDir
    Path directory;

    private final DevStaticHtml html = new DevStaticHtml();

    @Test
    void anHtmlFileIsReadInTheCharsetOfItsMediaType() throws IOException {
        Path page = Files.writeString(directory.resolve("index.html"), PAGE, StandardCharsets.ISO_8859_1);
        DevStaticHtml.Page read = html.read(new SystemFile(page.toFile(), MediaType.of("text/html;charset=ISO-8859-1")), HttpResponse.ok());
        assertNotNull(read);
        assertEquals(PAGE, read.html());
        assertEquals(StandardCharsets.ISO_8859_1, read.charset());
        // the name says it is a page when the media type does not
        Path upper = Files.writeString(directory.resolve("INDEX.HTM"), PAGE);
        read = html.read(new SystemFile(upper.toFile(), MediaType.APPLICATION_OCTET_STREAM_TYPE), HttpResponse.ok());
        assertNotNull(read);
        assertEquals(StandardCharsets.UTF_8, read.charset());
    }

    @Test
    void whatIsNoInlinePageIsLeftAlone() throws IOException {
        Path text = Files.writeString(directory.resolve("notes.txt"), "notes");
        assertNull(html.read(new SystemFile(text.toFile()), HttpResponse.ok()));
        Path page = Files.writeString(directory.resolve("index.html"), PAGE);
        assertNull(html.read(new SystemFile(page.toFile()).attach("index.html"), HttpResponse.ok()));
        assertNull(html.read(PAGE, HttpResponse.ok()));
        assertFalse(DevStaticHtml.isFile(PAGE));
        assertTrue(DevStaticHtml.isFile(new SystemFile(page.toFile())));
    }

    @Test
    void aStreamedPageIsKnownByItsMediaType() {
        StreamedFile streamed = new StreamedFile(new ByteArrayInputStream(PAGE.getBytes(StandardCharsets.UTF_8)), MediaType.TEXT_HTML_TYPE);
        assertTrue(DevStaticHtml.isFile(streamed));
        DevStaticHtml.Page read = html.read(streamed, HttpResponse.ok());
        assertNotNull(read);
        assertEquals(PAGE, read.html());

        assertNull(html.read(new StreamedFile(new ByteArrayInputStream(new byte[0]), MediaType.IMAGE_PNG_TYPE), HttpResponse.ok()));
        // a page that cannot be read is served as it is
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("gone");
            }
        };
        assertNull(html.read(new StreamedFile(broken, MediaType.TEXT_HTML_TYPE), HttpResponse.ok()));
    }
}
