package example

import io.micronaut.runtime.Micronaut

object Application {

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("org.slf4j.simpleLogger", "TRACE")
        Micronaut.build().packages(Package.getPackage("example")).start(javaClass)
    }
}


