package io.micronaut.http.converters;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.DefaultMutableConversionService;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.ContextlessMessageBodyHandlerRegistry;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.runtime.ApplicationConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompletedFileUploadConverterTest {
    private static final byte[] DATA = "some upload content".getBytes(StandardCharsets.UTF_8);

    private static DefaultMutableConversionService conversionService() {
        DefaultMutableConversionService conversionService = new DefaultMutableConversionService();
        ContextlessMessageBodyHandlerRegistry registry = new ContextlessMessageBodyHandlerRegistry(new ApplicationConfiguration(), null);
        new HttpConverterRegistrar(() -> null, () -> registry).register(conversionService);
        return conversionService;
    }

    private static CompletedFileUpload upload(MediaType mediaType) {
        ReadBuffer buffer = ReadBufferFactory.getJdkFactory().adapt(DATA.clone());
        return CompletedFileUpload.ofMemory(new FormFieldMetadata("file", "file.bin", mediaType), buffer);
    }

    private static <T> Optional<T> convert(CompletedFileUpload upload, Argument<T> target) {
        ArgumentConversionContext<T> context = ConversionContext.of(target);
        return conversionService().convert(upload, context);
    }

    @Test
    void bytesAreCopiedOnce() throws Exception {
        CompletedFileUpload upload = upload(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        Optional<byte[]> converted = convert(upload, Argument.of(byte[].class));
        assertTrue(converted.isPresent());
        assertArrayEquals(DATA, converted.get());
        // the upload is still readable: the conversion worked on a duplicate
        assertArrayEquals(DATA, upload.getBytes());
        upload.close();
    }

    @Test
    void bytesWithoutContentType() throws Exception {
        CompletedFileUpload upload = upload(null);
        Optional<byte[]> converted = convert(upload, Argument.of(byte[].class));
        assertTrue(converted.isPresent());
        assertArrayEquals(DATA, converted.get());
        upload.close();
    }

    @Test
    void stringGoesThroughTheReader() throws Exception {
        CompletedFileUpload upload = upload(MediaType.TEXT_PLAIN_TYPE);
        Optional<String> converted = convert(upload, Argument.STRING);
        assertEquals("some upload content", converted.orElseThrow());
        upload.close();
    }

    @Test
    void inputStreamIsNotCopied() throws Exception {
        CompletedFileUpload upload = upload(MediaType.APPLICATION_OCTET_STREAM_TYPE);
        Optional<InputStream> converted = convert(upload, Argument.of(InputStream.class));
        try (InputStream is = converted.orElseThrow()) {
            assertArrayEquals(DATA, is.readAllBytes());
        }
        upload.close();
    }
}
