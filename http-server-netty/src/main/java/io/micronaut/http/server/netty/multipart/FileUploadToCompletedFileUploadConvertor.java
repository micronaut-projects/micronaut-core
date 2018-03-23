package io.micronaut.http.server.netty.multipart;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.netty.handler.codec.http.multipart.FileUpload;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * @author Zachary Klein
 * @since 1.0
 */
@Singleton
public class FileUploadToCompletedFileUploadConvertor implements TypeConverter<FileUpload, CompletedFileUpload> {

    @Override
    public Optional<CompletedFileUpload> convert(FileUpload upload, Class<CompletedFileUpload> targetType, ConversionContext context) {
        try {
            if (!upload.isCompleted()) {
                return Optional.empty();
            }

            return Optional.of(new CompletedFileUpload(upload));
        } catch (Exception e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
