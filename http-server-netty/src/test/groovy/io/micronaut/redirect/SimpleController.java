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
package io.micronaut.redirect;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

@Singleton
@Controller
public final class SimpleController implements Api {

    @Override
    public String index() {
        return "index";
    }

    @Override
    public Publisher<byte[]> echo(@Body Publisher<byte[]> foo) {
        return foo;
    }

    @Override
    public Publisher<String> echoString(@Body Publisher<String> foo) {
        return foo;
    }

    @Override
    public Mono<String> echoMono(@Body Mono<String> foo) {
        return foo;
    }

    @Override
    public Flux<String> echoFlux(@Body Flux<String> foo) {
        return foo;
    }

    @Override
    public CompletionStage<String> echoFuture(@Body CompletionStage<String> foo) {
        return foo;
    }

    @Override
    public byte[] echo(@Body byte[] foo) {
        return foo;
    }

    @Override
    public String echo(@Body String foo) {
        return foo;
    }

    @Override
    public String echoPieceJson(@Body("foo") String foo) {
        return foo;
    }
}
