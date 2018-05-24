/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.configuration.lettuce.cache;

import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.dynamic.RedisCommandFactory;
import io.micronaut.cache.AsyncCache;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.serialize.DefaultStringKeySerializer;
import io.micronaut.configuration.lettuce.RedisConnectionUtil;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.serialize.JdkSerializer;
import io.micronaut.core.serialize.ObjectSerializer;
import io.micronaut.core.type.Argument;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * An implementation of {@link SyncCache} for Lettuce / Redis.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@EachBean(RedisCacheConfiguration.class)
public class RedisCache implements SyncCache<StatefulConnection<?, ?>> {
    private final RedisCacheConfiguration redisCacheConfiguration;
    private final ObjectSerializer keySerializer;
    private final ObjectSerializer valueSerializer;
    private final Long expireAfterWrite;
    private final Long expireAfterAccess;
    private final RedisAsyncCache asyncCache;
    private final SyncCacheCommands commands;
    private final StatefulConnection<String, String> connection;

    /**
     * Creates a new redis cache for the given arguments.
     *
     * @param redisCacheConfiguration The configuration
     * @param conversionService       The conversion service
     * @param beanLocator             The bean locator used to discover the redis connection from the configuration
     */
    @SuppressWarnings("unchecked")
    public RedisCache(
        RedisCacheConfiguration redisCacheConfiguration,
        ConversionService<?> conversionService,
        BeanLocator beanLocator) {
        if (redisCacheConfiguration == null) {
            throw new IllegalArgumentException("Redis cache configuration cannot be null");
        }
        this.redisCacheConfiguration = redisCacheConfiguration;
        this.expireAfterWrite = redisCacheConfiguration.getExpireAfterWrite().map(Duration::toMillis).orElse(null);
        this.expireAfterAccess = redisCacheConfiguration.getExpireAfterAccess().map(Duration::toMillis).orElse(null);
        this.keySerializer = redisCacheConfiguration
            .getKeySerializer()
            .flatMap(beanLocator::findOrInstantiateBean)
            .orElse(newDefaultKeySerializer(redisCacheConfiguration, conversionService));

        this.valueSerializer = redisCacheConfiguration
            .getValueSerializer()
            .flatMap(beanLocator::findOrInstantiateBean)
            .orElse(new JdkSerializer(conversionService));

        Optional<String> server = redisCacheConfiguration.getServer();
        this.connection = RedisConnectionUtil.findRedisConnection(beanLocator, server, "No Redis server configured to allow caching");
        this.commands = syncCommands(this.connection);
        this.asyncCache = new RedisAsyncCache();
    }

    @Override
    public String getName() {
        return redisCacheConfiguration.getCacheName();
    }

    @Override
    public StatefulConnection<?, ?> getNativeCache() {
        return connection;
    }

    @Override
    public <T> Optional<T> get(Object key, Argument<T> requiredType) {
        byte[] serializedKey = serializeKey(key);
        return getValue(requiredType, commands, serializedKey);
    }

    @Override
    public <T> T get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
        byte[] serializedKey = serializeKey(key);
        byte[] data = commands.get(serializedKey);
        if (data != null) {
            Optional<T> deserialized = valueSerializer.deserialize(data, requiredType.getType());
            if (deserialized.isPresent()) {
                return deserialized.get();
            }
        }

        T value = supplier.get();
        putValue(commands, serializedKey, value);
        return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Optional<T> putIfAbsent(Object key, T value) {
        if (value == null) {
            return Optional.empty();
        }

        byte[] serializedKey = serializeKey(key);
        Optional<T> existing = getValue(Argument.of((Class<T>) value.getClass()), commands, serializedKey);
        if (!existing.isPresent()) {
            putValue(commands, serializedKey, value);
            return Optional.empty();
        } else {
            return existing;
        }
    }

    @Override
    public void put(Object key, Object value) {
        byte[] serializedKey = serializeKey(key);
        putValue(commands, serializedKey, value);
    }

    @Override
    public void invalidate(Object key) {
        byte[] serializedKey = serializeKey(key);
        commands.remove(serializedKey);
    }

    @Override
    public void invalidateAll() {
        List<byte[]> keys = commands.keys(getKeysPattern().getBytes(redisCacheConfiguration.getCharset()));
        commands.del(keys.toArray(new byte[keys.size()][]));
    }

    @Override
    public AsyncCache<StatefulConnection<?, ?>> async() {
        return asyncCache;
    }

