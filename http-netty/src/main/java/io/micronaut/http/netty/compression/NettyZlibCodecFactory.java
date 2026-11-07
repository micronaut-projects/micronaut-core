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
package io.micronaut.http.netty.compression;

import io.micronaut.core.annotation.Experimental;
import io.netty.handler.codec.compression.ZlibDecoder;
import io.netty.handler.codec.compression.ZlibEncoder;
import io.netty.handler.codec.compression.ZlibWrapper;

/**
 * Creates streaming gzip and deflate handlers for HTTP connections and streams.
 * Each call must return a fresh handler whose resources are released when it is removed.
 * A replacement bean applies independently of the configured channel transport.
 *
 * @since 5.3.0
 */
@Experimental
public interface NettyZlibCodecFactory {
    /**
     * Creates an encoder. {@link ZlibWrapper#ZLIB_OR_NONE} is not valid for encoding.
     *
     * @param wrapper The wrapper format
     * @param compressionLevel The compression level, from 0 to 9
     * @param windowBits The window size, from 9 to 15
     * @param memLevel The memory level, from 1 to 9
     * @return A fresh encoder
     * @since 5.3.0
     */
    ZlibEncoder newZlibEncoder(ZlibWrapper wrapper, int compressionLevel, int windowBits, int memLevel);

    /**
     * Creates a decoder, including the zlib-or-raw compatibility mode.
     *
     * @param wrapper The wrapper format
     * @param maxAllocation The maximum output buffer allocation, or 0 for no allocation limit
     * @return A fresh decoder
     * @since 5.3.0
     */
    ZlibDecoder newZlibDecoder(ZlibWrapper wrapper, int maxAllocation);
}
