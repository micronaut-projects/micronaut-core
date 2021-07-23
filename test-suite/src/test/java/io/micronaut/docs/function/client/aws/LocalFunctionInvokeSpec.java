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
package io.micronaut.docs.function.client.aws;

import io.micronaut.context.ApplicationContext;

//tag::import[]
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.function.client.FunctionClient;
import jakarta.inject.Named;
import org.junit.Test;
import io.micronaut.core.async.annotation.SingleResult;
import static org.junit.Assert.assertEquals;
//end::import[]

//tag::rxImport[]
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
//end::rxImport[]

public class LocalFunctionInvokeSpec {

    //tag::invokeLocalFunction[]
    @Test
    public void testInvokingALocalFunction() {
        Sum sum = new Sum();
        sum.setA(5);
        sum.setB(10);

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        MathClient mathClient = server.getApplicationContext().getBean(MathClient.class);

        assertEquals(Long.valueOf(Integer.MAX_VALUE), mathClient.max());
        assertEquals(2, mathClient.rnd(1.6f));
        assertEquals(15, mathClient.sum(sum));

        server.close();
    }
    //end::invokeLocalFunction[]

    //tag::invokeRxLocalFunction[]
    @Test
    public void testInvokingALocalFunctionReactive() {
        Sum sum = new Sum();
        sum.setA(5);
        sum.setB(10);

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        RxMathClient mathClient = server.getApplicationContext().getBean(RxMathClient.class);

        assertEquals(Long.valueOf(Integer.MAX_VALUE), Mono.from(mathClient.max()).block());
        assertEquals(2, Mono.from(mathClient.rnd(1.6f)).block().longValue());
        assertEquals(15, Mono.from(mathClient.sum(sum)).block().longValue());

        server.close();
    }
    //end::invokeRxLocalFunction[]

    //tag::beginFunctionClient[]
    @FunctionClient
    interface MathClient {
    //end::beginFunctionClient[]

        //tag::functionMax[]
        Long max(); //<1>
        //end::functionMax[]

        //tag::functionRnd[]
        @Named("round")
        int rnd(float value);
        //end::functionRnd[]

        long sum(Sum sum);
        //tag::endFunctionClient[]
    }
    //end::endFunctionClient[]


    //tag::rxFunctionClient[]
    @FunctionClient
    interface RxMathClient {
        @SingleResult
        Publisher<Long> max();

        @Named("round")
        @SingleResult
        Publisher<Integer> rnd(float value);

        @SingleResult
        Publisher<Long> sum(Sum sum);
    }
    //end::rxFunctionClient[]
}
