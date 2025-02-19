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
package io.micronaut.http.exceptions;

/**
 * Exception thrown when attempting to buffer more than the configured limit.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
public final class BufferLengthExceededException extends HttpException {

    private final long advertisedLength;
    private final long receivedLength;

    /**
     * @param advertisedLength The advertised length
     * @param receivedLength   The received length
     */
    public BufferLengthExceededException(long advertisedLength, long receivedLength) {
        super("""
            The content length [%d] exceeds the maximum allowed bufferable length [%d]. \
            Note that the maximum buffer size got its own configuration property (micronaut.server.max-request-buffer-size) in 4.5.0 that you may have to configure. \
            Alternatively you can rewrite your controller to stream the request instead of buffering it.""".formatted(receivedLength, advertisedLength));
        this.advertisedLength = advertisedLength;
        this.receivedLength = receivedLength;
    }

    /**
     * The maximum permitted length.
     *
     * @return The maximum permitted length
     */
    public long getAdvertisedLength() {
        return advertisedLength;
    }

    /**
     * The actual received length.
     *
     * @return The actual received length
     */
    public long getReceivedLength() {
        return receivedLength;
    }
}
