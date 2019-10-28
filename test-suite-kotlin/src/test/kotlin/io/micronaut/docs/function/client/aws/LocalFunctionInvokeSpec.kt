package io.micronaut.docs.function.client.aws

//tag::import[]
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.function.client.FunctionClient
import io.micronaut.http.client.RxHttpClient
import javax.inject.Named
//end::import[]

//tag::rxImport[]
import io.reactivex.Single

//end::rxImport[]

class LocalFunctionInvokeSpec: StringSpec() {

    val embeddedServer = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java)
    )

    val client = autoClose(
            embeddedServer.applicationContext.createBean(RxHttpClient::class.java, embeddedServer.url)
    )

    init {
        //tag::invokeLocalFunction[]
        "test invoking a local function" {
            val sum = Sum()
            sum.a = 5
            sum.b = 10

            val mathClient = embeddedServer.applicationContext.getBean(MathClient::class.java)

            mathClient.max() shouldBe Integer.MAX_VALUE.toLong()
            mathClient.rnd(1.6f).toLong() shouldBe 2
            mathClient.sum(sum) shouldBe 15
        }
        //end::invokeLocalFunction[]

        //tag::invokeRxLocalFunction[]
        "test invoking a local function - rx" {
            val sum = Sum()
            sum.a = 5
            sum.b = 10

            val mathClient = embeddedServer.applicationContext.getBean(RxMathClient::class.java)

            mathClient.max().blockingGet() shouldBe Integer.MAX_VALUE.toLong()
            mathClient.rnd(1.6f).blockingGet().toLong() shouldBe 2
            mathClient.sum(sum).blockingGet() shouldBe 15
        }
        //end::invokeRxLocalFunction[]
    }

    //tag::beginFunctionClient[]
    @FunctionClient
    internal interface MathClient {
        //end::beginFunctionClient[]

        //tag::functionMax[]
        fun max(): Long?  //<1>
        //end::functionMax[]

        //tag::functionRnd[]
        @Named("round")
        fun rnd(value: Float): Int
        //end::functionRnd[]

        fun sum(sum: Sum): Long
        //tag::endFunctionClient[]
    }
    //end::endFunctionClient[]


    //tag::rxFunctionClient[]
    @FunctionClient
    internal interface RxMathClient {
        fun max(): Single<Long>

        @Named("round")
        fun rnd(value: Float): Single<Int>

        fun sum(sum: Sum): Single<Long>
    }
    //end::rxFunctionClient[]
}
