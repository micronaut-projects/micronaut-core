/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.reactive.converters

import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import rx.Observable
import rx.Single
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactiveTypeConversionSpec extends Specification {

    @Unroll
    void 'test converting reactive type #from to #target'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        ConversionService.SHARED.convert(from, target).isPresent()

        cleanup:
        applicationContext?.close()

        where:
        from                        | target
        Single.just(1)              | Flowable
        Single.just(1)              | io.reactivex.Single
        Single.just(1)                  | Mono
        Single.just(1)                  | Flux
        Observable.just(1)              | io.reactivex.Observable
        Observable.just(1)              | Flowable
        Observable.just(1)              | Mono
        Observable.just(1)              | Flux
        Flux.just(1)                    | Single
        Mono.just(1)                    | Single
        Flux.just(1)                    | Observable
        Mono.just(1)                    | Observable
        io.reactivex.Single.just(1)     | Single
        io.reactivex.Observable.just(1) | Single
        Flux.just(1)                    | Observable
        Mono.just(1)                    | Observable
    }
}