    /**
     * Get the value based on the parameters.
     * @param requiredType requiredType
     * @param commands commands
     * @param serializedKey serializedKey
     * @param <T> type of the argument
     * @return value
     */
    protected <T> Optional<T> getValue(Argument<T> requiredType, SyncCacheCommands commands, byte[] serializedKey) {
        byte[] data = commands.get(serializedKey);
        if (expireAfterAccess != null) {
            commands.expire(serializedKey, expireAfterAccess);
        }
        if (data != null) {
            return valueSerializer.deserialize(data, requiredType.getType());
        } else {

            return Optional.empty();
        }
    }

    /**
     * @return The default keys pattern.
     */
    protected String getKeysPattern() {
        return getName() + ":*";
    }

    /**
     * Place the value in the cache.
     * @param commands commands
     * @param serializedKey serializedKey
     * @param value value
     * @param <T> type of the value
     */
    protected <T> void putValue(SyncCacheCommands commands, byte[] serializedKey, T value) {
        Optional<byte[]> serialized = valueSerializer.serialize(value);
        if (serialized.isPresent()) {
            byte[] bytes = serialized.get();
            if (expireAfterWrite != null) {
                commands.put(serializedKey, bytes, expireAfterWrite);
            } else {
                commands.put(serializedKey, bytes);
            }
        } else {
            commands.remove(serializedKey);
        }
    }

    /**
     * Serialize the key.
     * @param key The key
     * @return bytes of the object
     */
    protected byte[] serializeKey(Object key) {
        return keySerializer.serialize(key).orElseThrow(() -> new IllegalArgumentException("Key cannot be null"));
    }

    /**
     * Get the synchronous commands for the stateful connection.
     * @param connection stateful connection
     * @return commands
     */
    protected SyncCacheCommands syncCommands(StatefulConnection<String, String> connection) {
        RedisCommandFactory redisCommandFactory = new RedisCommandFactory(connection);
        return redisCommandFactory.getCommands(SyncCacheCommands.class);
    }

    /**
     * Get the asynchronous commands for the stateful connection.
     * @param connection stateful connection
     * @return commands
     */
    protected AsyncCacheCommands asyncCommands(StatefulConnection<String, String> connection) {
        RedisCommandFactory redisCommandFactory = new RedisCommandFactory(connection);
        return redisCommandFactory.getCommands(AsyncCacheCommands.class);
    }

    private DefaultStringKeySerializer newDefaultKeySerializer(RedisCacheConfiguration redisCacheConfiguration, ConversionService<?> conversionService) {
        return new DefaultStringKeySerializer(redisCacheConfiguration.getCacheName(), redisCacheConfiguration.getCharset(), conversionService);
    }

    /**
     * Redis Async cache implementation.
     */
    protected class RedisAsyncCache implements AsyncCache<StatefulConnection<?, ?>> {

        private final AsyncCacheCommands async = asyncCommands(connection);

        @Override
        public <T> CompletableFuture<Optional<T>> get(Object key, Argument<T> requiredType) {
            CompletableFuture<Optional<T>> result = new CompletableFuture<>();
            byte[] serializedKey = serializeKey(key);
            async.get(serializedKey).whenComplete((data, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    if (data != null) {
                        completeGet(requiredType, result, async, serializedKey, data);
                    } else {
                        result.complete(Optional.empty());
                    }
                }
            });
            return result;
        }

