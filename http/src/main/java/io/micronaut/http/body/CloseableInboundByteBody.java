/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.io.Closeable;

/**
 * A {@link Closeable} version of {@link InboundByteBody}. {@link #close()} releases any resources
 * that may still be held. No other operations on this body are valid after {@link #close()}, but
 * multiple calls to {@link #close()} are allowed (though only the first will do anything). If a
 * primary operation (see {@link InboundByteBody}) is performed on this body, you can but do not
 * need to close it anymore. Closing becomes a no-op in that case.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface CloseableInboundByteBody extends InboundByteBody, Closeable {
    @Override
    @NonNull
    default CloseableInboundByteBody allowDiscard() {
        return this;
    }

    /**
     * Clean up any resources held by this instance. See class documentation.
     */
    @Override
    void close();
}
