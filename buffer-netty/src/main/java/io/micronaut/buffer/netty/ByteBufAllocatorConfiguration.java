/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.buffer.netty;

import io.micronaut.core.annotation.Nullable;

/**
 * Interface for the Netty bytebuf allocator configuration.
 *
 * @author graemerocher
 * @since 3.3.0
 */
public interface ByteBufAllocatorConfiguration {
    String DEFAULT_ALLOCATOR = "netty.default.allocator";

    /**
     * @param numHeapArenas The number of heap arenas
     */
    void setNumHeapArenas(@Nullable Integer numHeapArenas);

    /**
     * @param numDirectArenas The number of direct arenas
     */
    void setNumDirectArenas(@Nullable Integer numDirectArenas);

    /**
     * @param pageSize The page size
     */
    void setPageSize(@Nullable Integer pageSize);

    /**
     * @param maxOrder The max order
     */
    void setMaxOrder(@Nullable Integer maxOrder);

    /**
     * @param chunkSize The chunk size
     */
    void setChunkSize(@Nullable Integer chunkSize);

    /**
     * @param smallCacheSize The small cache size
     */
    void setSmallCacheSize(@Nullable Integer smallCacheSize);

    /**
     * @param normalCacheSize The normal cache size
     */
    void setNormalCacheSize(@Nullable Integer normalCacheSize);

    /**
     * @param useCacheForAllThreads Whether to use the cache for all threads
     */
    void setUseCacheForAllThreads(@Nullable Boolean useCacheForAllThreads);

    /**
     * @param maxCachedBufferCapacity The max cached buffer capacity
     */
    void setMaxCachedBufferCapacity(@Nullable Integer maxCachedBufferCapacity);

    /**
     * @param cacheTrimInterval The cache trim interval
     */
    void setCacheTrimInterval(@Nullable Integer cacheTrimInterval);

    /**
     * @param maxCachedByteBuffersPerChunk The max cached byte buffers per chunk
     */
    void setMaxCachedByteBuffersPerChunk(@Nullable Integer maxCachedByteBuffersPerChunk);
}
