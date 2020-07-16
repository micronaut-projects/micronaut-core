package io.micronaut.scheduling.instrument

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.rx2.await
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import kotlin.coroutines.CoroutineContext

typealias TokenDetail = String
@Controller
class Controller(private val executorService: ExecutorService) : CoroutineScope {

    override val coroutineContext: CoroutineContext = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            executorService.execute(block)
        }

    }

    val stream: Observable<TokenDetail> by lazy {
        requestNextToken(0).replay(1).autoConnect()
    }

    fun current() = stream.take(1).singleOrError()!!

    private fun requestNextToken(idx: Long): Observable<TokenDetail> {
        return Observable.just(idx).map {
            Thread.sleep(5000)
            "idx + $it"
        }.subscribeOn(Schedulers.io())
    }

    @Get("/tryout/{times}")
    fun tryout(@QueryValue("times") times: Int) = asyncResult {
        (1..times).map {
            async { current().await() }
        }.map {
            it.await()
        }
    }

    private fun <T> asyncResult(block: suspend CoroutineScope.() -> T): CompletableFuture<T> {
        return async { block() }.asCompletableFuture()
    }
}