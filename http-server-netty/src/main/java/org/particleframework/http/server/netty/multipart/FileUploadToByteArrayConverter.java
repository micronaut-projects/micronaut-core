/*
 * Copyright 2017 original authors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License. 
 */
package org.particleframework.http.server.netty.multipart;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.util.ReferenceCountUtil;
import org.particleframework.core.convert.ConversionContext;
import org.particleframework.core.convert.TypeConverter;
import org.particleframework.http.server.netty.multipart.ChunkedFileUpload;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.Optional;

/**
 * A converter that converts a {@link FileUpload} to a byte array
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class FileUploadToByteArrayConverter implements TypeConverter<ChunkedFileUpload, byte[]> {
    @Override
    public Optional<byte[]> convert(ChunkedFileUpload upload, Class<byte[]> targetType, ConversionContext context) {
        try {
            ByteBuf byteBuf = null;
            try {
                byteBuf = upload.getCurrentChunk();
                if(byteBuf.hasArray() && upload.isCompleted()) {
                    return Optional.of(byteBuf.array());
                }
                else {
                    int len = byteBuf.readableBytes();
                    byte[] bytes = new byte[len];
                    byteBuf.readBytes(bytes);
                    return Optional.of(bytes);
                }
            } finally {
                ReferenceCountUtil.release(byteBuf);
            }

        } catch (IOException e) {
            context.reject(e);
            return Optional.empty();
        }
    }
}
