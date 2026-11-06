package example.micronaut.suspend

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
open class ExecutableOnlySuspendingService {

    @Executable
    open suspend fun suspending(name: String): String {
        delay(1)
        return "Hello $name"
    }
}
