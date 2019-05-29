package io.micronaut.docs.context

// tag::imports[]
import io.micronaut.runtime.Micronaut
// end::imports[]

// tag::class[]
class Application {

    static void main(String[] args) {
        Micronaut.build()
                .mainClass(Application)
                .environmentPropertySource(false)
                //or
                .environmentVariableIncludes("THIS_ENV_ONLY")
                //or
                .environmentVariableExcludes("EXCLUDED_ENV")
                .start()
    }
}
// end::class[]

