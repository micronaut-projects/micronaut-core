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
package io.micronaut.http.netty.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.stream.BodyPublishers;
import io.micronaut.http.netty.content.HttpContentUtil;
import io.netty.handler.codec.http.HttpContent;
import org.reactivestreams.Publisher;

import java.util.function.Function;

/**
 * Frames the pieces of JSON content as the elements of a JSON array: an opening bracket in front
 * of the first piece, a comma in front of the others, and a closing bracket after the last one.
 */
@Internal
public final class JsonSubscriber {
    public static Publisher<HttpContent> lift(Publisher<HttpContent> publisher) {
        HttpContent closeBracket = HttpContentUtil.closeBracket();
        return BodyPublishers.map(BodyPublishers.append(publisher, closeBracket, HttpContent::release), new Function<>() {
            boolean empty = true;

            @Override
            public HttpContent apply(HttpContent httpContent) {
                if (empty) {
                    empty = false;
                    return HttpContentUtil.prefixOpenBracket(httpContent);
                } else if (httpContent != closeBracket) {
                    return HttpContentUtil.prefixComma(httpContent);
                } else {
                    return httpContent;
                }
            }
        });
    }
}
