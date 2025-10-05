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
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletionStage;

@Singleton
@Controller(RedirectingController.REDIRECT)
public final class RedirectingController implements Api {
    static final String REDIRECT = "/redirect";
    static final String REDIRECT_PUBLISHER = REDIRECT + Api.ECHO_PUBLISHER;
    static final String REDIRECT_MONO = REDIRECT + Api.ECHO_MONO;
    static final String REDIRECT_FLUX = REDIRECT + Api.ECHO_FLUX;
    static final String REDIRECT_FUTURE = REDIRECT + Api.ECHO_FUTURE;
    static final String REDIRECT_STRING_PUBLISHER = REDIRECT + Api.ECHO_STRING_PUBLISHER;
    static final String REDIRECT_ARRAY = REDIRECT + Api.ECHO_ARRAY;
    static final String REDIRECT_STRING = REDIRECT + Api.ECHO_STRING;
    static final String REDIRECT_PIECE_JSON = REDIRECT + Api.ECHO_PIECE_JSON;

    private final HttpClientApi clientApi;

    public RedirectingController(HttpClientApi clientApi) {
        this.clientApi = clientApi;
    }

    public String index() {
        return clientApi.index();
    }

    public Publisher<byte[]> echo(@Body Publisher<byte[]> foo) {
        return clientApi.echo(foo);
    }

    public Publisher<String> echoString(@Body Publisher<String> foo) {
        return clientApi.echoString(foo);
    }

    public Mono<String> echoMono(@Body Mono<String> foo) {
        return clientApi.echoMono(foo);
    }

    public Flux<String> echoFlux(@Body Flux<String> foo) {
        return clientApi.echoFlux(foo);
    }

    public CompletionStage<String> echoFuture(@Body CompletionStage<String> foo) {
        return clientApi.echoFuture(foo);
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    public byte[] echo(@Body byte[] foo) {
        return clientApi.echo(foo);
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    public String echo(@Body String foo) {
        return clientApi.echo(foo);
    }

    @ExecuteOn(TaskExecutors.BLOCKING)
    public String echoPieceJson(@Body("foo") String foo) {
        if (ServerRequestContext.currentRequest().get().getContentType().get().matchesExtension("json")) {
            return clientApi.echoPieceJson("{\"foo\": \"" + foo + "\"}");
        }
        return clientApi.echoPieceJson("foo=" + foo);
    }
}
