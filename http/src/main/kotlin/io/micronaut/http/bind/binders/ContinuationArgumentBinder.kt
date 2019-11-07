package io.micronaut.http.bind.binders

import io.micronaut.core.bind.ArgumentBinder
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class ContinuationArgumentBinder: TypedRequestArgumentBinder<Continuation<*>> {
    override fun bind(context: ArgumentConversionContext<Continuation<*>>?, source: HttpRequest<*>): ArgumentBinder.BindingResult<Continuation<*>> =
        with (CustomContinuation()) {
            source.setAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY, this)
            ArgumentBinder.BindingResult { Optional.of(this as Continuation<*>) }
        }

    override fun argumentType(): Argument<Continuation<*>> = Argument.of(Continuation::class.java)

    override fun supportsSuperTypes(): Boolean = false

    companion object {
        @JvmStatic
        fun extractContinuationCompletableFutureSupplier(source: HttpRequest<*>): Supplier<CompletableFuture<*>> =
            source.getAttribute(CONTINUATION_ARGUMENT_ATTRIBUTE_KEY).orElse(null) as CustomContinuation

        private const val CONTINUATION_ARGUMENT_ATTRIBUTE_KEY = "__continuation__"
    }
}

private class CustomContinuation: Continuation<Any>, Supplier<CompletableFuture<*>> {
    private val completableFuture = CompletableFuture<Any>()

    override fun get(): CompletableFuture<*> = completableFuture

    override val context: CoroutineContext
        get() = EmptyCoroutineContext

    override fun resumeWith(result: Result<Any>) {
        if (result.isSuccess) {
            completableFuture.complete(result.getOrNull())
        } else {
            completableFuture.completeExceptionally(result.exceptionOrNull())
        }
    }
}