        @Override
        public <T> CompletableFuture<T> get(Object key, Argument<T> requiredType, Supplier<T> supplier) {
            CompletableFuture<T> result = new CompletableFuture<>();
            byte[] serializedKey = serializeKey(key);
            async.get(serializedKey).whenComplete((data, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    if (data != null) {
                        Optional<T> deserialized = valueSerializer.deserialize(data, requiredType.getType());
                        boolean hasValue = deserialized.isPresent();
                        if (expireAfterAccess != null && hasValue) {
                            async.expire(serializedKey, expireAfterAccess).whenComplete((s, throwable1) -> {
                                if (throwable1 != null) {
                                    result.completeExceptionally(throwable1);
                                } else {
                                    result.complete(deserialized.get());
                                }
                            });
                        } else {
                            if (hasValue) {
                                result.complete(deserialized.get());
                            } else {
                                invokeSupplier(serializedKey, supplier, async, result);
                            }
                        }
                    } else {
                        invokeSupplier(serializedKey, supplier, async, result);
                    }
                }
            });
            return result;
        }

        @Override
        public <T> CompletableFuture<Optional<T>> putIfAbsent(Object key, T value) {
            CompletableFuture<Optional<T>> result = new CompletableFuture<>();
            byte[] serializedKey = serializeKey(key);
            async.get(serializedKey).whenComplete((data, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    if (data != null) {
                        completeGet(Argument.of((Class<T>) value.getClass()), result, async, serializedKey, data);
                    } else {
                        Optional<byte[]> serialized = valueSerializer.serialize(value);
                        if (serialized.isPresent()) {
                            RedisFuture<String> putOperation = newPutOperation(async, serializedKey, serialized.get());
                            putOperation.whenComplete((s, throwable12) -> {
                                if (throwable12 != null) {
                                    result.completeExceptionally(throwable12);
                                } else {
                                    result.complete(Optional.empty());
                                }
                            });
                        }
                    }
                }
            });
            return result;
        }

        @Override
        public CompletableFuture<Boolean> put(Object key, Object value) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            BiConsumer<String, Throwable> booleanConsumer = (s, throwable) -> {
                if (throwable == null) {
                    result.complete(true);
                } else {
                    result.completeExceptionally(throwable);
                }
            };
            byte[] serializedKey = serializeKey(key);
            Optional<byte[]> serialized = valueSerializer.serialize(value);
            if (serialized.isPresent()) {
                RedisFuture<String> future = newPutOperation(async, serializedKey, serialized.get());
                future.whenComplete(booleanConsumer);
            } else {
                async.remove(serializedKey).whenComplete((aLong, throwable) -> {
                    if (throwable == null) {
                        result.complete(true);
                    } else {
                        result.completeExceptionally(throwable);
                    }
                });
            }
            return result;
        }

        @Override
        public CompletableFuture<Boolean> invalidate(Object key) {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            async.remove(serializeKey(key)).whenComplete((status, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    result.complete(true);
                }
            });
            return result;
        }

        @Override
        public CompletableFuture<Boolean> invalidateAll() {
            CompletableFuture<Boolean> result = new CompletableFuture<>();
            async.keys(getKeysPattern().getBytes(redisCacheConfiguration.getCharset())).whenComplete((keys, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(throwable);
                } else {
                    async.del(keys.toArray(new byte[keys.size()][])).whenComplete((deleteCount, throwable1) -> {
                        if (throwable1 != null) {
                            result.completeExceptionally(throwable1);
                        } else {
                            result.complete(true);
                        }
                    });
                }
            });
            return result;
        }

        @Override
        public String getName() {
            return RedisCache.this.getName();
        }

        @Override
        public StatefulConnection<?, ?> getNativeCache() {
            return RedisCache.this.getNativeCache();
        }

        private <T> void completeGet(Argument<T> requiredType, CompletableFuture<Optional<T>> result, AsyncCacheCommands async, byte[] serializedKey, byte[] data) {
            Optional<T> deserialized = valueSerializer.deserialize(data, requiredType.getType());
            if (expireAfterAccess != null && deserialized.isPresent()) {
                async.expire(serializedKey, expireAfterAccess).whenComplete((s, throwable1) -> {
                    if (throwable1 != null) {
                        result.completeExceptionally(throwable1);
                    } else {
                        result.complete(deserialized);
                    }
                });
            } else {
                result.complete(deserialized);
            }
        }

        private <T> void invokeSupplier(byte[] serializedKey, Supplier<T> supplier, AsyncCacheCommands async, CompletableFuture<T> result) {
            T value = null;
            boolean hasSupplierError = false;
            try {
                value = supplier.get();
            } catch (Exception e) {
                hasSupplierError = true;
                result.completeExceptionally(e);
            }
            if (!hasSupplierError) {

                Optional<byte[]> serialized = valueSerializer.serialize(value);
                if (serialized.isPresent()) {
                    RedisFuture<String> future = newPutOperation(async, serializedKey, serialized.get());
                    T finalValue = value;
                    future.whenComplete((s, throwable12) -> {
                        if (throwable12 != null) {
                            result.completeExceptionally(throwable12);
                        } else {
                            result.complete(finalValue);
                        }
                    });
                } else {
                    result.complete(null);
                }
            }
        }

        private RedisFuture newPutOperation(AsyncCacheCommands async, byte[] serializedKey, byte[] serialized) {
            RedisFuture future;
            if (expireAfterWrite != null) {
                future = async.put(serializedKey, serialized, expireAfterWrite);
            } else {
                future = async.put(serializedKey, serialized);
            }
            return future;
        }

    }
}
