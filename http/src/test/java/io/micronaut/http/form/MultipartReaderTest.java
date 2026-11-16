package io.micronaut.http.form;

import io.micronaut.http.MediaType;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartReaderTest {

    private static final String BODY = "preamble\r\n"
        + "--XyZ\r\n"
        + "Content-Disposition: form-data; name=\"field\"\r\n"
        + "\r\n"
        + "value\r\n"
        + "--XyZ  \r\n"
        + "Content-Disposition: form-data; name=\"file\"; filename=\"a;b \\\"c\\\".txt\"\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "line1 --XyZ\r\n-XyZ\r\nline2\r\n"
        + "--XyZ\r\n"
        + "Content-Disposition: form-data; name=\"intl\"; filename=\"x\"; filename*=UTF-8''%C3%A9t%C3%A9.txt\r\n"
        + "\r\n"
        + "\r\n"
        + "--XyZ--\r\n"
        + "epilogue";

    @ParameterizedTest
    @ValueSource(ints = {1, 3, 7, 8192})
    void readParts(int chunk) throws IOException {
        try (MultipartReader reader = MultipartReader.of(chunked(BODY, chunk), MediaType.of("multipart/form-data; boundary=XyZ"), MultipartReader.Limits.DEFAULT)) {
            MultipartReader.Part field = reader.next();
            assertEquals("field", field.name());
            assertNull(field.fileName());
            assertEquals("value", text(field));

            MultipartReader.Part file = reader.next();
            assertEquals("file", file.name());
            assertEquals("a;b \"c\".txt", file.fileName());
            assertEquals(MediaType.TEXT_PLAIN_TYPE, file.contentType().orElseThrow());
            assertEquals("line1 --XyZ\r\n-XyZ\r\nline2", text(file));

            MultipartReader.Part intl = reader.next();
            assertEquals("été.txt", intl.fileName());
            assertEquals("", text(intl));

            assertNull(reader.next());
            assertNull(reader.next());
        }
    }

    @Test
    void nextSkipsUnreadBody() throws IOException {
        MultipartReader reader = MultipartReader.of(stream(BODY), "XyZ", MultipartReader.Limits.UNLIMITED);
        reader.next();
        reader.next().body().read();
        assertEquals("intl", reader.next().name());
        assertNull(reader.next());
    }

    @Test
    void bodyWithoutPreamble() throws IOException {
        MultipartReader reader = MultipartReader.of(stream("--b\r\n\r\nx\r\n--b--"), "b", MultipartReader.Limits.DEFAULT);
        MultipartReader.Part part = reader.next();
        assertNull(part.name());
        assertEquals("x", text(part));
        assertNull(reader.next());
    }

    @Test
    void largePart() throws IOException {
        byte[] data = new byte[100_000];
        Arrays.fill(data, (byte) '\r');
        String body = "--b\r\n\r\n" + new String(data, StandardCharsets.ISO_8859_1) + "\r\n--b--";
        MultipartReader reader = MultipartReader.of(chunked(body, 1000), "b", MultipartReader.Limits.UNLIMITED);
        assertArrayEquals(data, reader.next().body().readAllBytes());
        assertNull(reader.next());
    }

    @Test
    void maxParts() {
        MultipartReader reader = MultipartReader.of(stream(BODY), "XyZ", new MultipartReader.Limits(2, -1, -1, -1));
        assertThrows(ContentLengthExceededException.class, () -> {
            while (reader.next() != null) {
                // read all
            }
        });
    }

    @Test
    void maxPartSize() throws IOException {
        MultipartReader reader = MultipartReader.of(stream(BODY), "XyZ", new MultipartReader.Limits(-1, -1, 10, -1));
        reader.next();
        MultipartReader.Part file = reader.next();
        assertThrows(ContentLengthExceededException.class, () -> file.body().readAllBytes());
    }

    @Test
    void maxPartSizeAppliesToSkippedBodies() throws IOException {
        MultipartReader reader = MultipartReader.of(stream(BODY), "XyZ", new MultipartReader.Limits(-1, -1, 10, -1));
        reader.next();
        reader.next();
        assertThrows(ContentLengthExceededException.class, reader::next);
    }

    @Test
    void maxHeaderSize() {
        MultipartReader reader = MultipartReader.of(stream(BODY), "XyZ", new MultipartReader.Limits(-1, 20, -1, -1));
        assertThrows(ContentLengthExceededException.class, reader::next);
    }

    @Test
    void maxTotalSize() {
        MultipartReader reader = MultipartReader.of(chunked(BODY, 16), "XyZ", new MultipartReader.Limits(-1, -1, -1, 64));
        assertThrows(ContentLengthExceededException.class, () -> {
            MultipartReader.Part part;
            while ((part = reader.next()) != null) {
                part.body().readAllBytes();
            }
        });
    }

    @Test
    void truncatedBody() throws IOException {
        MultipartReader reader = MultipartReader.of(stream("--b\r\n\r\nunterminated"), "b", MultipartReader.Limits.DEFAULT);
        MultipartReader.Part part = reader.next();
        assertThrows(IOException.class, () -> part.body().readAllBytes());
        assertThrows(IOException.class, () -> MultipartReader.of(stream("no boundary"), "b", MultipartReader.Limits.DEFAULT).next());
    }

    @Test
    void invalidContentType() {
        assertThrows(IllegalArgumentException.class, () -> MultipartReader.of(stream(""), MediaType.TEXT_PLAIN_TYPE, MultipartReader.Limits.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> MultipartReader.of(stream(""), MediaType.MULTIPART_FORM_DATA_TYPE, MultipartReader.Limits.DEFAULT));
        assertTrue(MultipartReader.Limits.UNLIMITED.maxParts() < 0);
    }

    @Test
    void headerLineExactlyFillingTheLimitRejectsTheNextLine() throws IOException {
        String header = "Content-Disposition: form-data; name=\"a\"\r\n";
        int max = header.getBytes(StandardCharsets.UTF_8).length;
        String body = "--b\r\n" + header + "\r\nv\r\n--b--";
        try (MultipartReader reader = MultipartReader.of(stream(body), "b", new MultipartReader.Limits(10, max, 100, 1000))) {
            assertThrows(ContentLengthExceededException.class, reader::next);
        }
        try (MultipartReader reader = MultipartReader.of(stream(body), "b", new MultipartReader.Limits(10, max + 2, 100, 1000))) {
            assertEquals("a", reader.next().name());
        }
    }

    @Test
    void headerSizeCountsBytes() throws IOException {
        String header = "Content-Disposition: form-data; name=\"\u0436\u0436\"\r\n";
        int chars = header.length();
        String body = "--b\r\n" + header + "\r\nv\r\n--b--";
        try (MultipartReader reader = MultipartReader.of(stream(body), "b", new MultipartReader.Limits(10, chars + 2, 100, 1000))) {
            assertThrows(ContentLengthExceededException.class, reader::next);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bad Name", "Bad\"Name", "Bad\u00e9", "Bad\u0001"})
    void invalidHeaderName(String name) {
        String body = "--b\r\n" + name + ": x\r\n\r\nv\r\n--b--";
        try (MultipartReader reader = MultipartReader.of(stream(body), "b", MultipartReader.Limits.DEFAULT)) {
            IOException e = assertThrows(IOException.class, reader::next);
            assertTrue(e.getMessage().startsWith("Invalid multipart header name"));
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u0444\u0430\u0439\u043b.txt", "\u6587\u4ef6.txt"})
    void rawUtf8FileName(String fileName) throws IOException {
        String body = "--b\r\nContent-Disposition: form-data; name=\"f\"; filename=\"" + fileName + "\"\r\nX-Note: " + fileName + "\r\n\r\nv\r\n--b--";
        try (MultipartReader reader = MultipartReader.of(stream(body), "b", MultipartReader.Limits.DEFAULT)) {
            MultipartReader.Part part = reader.next();
            assertEquals(fileName, part.fileName());
            assertEquals(fileName, part.headers().get("x-note"));
            assertEquals("v", text(part));
        }
    }

    @Test
    void readByteAtATime() throws IOException {
        String content = "x".repeat(100_000);
        String body = "--b\r\n\r\n" + content + "\r\n--b--";
        try (MultipartReader reader = MultipartReader.of(stream(body), "b", MultipartReader.Limits.UNLIMITED)) {
            InputStream in = reader.next().body();
            int count = 0;
            while (in.read() >= 0) {
                count++;
            }
            assertEquals(content.length(), count);
            assertNull(reader.next());
        }
    }

    private static String text(MultipartReader.Part part) throws IOException {
        return new String(part.body().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }

    private static InputStream chunked(String s, int chunk) {
        return new FilterInputStream(stream(s)) {
            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return super.read(b, off, Math.min(len, chunk));
            }
        };
    }
}
