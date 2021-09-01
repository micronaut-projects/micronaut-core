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
package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.Internal;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpPostMultipartRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.ReferenceCountUtil;

import java.nio.charset.Charset;

/**
 * Customized multipart decoder for custom destroy behavior.
 *
 * @author James Kleeh
 * @since 2.5.5
 */
@Internal
class MicronautHttpPostMultipartRequestDecoder extends HttpPostMultipartRequestDecoder {

    MicronautHttpPostMultipartRequestDecoder(HttpDataFactory factory, HttpRequest request, Charset charset) {
        super(factory, request, charset);
    }

    @Override
    public void destroy() {
        /*
            Prevent them from being released as they will
            be released after they are read or when the request
            terminates
         */
        getBodyHttpDatas().clear();
        super.destroy();
        // release any data partially uploaded but not completed
        final InterfaceHttpData data = currentPartialHttpData();
        if (data != null && data.refCnt() != 0) {
            ReferenceCountUtil.safeRelease(data);
        }
    }
}
