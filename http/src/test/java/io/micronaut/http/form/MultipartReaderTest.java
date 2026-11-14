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
