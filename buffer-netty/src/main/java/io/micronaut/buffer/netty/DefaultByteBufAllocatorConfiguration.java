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

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;

/**
 * Allows configuring the default netty allocator. Note that Netty initializes the default
 * allocator once from system properties, so once this is loaded it cannot be altered again
 * for the lifecycle of the JVM.
 *
 * @see io.netty.buffer.ByteBufAllocator
 * @author graemerocher
 * @since 3.3.0
 */
@ConfigurationProperties(ByteBufAllocatorConfiguration.DEFAULT_ALLOCATOR)
@Requires(property = ByteBufAllocatorConfiguration.DEFAULT_ALLOCATOR)
@Context
@BootstrapContextCompatible
@Internal
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class DefaultByteBufAllocatorConfiguration implements ByteBufAllocatorConfiguration {

    private static final String PROP_PREFIX = "io.netty.allocator.";

    DefaultByteBufAllocatorConfiguration() {
    }

    /**
     * @param numHeapArenas The number of heap arenas
     */
    @Override
    public void setNumHeapArenas(@Nullable Integer numHeapArenas) {
        if (numHeapArenas != null) {
            System.setProperty(PROP_PREFIX + "numHeapArenas", numHeapArenas.toString());
        }
    }

    /**
     * @param numDirectArenas The number of direct arenas
     */
    @Override
    public void setNumDirectArenas(@Nullable Integer numDirectArenas) {
        if (numDirectArenas != null) {
            System.setProperty(PROP_PREFIX + "numDirectArenas", numDirectArenas.toString());
        }
    }

    /**
     * @param pageSize The page size
     */
    @Override
    public void setPageSize(@Nullable Integer pageSize) {
        if (pageSize != null) {
            System.setProperty(PROP_PREFIX + "pageSize", pageSize.toString());
        }
    }

    /**
     * @param maxOrder The max order
     */
    @Override
    public void setMaxOrder(@Nullable Integer maxOrder) {
        if (maxOrder != null) {
            System.setProperty(PROP_PREFIX + "maxOrder", maxOrder.toString());
        }
    }

    /**
     * @param chunkSize The chunk size
     */
    @Override
    public void setChunkSize(@Nullable Integer chunkSize) {
        if (chunkSize != null) {
            System.setProperty(PROP_PREFIX + "chunkSize", chunkSize.toString());
        }
    }

    /**
     * @param smallCacheSize The small cache size
     */
    @Override
    public void setSmallCacheSize(@Nullable Integer smallCacheSize) {
        if (smallCacheSize != null) {
            System.setProperty(PROP_PREFIX + "smallCacheSize", smallCacheSize.toString());
        }
    }

    /**
     * @param normalCacheSize The normal cache size
     */
    @Override
    public void setNormalCacheSize(@Nullable Integer normalCacheSize) {
        if (normalCacheSize != null) {
            System.setProperty(PROP_PREFIX + "normalCacheSize", normalCacheSize.toString());
        }
    }

    /**
     * @param useCacheForAllThreads Whether to use the cache for all threads
     */
    @Override
    public void setUseCacheForAllThreads(@Nullable Boolean useCacheForAllThreads) {
        if (useCacheForAllThreads != null) {
            System.setProperty(PROP_PREFIX + "useCacheForAllThreads", useCacheForAllThreads.toString());
        }
    }

    /**
     * @param maxCachedBufferCapacity The max cached buffer capacity
     */
    @Override
    public void setMaxCachedBufferCapacity(@Nullable Integer maxCachedBufferCapacity) {
        if (maxCachedBufferCapacity != null) {
            System.setProperty(PROP_PREFIX + "maxCachedBufferCapacity", maxCachedBufferCapacity.toString());
        }
    }

    /**
     * @param cacheTrimInterval The cache trim interval
     */
    @Override
    public void setCacheTrimInterval(@Nullable Integer cacheTrimInterval) {
        if (cacheTrimInterval != null) {
            System.setProperty(PROP_PREFIX + "cacheTrimInterval", cacheTrimInterval.toString());
        }
    }

    /**
     * @param maxCachedByteBuffersPerChunk The max cached byte buffers per chunk
     */
    @Override
    public void setMaxCachedByteBuffersPerChunk(@Nullable Integer maxCachedByteBuffersPerChunk) {
        if (maxCachedByteBuffersPerChunk != null) {
            System.setProperty(PROP_PREFIX + "maxCachedByteBuffersPerChunk", maxCachedByteBuffersPerChunk.toString());
        }
    }
}
