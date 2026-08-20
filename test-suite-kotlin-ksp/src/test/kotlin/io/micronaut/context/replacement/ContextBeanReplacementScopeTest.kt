package io.micronaut.context.replacement

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import jakarta.inject.Singleton
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

interface ReplacedApi

@Context
@Requires(property = "spec.name", value = "ContextBeanReplacementScopeTest")
open class OriginalContextApi : ReplacedApi {
    companion object {
        @JvmField
        val created = AtomicInteger(0)
    }
    init {
        created.incrementAndGet()
    }
}

@Singleton
@Replaces(OriginalContextApi::class)
@Requires(property = "spec.name", value = "ContextBeanReplacementScopeTest")
open class ReplacementSingletonApi : ReplacedApi {
    companion object {
        @JvmField
        val created = AtomicInteger(0)
    }
    init {
        created.incrementAndGet()
    }
}

class ContextBeanReplacementScopeTest {

    @Test
    fun replacingContextBeanWithSingletonShouldPreventOriginalInstantiation() {
        // Reset counters for isolation
        OriginalContextApi.created.set(0)
        ReplacementSingletonApi.created.set(0)

        val ctx = ApplicationContext.run(mapOf("spec.name" to "ContextBeanReplacementScopeTest"))
        try {
            // Expected: Replacing a @Context bean with a non-@Context bean should prevent eager instantiation
            // Actual (bug): OriginalContextApi is still eagerly created at startup
            assertEquals(0, OriginalContextApi.created.get(), "Original @Context bean should not be instantiated when replaced by a non-context bean")

            val api = ctx.getBean(ReplacedApi::class.java)
            assertTrue(api is ReplacementSingletonApi, "Injected bean should be the replacement")
            assertEquals(1, ReplacementSingletonApi.created.get(), "Replacement bean should be constructed once on demand")
            assertEquals(0, OriginalContextApi.created.get(), "Original bean must not be constructed at all")
        } finally {
            ctx.close()
        }
    }
}
