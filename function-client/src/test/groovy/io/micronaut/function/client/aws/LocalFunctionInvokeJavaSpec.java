package io.micronaut.function.client.aws;

//tag::import[]
import io.micronaut.context.ApplicationContext;
import io.micronaut.function.client.FunctionClient;
import javax.inject.Named;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
//end::rxImport[]
//end::import[]

import io.micronaut.runtime.server.EmbeddedServer;
//tag::rxImport[]
import io.reactivex.Single;

public class LocalFunctionInvokeJavaSpec {

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
