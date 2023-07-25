/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.json.convert;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.json.JsonSyntaxException;
import io.micronaut.json.tree.JsonNode;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lazily parsed {@link JsonNode}.
 */
@Internal
public final class LazyJsonNode implements ReferenceCounted {
    private final Lock lock = new ReentrantLock();
    @Nullable
    private ByteBuffer<?> buffer;
    private int refCnt = 1;
    @Nullable
    @SuppressWarnings("java:S3077")
    private volatile JsonNode asNode;
    @Nullable
    private JsonSyntaxException syntaxException;

    public LazyJsonNode(@NonNull ByteBuffer<?> buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
    }

    /**
     * Parse this JSON to the given type.
     *
     * @param mapper The mapper to use for parsing
     * @param type   The target type
     * @param <T>    The target type
     * @return The parsed value
     * @throws IOException A {@link JsonSyntaxException} or framework data binding exception
     */
    public <T> T parse(JsonMapper mapper, Argument<T> type) throws IOException {
        lock.lock();
        try {
            if (asNode == null) {
                try {
                    return mapper.readValue(buffer(), type);
                } catch (JsonSyntaxException se) {
                    this.syntaxException = se;
                    discardBuffer();
                    throw se;
                }
            } else {
                return mapper.readValueFromTree(asNode, type);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Check whether this node is an object.
     *
     * @return {@code true} if this node is an object
     * @throws JsonSyntaxException If the JSON is malformed. Note that this method does not always
     *                             do full parsing, so this exception is best-effort only
     */
    boolean isObject() throws JsonSyntaxException {
        JsonNode n = asNode;
        if (n != null) {
            return n.isObject();
        }

        lock.lock();
        try {
            n = asNode;
            if (n != null) {
                return n.isObject();
            }
            if (syntaxException != null) {
                throw syntaxException;
            }

            ByteBuffer<?> buf = buffer();
            if (buf.readableBytes() == 0) {
                return false;
            }
            byte b = buf.getByte(buf.readerIndex());
            if (b == ' ' || b == '\t' || b == '\n' || b == '\r' || b == (byte) 0xef) {
                // this should have been handled by the JsonCounter
                throw new IllegalStateException("JSON input is not properly trimmed");
            }
            return b == '{';
        } finally {
            lock.unlock();
        }
    }

    /**
     * Parse this JSON to a {@link JsonNode}.
     *
     * @param mapper The JSON mapper to use for parsing
     * @return The parsed JSON node
     * @throws IOException A {@link JsonSyntaxException} or framework data binding exception
     */
    JsonNode toJsonNode(JsonMapper mapper) throws IOException {
        if (asNode == null) {
            lock.lock();
            try {
                if (asNode == null) {
                    if (syntaxException != null) {
                        throw syntaxException;
                    }

                    asNode = parse(mapper, Argument.of(JsonNode.class));
                }
                discardBuffer();
            } finally {
                lock.unlock();
            }
        }
        return asNode;
    }

    @Override
    public LazyJsonNode retain() {
        lock.lock();
        try {
            if (refCnt == 0) {
                throw new IllegalStateException("Already released");
            }
            refCnt++;
        } finally {
            lock.unlock();
        }
        return this;
    }

    private ByteBuffer<?> buffer() {
        ByteBuffer<?> b = buffer;
        if (b == null) {
            throw new IllegalStateException("Buffer not available anymore");
        }
        return b;
    }

    @Override
    public boolean release() {
        lock.lock();
        try {
            if (refCnt == 0) {
                throw new IllegalStateException("Already released");
            }
            refCnt--;
            if (refCnt == 0) {
                discardBuffer();
                return true;
            } else {
                return false;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Try to release this node if it hasn't been released already.
     */
    @Internal
    public void tryRelease() {
        // this is a bit yikes but it's necessary so we can attempt conversion twice.
        // it seems to work fine because the first conversion is to JsonNode, which we store
        // locally.
        lock.lock();
        try {
            if (refCnt != 0) {
                release();
            }
        } finally {
            lock.unlock();
        }
    }

    private void discardBuffer() {
        // implicit null check here
        if (buffer instanceof ReferenceCounted rc) {
            rc.release();
        }
        buffer = null;
    }
}
