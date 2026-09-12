package example.micronaut.suspend

import io.micronaut.context.BeanContext
import io.micronaut.inject.ExecutableMethod
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import jakarta.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.lang.reflect.Method
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@MicronautTest
class SuspendingTargetMethodTest {

    @Inject
    lateinit var beanContext: BeanContext

    @Inject
    lateinit var service: SuspendingService

    @Inject
    lateinit var executableOnlyService: ExecutableOnlySuspendingService

    @Test
    fun testTargetMethodOfSuspendFunction() {
        val targetMethod = executableMethod(SuspendingService::class.java).targetMethod

        assertInvocable(targetMethod, service)
    }

    @Test
    fun testTargetMethodOfExecutableOnlySuspendFunction() {
        val targetMethod = executableMethod(ExecutableOnlySuspendingService::class.java).targetMethod

        assertInvocable(targetMethod, executableOnlyService)
    }

    @Test
    fun testReflectiveLookupOfSuspendFunction() {
        // The name and parameter types only exist at runtime, so the native image can't fold
        // the lookup and has to rely on the reflection metadata written for @ReflectiveAccess
        val executableMethod = executableMethod(SuspendingService::class.java)
        val parameterTypes = executableMethod.arguments.map { it.type }.toTypedArray()
        val method = SuspendingService::class.java.getDeclaredMethod(executableMethod.methodName, *parameterTypes)

        assertInvocable(method, service)
    }

    private fun executableMethod(type: Class<*>): ExecutableMethod<*, *> =
        beanContext.getBeanDefinition(type)
            .executableMethods
            .single { it.methodName == "suspending" }

    private fun assertInvocable(method: Method, target: Any) {
        Assertions.assertEquals(
            listOf(String::class.java, Continuation::class.java),
            method.parameterTypes.toList()
        )
        val result = runBlocking {
            suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
                method.invoke(target, "World", continuation)
            }
        }
        Assertions.assertEquals("Hello World", result)
    }
}
