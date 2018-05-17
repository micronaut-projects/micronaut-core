/*
 * Copyright 2018 original authors
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
package io.micronaut.function.client.aws

import io.micronaut.context.ApplicationContext
import io.micronaut.function.client.FunctionClient
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.Specification

import javax.inject.Named

/**
 * @author graemerocher
 * @since 1.0
 */
class LocalFunctionInvokeSpec extends Specification {

    void "test invoking a local function"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        MathClient mathClient = server.getApplicationContext().getBean(MathClient)

        expect:
        mathClient.max() == Integer.MAX_VALUE.toLong()
        mathClient.rnd(1.6) == 2
        mathClient.sum(new Sum(a:5,b:10)) == 15

    }

    void "test invoking a local function - rx"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        RxMathClient mathClient = server.getApplicationContext().getBean(RxMathClient)

        expect:
        mathClient.max().blockingGet() == Integer.MAX_VALUE.toLong()
        mathClient.rnd(1.6).blockingGet() == 2
        mathClient.sum(new Sum(a:5,b:10)).blockingGet() == 15

    }

    @FunctionClient
    static interface MathClient {
        Long max()

        @Named("round")
        int rnd(float value)

        long sum(Sum sum)
    }

    @FunctionClient
    static interface RxMathClient {
        Single<Long> max()

        @Named("round")
        Single<Integer> rnd(float value)

        Single<Long> sum(Sum sum)
    }
}
