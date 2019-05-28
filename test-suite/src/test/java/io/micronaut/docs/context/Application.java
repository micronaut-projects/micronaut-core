package io.micronaut.docs.context;

// tag::imports[]
import io.micronaut.runtime.Micronaut;
// end::imports[]

// tag::class[]
public class Application {

    public static void main(String[] args) {
        Micronaut.build(null)
                .mainClass(Application.class)
                .environmentPropertySource(false)
                //or
                .environmentVariableIncludes("THIS_ENV_ONLY")
                //or
                .environmentVariableExcludes("EXCLUDED_ENV")
                .start();
    }
}
// end::class[]

