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
package io.micronaut.docs.server.reactive;

import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Controller("/reactive")
public class ReactiveController {

    @Get("/reactor-mono")
    public Mono<String> mono() {
        return Mono.just("Hello world");
    }

    @Get("/reactor-flux")
    public Flux<String> flux() {
        return Flux.just("Hello world");
    }

    @SingleResult
    @Get("/reactor-flux-single")
    public Flux<String> fluxSingle() {
        return Flux.just("Hello world");
    }

    @Get("/rxjava3-single")
    public Single<String> rxJava3Single() {
        return Single.just("Hello world");
    }

    @Get("/rxjava3-maybe")
    public Maybe<String> rxJava3Maybe() {
        return Maybe.just("Hello world");
    }

    @Get("/rxjava3-flowable")
    public Flowable<String> rxJava3Flowable() {
        return Flowable.just("Hello world");
    }

    @SingleResult
    @Get("/rxjava3-flowable-single")
    public Flowable<String> rxJava3FlowableSingle() {
        return Flowable.just("Hello world");
    }

    @Get("/rxjava3-flowable-completable")
    public Completable rxJava3FlowableCompletable() {
        return Completable.complete();
    }

}
