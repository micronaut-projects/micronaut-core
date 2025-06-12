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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.codec.quic.QuicTokenHandler;
import io.netty.util.concurrent.FastThreadLocal;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import java.net.InetSocketAddress;
import java.security.Key;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Secure {@link QuicTokenHandler} implementation based on a MAC.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
class QuicTokenHandlerImpl implements QuicTokenHandler {
    private static final int MAC_LENGTH = 256 / 8;
    private static final int MAX_CONNECTION_ID_LENGTH = 20;
    /**
     * Timestamp will be modulo'd by this window size, and included in the MAC. In verification, we
     * check the last two windows, so a given token expires in roughly this time frame.
     */
    private static final long TIMESTAMP_WINDOW_SIZE = 5 * 60 * 1000;

    private final Key key;
    /**
     * Making this non-static is not ideal, but it allows initializing the mac with the key only
     * once.
     */
    private final FastThreadLocal<Mac> macCache = new FastThreadLocal<>() {
        @Override
        protected Mac initialValue() throws Exception {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac;
        }
    };

    private final ByteBufAllocator alloc;

    QuicTokenHandlerImpl(ByteBufAllocator alloc) {
        this.alloc = alloc;
        try {
            key = KeyGenerator.getInstance("HmacSHA256").generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Alias for {@link #QuicTokenHandlerImpl}, avoids a {@link NoClassDefFoundError} when quic is
     * missing from the classpath.
     */
    static QuicTokenHandler create(ByteBufAllocator alloc) {
        return new QuicTokenHandlerImpl(alloc);
    }

    /**
     * Write the validation token. The output buffer contains first the token, then the
     * {@code dcid}, the same format that is passed into {@link #validateToken}.
     *
     * @param out       {@link ByteBuf} into which the token will be written.
     * @param dcid      the destination connection id.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return {@code true}
     */
    @Override
    public boolean writeToken(ByteBuf out, ByteBuf dcid, InetSocketAddress address) {
        byte[] hash = hash(address, dcid, currentWindowId());
        out.writeBytes(hash);
        out.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
        return true;
    }

    /**
     * Verify the validation token. The input buffer is user-controlled, but should contain the
     * same format returned by {@link #writeToken} (first token, then dcid). This method extracts
     * the dcid from the input, computes the MAC, and validates it against the token.
     *
     * @param token     the {@link ByteBuf} that contains the token. The ownership is not transferred.
     * @param address   the {@link InetSocketAddress} of the sender.
     * @return The offset of the dcid in the input buffer. This is used by netty. -1 on validation
     * failure.
     */
    @Override
    public int validateToken(ByteBuf token, InetSocketAddress address) {
        byte[] actual = new byte[MAC_LENGTH];
        token.getBytes(token.readerIndex(), actual);
        ByteBuf dcid = token.slice(token.readerIndex() + MAC_LENGTH, token.readableBytes() - MAC_LENGTH);

        long windowId = currentWindowId();
        byte[] expectedHashNow = hash(address, dcid, windowId);
        byte[] expectedHashPrev = hash(address, dcid, windowId - 1);
        // constant-time comparison
        boolean equalNow = MessageDigest.isEqual(expectedHashNow, actual);
        boolean equalPrev = MessageDigest.isEqual(expectedHashPrev, actual);
        if (equalNow | equalPrev) {
            return MAC_LENGTH;
        } else {
            return -1;
        }
    }

    private byte[] hash(InetSocketAddress address, ByteBuf dcid, long windowId) {
        ByteBuf textToVerify = buildTextToVerify(address, dcid, windowId);
        byte[] hash;
        try {
            Mac mac = macCache.get();
            mac.update(textToVerify.array(), textToVerify.arrayOffset() + textToVerify.readerIndex(), textToVerify.readableBytes());
            hash = mac.doFinal();
        } finally {
            textToVerify.release();
        }
        assert hash.length == MAC_LENGTH;
        return hash;
    }

    private ByteBuf buildTextToVerify(InetSocketAddress address, ByteBuf dcid, long windowId) {
        if (dcid.readableBytes() > MAX_CONNECTION_ID_LENGTH) {
            throw new IllegalArgumentException("Connection ID may not exceed 20 bytes length");
        }
        ByteBuf textToVerify = alloc.heapBuffer();
        byte[] addressBytes = address.getAddress().getAddress();
        textToVerify.writeByte(addressBytes.length);
        textToVerify.writeBytes(addressBytes);
        textToVerify.writeShort(address.getPort());
        textToVerify.writeByte(dcid.readableBytes());
        textToVerify.writeBytes(dcid, dcid.readerIndex(), dcid.readableBytes());
        textToVerify.writeLong(windowId);
        return textToVerify;
    }

    @Override
    public int maxTokenLength() {
        return MAC_LENGTH + MAX_CONNECTION_ID_LENGTH;
    }

    // overridden in test
    long currentWindowId() {
        return System.currentTimeMillis() / TIMESTAMP_WINDOW_SIZE;
    }
}
