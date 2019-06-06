package io.micronaut.docs.context

// tag::imports[]
import io.micronaut.runtime.Micronaut
// end::imports[]

// tag::class[]
object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        Micronaut.build(null)
                .mainClass(Application::class.java)
                .environmentPropertySource(false)
                //or
                .environmentVariableIncludes("THIS_ENV_ONLY")
                //or
                .environmentVariableExcludes("EXCLUDED_ENV")
                .start()
    }
}
// end::class[]

