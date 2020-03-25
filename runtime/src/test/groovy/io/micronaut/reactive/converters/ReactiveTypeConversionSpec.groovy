/*
 * Copyright 2017-2019 original authors
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

import io.reactivex.Completable
import io.reactivex.Flowable
import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import io.reactivex.Maybe
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author graemerocher
 * @since 1.0
 */
class ReactiveTypeConversionSpec extends Specification {

    @Unroll
    void 'test converting reactive type #from.getClass().getSimpleName() to #target'() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run()

        expect:
        ConversionService.SHARED.convert(from, target).isPresent()

        cleanup:
        applicationContext?.close()

        where:
        from                            | target
        Completable.complete()          | io.reactivex.Observable
        Completable.complete()          | Flowable
        Completable.complete()          | Mono
        Completable.complete()          | Flux
        Completable.complete()          | io.reactivex.Single
        Completable.complete()          | Maybe
    }
}
