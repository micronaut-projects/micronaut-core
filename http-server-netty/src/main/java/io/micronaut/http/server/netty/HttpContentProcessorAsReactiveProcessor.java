/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.netty.stream.StreamedHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for transforming a {@link NettyHttpRequest} using a {@link HttpContentProcessor}
 * to a {@link Publisher}.<br>
 * Note: A more complicated, but possibly faster, implementation of this class is archived in
 * <a href="https://github.com/micronaut-projects/micronaut-core/pull/8463">the original PR</a>.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
public final class HttpContentProcessorAsReactiveProcessor {
    private HttpContentProcessorAsReactiveProcessor() {
    }

    /**
     * Subscribe to the {@link StreamedHttpMessage} in the given request, and return a
     * {@link Publisher} that will produce the processed items.<br>
     * This exists mostly for compatibility with the old {@link HttpContentProcessor}, which was a
     * {@link org.reactivestreams.Processor}.
     *
     * @param processor The content processor to use
     * @param streamed The request to subscribe to
     * @return The publisher producing output data
     * @param <T> The output element type
     */
    @NonNull
    public static <T> Flux<T> asPublisher(HttpContentProcessor processor, Publisher<HttpContent> streamed) {
        return Flux.concat(Flux.from(streamed)
            .doOnError(e -> {
                try {
                    processor.cancel();
                } catch (Throwable ex) {
                    e.addSuppressed(ex);
                }
            })
            .concatMap(c -> {
                try {
                    List<T> out = new ArrayList<>(1);
                    processor.add(c, (List) out);
                    return Flux.fromIterable(out);
                } catch (Throwable e) {
                    c.touch();
                    return Flux.error(e);
                }
            }), Flux.defer(() -> {
            try {
                List<T> out = new ArrayList<>(1);
                processor.complete((List) out);
                return Flux.fromIterable(out);
            } catch (Throwable ex) {
                return Flux.error(ex);
            }
        }));
    }
}
