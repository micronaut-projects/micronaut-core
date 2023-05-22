package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import java.util.*

@ConfigurationProperties("rec")
class RecConf {
    var namesListOf: List<String>? = null
    var mapChildren: Map<String, RecConf>? = null
    var listChildren: List<RecConf>? = null

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        if (o == null || javaClass != o.javaClass) return false
        val recConf = o as RecConf
        return namesListOf == recConf.namesListOf &&
                mapChildren == recConf.mapChildren &&
                listChildren == recConf.listChildren
    }

    override fun hashCode(): Int {
        return Objects.hash(namesListOf, mapChildren, listChildren)
    }
}
