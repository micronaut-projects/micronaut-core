package io.micronaut.http.server.binding;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.io.file.TemporaryFileResource;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.form.FileUpload;
import io.micronaut.http.form.FormPart;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The charset of the text reads of form content: the charset of the part, else the charset of
 * the request, unless the caller gives one.
 */
class UploadContentCharsetTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final String TEXT = "Grüße, çà";
    private static final MediaType LATIN_1 = MediaType.of("text/plain; charset=ISO-8859-1");
    private static final MediaType UTF_16 = MediaType.of("text/plain; charset=UTF-16");

    @TempDir
    Path directory;

    private static UploadContext context(Charset requestCharset) {
        return new UploadContext(Runnable::run, BODY_FACTORY, requestCharset, 1024, Long.MAX_VALUE);
    }

    private static FormFieldMetadata file(@Nullable MediaType contentType) {
        return new FormFieldMetadata("file", "file.txt", contentType);
    }

    private static FormFieldMetadata field(@Nullable MediaType contentType) {
        return new FormFieldMetadata("title", null, contentType);
    }

    private static FileUpload memory(FormFieldMetadata metadata, byte[] content, Charset requestCharset) {
        return new DefaultFileUpload(new StoredUploadContent(
            CompletedFileUpload.ofMemory(metadata, ReadBufferFactory.getJdkFactory().adapt(content)), context(requestCharset)));
    }

    private FileUpload disk(FormFieldMetadata metadata, byte[] content, Charset requestCharset) throws IOException {
        Path file = Files.createTempFile(directory, "stored", ".tmp");
        Files.write(file, content);
        CompletedFileUpload upload = CompletedFileUpload.ofFile(metadata, new TemporaryFileResource(file), content.length);
        return new DefaultFileUpload(new StoredUploadContent(upload, context(requestCharset)));
    }

    private static FormPart streaming(FormFieldMetadata metadata, byte[] content, Charset requestCharset) {
        return new DefaultFormPart(new StreamingUploadContent(new RawFormField(metadata, BODY_FACTORY.adapt(content)), context(requestCharset)));
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    @Test
    void theCharsetOfThePartIsTheDefault() throws IOException {
        byte[] latin1 = TEXT.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(TEXT, join(memory(file(LATIN_1), latin1, StandardCharsets.UTF_8).text(100)));
        assertEquals(TEXT, join(disk(file(LATIN_1), latin1, StandardCharsets.UTF_8).text(100)));
        FormPart part = streaming(file(LATIN_1), latin1, StandardCharsets.UTF_8);
        assertEquals(TEXT, join(part.text()));
        assertEquals(TEXT, join(streaming(file(LATIN_1), latin1, StandardCharsets.UTF_8).text(100)));
        assertEquals(TEXT, join(streaming(file(LATIN_1), latin1, StandardCharsets.UTF_8).file().text(100)));
        // a text field with a content type too
        assertEquals(TEXT, join(streaming(field(LATIN_1), latin1, StandardCharsets.UTF_8).text()));
        // a quoted charset
        assertEquals(TEXT, join(streaming(field(MediaType.of("text/plain; charset=\"ISO-8859-1\"")), latin1, StandardCharsets.UTF_8).text()));
    }

    @Test
    void withoutACharsetThePartIsInTheCharsetOfTheRequest() throws IOException {
        byte[] latin1 = TEXT.getBytes(StandardCharsets.ISO_8859_1);
        byte[] utf8 = TEXT.getBytes(StandardCharsets.UTF_8);
        // a content type without a charset
        assertEquals(TEXT, join(memory(file(MediaType.TEXT_PLAIN_TYPE), latin1, StandardCharsets.ISO_8859_1).text(100)));
        assertEquals(TEXT, join(disk(file(MediaType.TEXT_PLAIN_TYPE), latin1, StandardCharsets.ISO_8859_1).text(100)));
        assertEquals(TEXT, join(streaming(file(MediaType.TEXT_PLAIN_TYPE), latin1, StandardCharsets.ISO_8859_1).text()));
        // no content type: a text field
        assertEquals(TEXT, join(streaming(field(null), latin1, StandardCharsets.ISO_8859_1).text()));
        // the charset of the request is the default charset when the request names none
        assertEquals(TEXT, join(streaming(field(null), utf8, StandardCharsets.UTF_8).text(100)));
        assertEquals(TEXT, join(memory(file(MediaType.APPLICATION_OCTET_STREAM_TYPE), utf8, StandardCharsets.UTF_8).text(100)));
    }

    @Test
    void anUnsupportedCharsetOfThePartIsIgnored() {
        byte[] latin1 = TEXT.getBytes(StandardCharsets.ISO_8859_1);
        MediaType unsupported = MediaType.of("text/plain; charset=x-no-such-charset");
        assertEquals(TEXT, join(memory(file(unsupported), latin1, StandardCharsets.ISO_8859_1).text(100)));
        assertEquals(TEXT, join(streaming(field(MediaType.of("text/plain; charset=\"not a charset\"")), latin1, StandardCharsets.ISO_8859_1).text()));
    }

    @Test
    void anExplicitCharsetWins() throws IOException {
        byte[] utf16 = TEXT.getBytes(StandardCharsets.UTF_16);
        // over the charset of the part
        assertEquals(TEXT, join(memory(file(LATIN_1), utf16, StandardCharsets.UTF_8).text(100, StandardCharsets.UTF_16)));
        assertEquals(TEXT, join(disk(file(LATIN_1), utf16, StandardCharsets.UTF_8).text(100, StandardCharsets.UTF_16)));
        assertEquals(TEXT, join(streaming(file(LATIN_1), utf16, StandardCharsets.UTF_8).text(100, StandardCharsets.UTF_16)));
        assertEquals(TEXT, join(streaming(file(LATIN_1), utf16, StandardCharsets.UTF_8).file().text(100, StandardCharsets.UTF_16)));
        // over the charset of the request
        assertEquals(TEXT, join(streaming(field(null), utf16, StandardCharsets.UTF_8).text(100, StandardCharsets.UTF_16)));
        byte[] latin1 = TEXT.getBytes(StandardCharsets.ISO_8859_1);
        assertEquals(TEXT, join(memory(file(UTF_16), latin1, StandardCharsets.UTF_8).text(100, StandardCharsets.ISO_8859_1)));
    }

    @Test
    void theExplicitCharsetIsCheckedBeforeTheContentIsConsumed() {
        byte[] utf8 = TEXT.getBytes(StandardCharsets.UTF_8);
        FileUpload upload = memory(file(null), utf8, StandardCharsets.UTF_8);
        assertThrows(NullPointerException.class, () -> upload.text(100, null));
        assertThrows(IllegalArgumentException.class, () -> upload.text(-1, StandardCharsets.UTF_8));
        assertEquals(TEXT, join(upload.text(100, StandardCharsets.UTF_8)));
        assertThrows(IllegalStateException.class, () -> upload.text(100, StandardCharsets.UTF_8));

        FormPart part = streaming(field(null), utf8, StandardCharsets.UTF_8);
        assertThrows(NullPointerException.class, () -> part.text(100, null));
        assertEquals(TEXT, join(part.text(100, StandardCharsets.UTF_8)));
    }

    @Test
    void theWholeRequestBodyIsInTheCharsetOfTheRequest() {
        byte[] latin1 = TEXT.getBytes(StandardCharsets.ISO_8859_1);
        UploadContent body = StreamingUploadContent.requestBody(BODY_FACTORY.adapt(latin1), LATIN_1, context(StandardCharsets.ISO_8859_1));
        assertEquals(TEXT, join(body.text(100)));
        byte[] utf16 = TEXT.getBytes(StandardCharsets.UTF_16);
        UploadContent overridden = StreamingUploadContent.requestBody(BODY_FACTORY.adapt(utf16), LATIN_1, context(StandardCharsets.ISO_8859_1));
        assertEquals(TEXT, join(overridden.text(100, StandardCharsets.UTF_16)));
    }
}
