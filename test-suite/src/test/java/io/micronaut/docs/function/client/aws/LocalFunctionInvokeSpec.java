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
import org.junit.Test;

import javax.inject.Named;
import static org.junit.Assert.assertEquals;
//end::import[]

//tag::rxImport[]
import io.reactivex.Single;

import java.util.Collections;
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
    public void testInvokingALocalFunctionRX() {
        Sum sum = new Sum();
        sum.setA(5);
        sum.setB(10);

        EmbeddedServer server = ApplicationContext.run(EmbeddedServer.class);
        RxMathClient mathClient = server.getApplicationContext().getBean(RxMathClient.class);

        assertEquals(Long.valueOf(Integer.MAX_VALUE), mathClient.max().blockingGet());
        assertEquals(2, mathClient.rnd(1.6f).blockingGet().longValue());
        assertEquals(15, mathClient.sum(sum).blockingGet().longValue());

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
        Single<Long> max();

        @Named("round")
        Single<Integer> rnd(float value);

        Single<Long> sum(Sum sum);
    }
    //end::rxFunctionClient[]
}
