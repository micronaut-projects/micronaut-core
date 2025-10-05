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

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

@Singleton
@Controller
public interface Api {
    String ECHO_PUBLISHER = "/echo-publisher";
    String ECHO_MONO = "/echo-mono";
    String ECHO_FLUX = "/echo-flux";
    String ECHO_FUTURE = "/echo-future";
    String ECHO_STRING_PUBLISHER = "/echo-string-publisher";
    String ECHO_ARRAY = "/echo-array";
    String ECHO_STRING = "/echo-string";
    String ECHO_PIECE_JSON = "/echo-piece-json";

    @Get
    String index();

    @SingleResult
    @Post(ECHO_PUBLISHER)
    Publisher<byte[]> echo(@Body Publisher<byte[]> foo);

    @SingleResult
    @Post(ECHO_STRING_PUBLISHER)
    Publisher<String> echoString(@Body Publisher<String> foo);

    @Post(ECHO_MONO)
    Mono<String> echoMono(@Body Mono<String> foo);

    @SingleResult
    @Post(ECHO_FLUX)
    Flux<String> echoFlux(@Body Flux<String> foo);

    @Post(ECHO_FUTURE)
    CompletionStage<String> echoFuture(@Body CompletionStage<String> foo);

    @Post(ECHO_ARRAY)
    byte[] echo(@Body byte[] foo);

    @Post(ECHO_STRING)
    String echo(@Body String foo);

    @Post(ECHO_PIECE_JSON)
    @Consumes({
        MediaType.APPLICATION_JSON,
        MediaType.APPLICATION_FORM_URLENCODED
    })
    String echoPieceJson(@Body("foo") String foo);
}
