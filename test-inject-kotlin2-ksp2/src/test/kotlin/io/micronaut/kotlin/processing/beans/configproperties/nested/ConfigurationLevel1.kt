package io.micronaut.kotlin.processing.beans.configproperties.nested

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("level1")
interface ConfigurationLevel1 {
    val foo: String
    @ConfigurationProperties("level2")
    interface ConfigurationLevel2 {
        val bar: String
        @ConfigurationProperties("level3")
        interface ConfigurationLevel3 {
            val baz: String
        }
    }
}
