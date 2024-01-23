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
package io.micronaut.http.netty.cookies;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.ServerCookieDecoder;

import java.util.List;
import static io.netty.handler.codec.http.cookie.ServerCookieDecoder.LAX;

/**
 * @author Sergio del Amo
 * @since 4.3.0
 */
@Internal
public class NettyLaxServerCookieDecoder implements ServerCookieDecoder {
    @Override
    @NonNull
    public List<Cookie> decode(@NonNull String headerValue) {
        return LAX.decode(headerValue)
                .stream()
                .map(nettyCookie -> (Cookie) new NettyCookie(nettyCookie))
                .toList();
    }
}
