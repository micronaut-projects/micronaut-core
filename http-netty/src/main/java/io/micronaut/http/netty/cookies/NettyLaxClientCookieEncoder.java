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
import io.micronaut.http.cookie.ClientCookieEncoder;
import io.micronaut.http.cookie.Cookie;

import static io.netty.handler.codec.http.cookie.ClientCookieEncoder.LAX;

/**
 * {@link ClientCookieEncoder} implementation backed on Netty's {@link io.netty.handler.codec.http.cookie.ClientCookieEncoder#LAX}.
 * @author Sergio del Amo
 * @since 4.3.0
 */
@Internal
public final class NettyLaxClientCookieEncoder implements ClientCookieEncoder {

    @Override
    public String encode(Cookie cookie) {
        if (cookie instanceof NettyCookie nettyCookie) {
            return LAX.encode(nettyCookie.getNettyCookie());
        }
        return LAX.encode(new NettyCookie(cookie).getNettyCookie());
    }
}
