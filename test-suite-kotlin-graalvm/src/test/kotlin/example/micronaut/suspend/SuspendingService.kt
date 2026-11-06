package example.micronaut.suspend

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.ReflectiveAccess
import jakarta.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
open class SuspendingService {

    @Executable
    @ReflectiveAccess
    open suspend fun suspending(name: String): String {
        delay(1)
        return "Hello $name"
    }
}
