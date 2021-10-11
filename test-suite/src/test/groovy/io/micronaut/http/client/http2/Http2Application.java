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
package io.micronaut.http.client.http2;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.runtime.Micronaut;
import io.micronaut.runtime.server.EmbeddedServer;

public class Http2Application {

    public static void main(String[] args) {
        Micronaut
                .build(args)
                .properties(CollectionUtils.mapOf(
                        "micronaut.server.http-version" , "2.0",
                        "micronaut.server.netty.log-level" , "TRACE"
                ))
                .run(EmbeddedServer.class);
    }
}
