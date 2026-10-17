/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.http.body.ByteBody;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.util.concurrent.DefaultPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The interleavings of a registration with the final write of the response, driven step by
 * step against a connection whose state the test controls.
 */
class ResponseCallbacksRaceTest {

    @Test
    void aRegistrationAfterAnUnrecordedFinalWriteIsRefused() {
        // the final write was issued before the registration could have it recorded
        FakeAccess access = new FakeAccess();
        access.finalWriteIssued = true;
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        assertFalse(callbacks.whenCompleted(t -> { }));
        callbacks.cleanedUp();
    }

    @Test
    void aRegistrationBeforeTheFinalWriteWaitsForIt() {
        FakeAccess access = new FakeAccess();
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        AtomicReference<Optional<Throwable>> outcome = new AtomicReference<>();
        assertTrue(callbacks.whenCompleted(t -> outcome.set(Optional.ofNullable(t))));
        assertTrue(access.record);
        // the final write is issued, recorded, and the request cleaned up before it completes
        Promise<Void> write = access.issueFinalWrite();
        callbacks.cleanedUp();
        assertNull(outcome.get());
        IOException failure = new IOException("write failed");
        write.setFailure(failure);
        assertSame(failure, outcome.get().orElseThrow());
    }

    @Test
    void abandonmentCallbacksStayUntilTheFinalWriteCompletes() {
        FakeAccess access = new FakeAccess();
        ResponseCallbacks callbacks = new ResponseCallbacks(access, null);
        List<String> runs = new ArrayList<>();
        callbacks.whenAbandoned(() -> runs.add("abandoned"));
        Promise<Void> write = access.issueFinalWrite();
        callbacks.cleanedUp();
        assertEquals(1, access.abandonCallbacks.size(), "still registered while the write is in progress");
        access.abandonCallbacks.forEach(Runnable::run);
        assertEquals(List.of("abandoned"), runs);
        write.setSuccess(null);
        assertTrue(access.abandonCallbacks.isEmpty(), "removed once the write completed");
    }

    private static final class FakeAccess implements OutboundAccess {
        final List<Runnable> abandonCallbacks = new ArrayList<>();
        volatile boolean finalWriteIssued;
        volatile boolean record;
        @Nullable Promise<Void> finalWrite;

        Promise<Void> issueFinalWrite() {
            finalWriteIssued = true;
            Promise<Void> promise = new DefaultPromise<>(ImmediateEventExecutor.INSTANCE);
            if (record) {
                finalWrite = promise;
            }
            return promise;
        }

        @Override
        public boolean recordFinalWrite() {
            record = true;
            return !finalWriteIssued;
        }

        @Override
        public @Nullable Future<?> finalWrite() {
            return finalWrite;
        }

        @Override
        public void attachment(Object attachment) {
        }

        @Override
        public void closeAfterWrite() {
        }

        @Override
        public void write(HttpResponse response, ByteBody body) {
        }

        @Override
        public void abort() {
        }

        @Override
        public Runnable whenAbandoned(Runnable callback) {
            abandonCallbacks.add(callback);
            return () -> abandonCallbacks.remove(callback);
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
        }
    }
}
