package io.micronaut.docs.function.client.aws

import io.micronaut.context.ApplicationContext

//tag::import[]
import io.micronaut.function.client.FunctionClient
import javax.inject.Named
//end::import[]

import io.micronaut.runtime.server.EmbeddedServer
//tag::rxImport[]
import io.reactivex.Single
//end::rxImport[]
import spock.lang.Specification

class LocalFunctionInvokeSpec extends Specification {

    //tag::invokeLocalFunction[]
    void "test invoking a local function"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        MathClient mathClient = server.getApplicationContext().getBean(MathClient)

        expect:
        mathClient.max() == Integer.MAX_VALUE.toLong()
        mathClient.rnd(1.6) == 2
        mathClient.sum(new Sum(a:5,b:10)) == 15

        cleanup:
        server.close()

    }
    //end::invokeLocalFunction[]

    //tag::invokeRxLocalFunction[]
    void "test invoking a local function - rx"() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer)
        RxMathClient mathClient = server.getApplicationContext().getBean(RxMathClient)

        expect:
        mathClient.max().blockingGet() == Integer.MAX_VALUE.toLong()
        mathClient.rnd(1.6).blockingGet() == 2
        mathClient.sum(new Sum(a:5,b:10)).blockingGet() == 15

        cleanup:
        server.close()

    }
    //end::invokeRxLocalFunction[]

    //tag::beginFunctionClient[]
    @FunctionClient
    static interface MathClient {
    //end::beginFunctionClient[]

        //tag::functionMax[]
        Long max() //<1>
        //end::functionMax[]

        //tag::functionRnd[]
        @Named("round")
        int rnd(float value)
        //end::functionRnd[]

        long sum(Sum sum)
        //tag::endFunctionClient[]
    }
    //end::endFunctionClient[]


    //tag::rxFunctionClient[]
    @FunctionClient
    static interface RxMathClient {
        Single<Long> max()

        @Named("round")
        Single<Integer> rnd(float value)

        Single<Long> sum(Sum sum)
    }
    //end::rxFunctionClient[]
}
