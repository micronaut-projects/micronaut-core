/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.server.netty.converters;

import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.server.netty.multipart.NettyCompletedFileUpload;
import io.netty.handler.codec.http.multipart.FileUpload;
import java.util.Optional;

/**
 * Converts a {@link FileUpload} to a {@link CompletedFileUpload} if the upload is complete.
 *
 * @author Zachary Klein
 * @since 1.0
 * @deprecated Registered by {@link NettyConverters} instead
 */
public class FileUploadToCompletedFileUploadConverter implements TypeConverter<FileUpload, CompletedFileUpload> {

    @Override
    public Optional<CompletedFileUpload> convert(FileUpload upload, Class<CompletedFileUpload> targetType, ConversionContext context) {
        try {
            if (!upload.isCompleted()) {
                return Optional.empty();
            }

            return Optional.of(new NettyCompletedFileUpload(upload));
        } catch (Exception e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
