/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.cache;

import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.Toggleable;
import io.micronaut.runtime.ApplicationConfiguration;

import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * <p>A base configuration class for configuring caches.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class CacheConfiguration implements Toggleable {

    /**
     * The prefix for cache configuration.
     */
    public static final String PREFIX = "micronaut.caches";

    /**
     * The default record stats value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_RECORD_STATS = false;

    /**
     * The default test mode value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_TESTMODE = false;

    /**
     * The default enabled value.
     */
    public static final boolean DEFAULT_ENABLED = true;

    protected Charset charset;

    private Integer initialCapacity;
    private Long maximumSize;
    private Long maximumWeight;
    private Duration expireAfterWrite;
    private Duration expireAfterAccess;
    private boolean recordStats = DEFAULT_RECORD_STATS;
    private boolean testMode = DEFAULT_TESTMODE;
    private boolean enabled = DEFAULT_ENABLED;
    private final String cacheName;

    /**
     * Creates a new cache with the given name.
     *
     * @param cacheName Name or key of the cache
     * @param applicationConfiguration The common application configuration
     */
    public CacheConfiguration(@Parameter String cacheName, ApplicationConfiguration applicationConfiguration) {
        this.cacheName = cacheName;
        this.charset = applicationConfiguration.getDefaultCharset();
    }

    /**
     * @return The name of the cache
     */
    public String getCacheName() {
        return cacheName;
    }

    /**
     * @return The initial capacity of the cache
     */
    public OptionalInt getInitialCapacity() {
        return initialCapacity == null ? OptionalInt.empty() : OptionalInt.of(initialCapacity);
    }

    /**
     * @return The maximum size of the cache
     */
    public OptionalLong getMaximumSize() {
        return maximumSize == null ? OptionalLong.empty() : OptionalLong.of(maximumSize);
    }

    /**
     * @return The maximum weight of cache entries
     */
    public OptionalLong getMaximumWeight() {
        return maximumWeight == null ? OptionalLong.empty() : OptionalLong.of(maximumWeight);
    }

    /**
     * @return The expiry to use after the value is written
     */
    public Optional<Duration> getExpireAfterWrite() {
        return Optional.ofNullable(expireAfterWrite);
    }

    /**
     * Specifies that each entry should be automatically removed from the cache once a fixed duration
     * has elapsed after the entry's creation, the most recent replacement of its value, or its last
     * read.
     *
     * @return The {@link Duration}
     */
    public Optional<Duration> getExpireAfterAccess() {
        return Optional.ofNullable(expireAfterAccess);
    }

    /**
     * Some caches support recording statistics. For example to record hit and miss ratio's fine tune the cache characteristics.
     *
     * @return True if record stats is enabled
     */
    public boolean isRecordStats() {
        return recordStats;
    }

    /**
     * @return The charset used to serialize and deserialize values
     */
    public Charset getCharset() {
        return charset;
    }

    /**
     *
     * @param initialCapacity The initial cache capacity.
     */
    public void setInitialCapacity(Integer initialCapacity) {
        this.initialCapacity = initialCapacity;
    }

    /**
     *
     * @param maximumSize Specifies the maximum number of entries the cache may contain
     */
    public void setMaximumSize(Long maximumSize) {
        this.maximumSize = maximumSize;
    }

    /**
     *
     * @param maximumWeight Specifies the maximum weight of entries
     */
    public void setMaximumWeight(Long maximumWeight) {
        this.maximumWeight = maximumWeight;
    }

    /**
     *
     * @param expireAfterWrite The cache expiration duration after writing into it.
     */
    public void setExpireAfterWrite(Duration expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    /**
     * @param expireAfterAccess The cache expiration duration after accessing it
     */
    public void setExpireAfterAccess(Duration expireAfterAccess) {
        this.expireAfterAccess = expireAfterAccess;
    }

    /**
     * Set whether record stats is enabled. Default value ({@value #DEFAULT_RECORD_STATS}).
     *
     * @param recordStats True if record status is enabled
     */
    public void setRecordStats(final boolean recordStats) {
        this.recordStats = recordStats;
    }

    /**
     * @param charset The charset used to serialize and deserialize values
     */
    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    /**
     * Some caches have a test mode. For example to to enable the eager execution of cleanup operations making it
     * easier to test.
     *
     * @return True if it test mode is enabled
     */
    public boolean isTestMode() {
        return testMode;
    }

    /**
     * Set whether test mode is enabled. Default value ({@value #DEFAULT_TESTMODE}).
     *
     * @param testMode True if test mode is eanbled
     */
    public void setTestMode(boolean testMode) {
        this.testMode = testMode;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param enabled Whether the cache is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
