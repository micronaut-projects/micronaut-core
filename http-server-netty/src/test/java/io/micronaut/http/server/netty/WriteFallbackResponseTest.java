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
import io.netty.handler.codec.http.HttpResponseStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The fallback response after writing the response failed.
 */
class WriteFallbackResponseTest {

    @Test
    void theFallbackIsAnEmpty500ThatClosesTheConnection() {
        RecordingAccess access = new RecordingAccess(null);
        Throwable failure = new OutOfMemoryError("write");

        RoutingInBoundHandler.writeFallbackResponse(access, failure);

        assertEquals(List.of("closeAfterWrite", "write 500"), access.calls);
        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    void aRefusedFallbackIsKeptAsSuppressedByTheWriteFailure() {
        IllegalStateException refused = new IllegalStateException("Response already written");
        RecordingAccess access = new RecordingAccess(refused);
        Throwable failure = new OutOfMemoryError("write");

        RoutingInBoundHandler.writeFallbackResponse(access, failure);

        assertEquals(List.of("closeAfterWrite", "write 500"), access.calls);
        assertArrayEquals(new Throwable[]{refused}, failure.getSuppressed());
        assertSame(refused, failure.getSuppressed()[0]);
    }

    private static final class RecordingAccess implements OutboundAccess {
        final List<String> calls = new ArrayList<>();
        private final RuntimeException writeFailure;

        RecordingAccess(RuntimeException writeFailure) {
            this.writeFailure = writeFailure;
        }

        @Override
        public void attachment(Object attachment) {
            calls.add("attachment");
        }

        @Override
        public void closeAfterWrite() {
            calls.add("closeAfterWrite");
        }

        @Override
        public void write(HttpResponse response, ByteBody body) {
            calls.add("write " + response.status().code());
            assertEquals(HttpResponseStatus.INTERNAL_SERVER_ERROR, response.status());
            if (writeFailure != null) {
                throw writeFailure;
            }
        }

        @Override
        public void writeHeadResponse(HttpResponse response) {
            calls.add("writeHeadResponse");
        }
    }
}
