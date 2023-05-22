package io.micronaut.kotlin.processing.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties
import java.util.*

@ConfigurationProperties("rec")
class RecConf {

    var namesListOf: List<String>? = null
    var mapChildren: Map<String, RecConf>? = null
    var listChildren: List<RecConf>? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) return true;
        if (other == null || this.javaClass != other.javaClass) return false;
        val recConf = other as RecConf
        return Objects.equals(namesListOf, recConf.namesListOf) &&
                Objects.equals(mapChildren, recConf.mapChildren) &&
                Objects.equals(listChildren, recConf.listChildren)
    }

    override fun hashCode(): Int {
        return Objects.hash(namesListOf, mapChildren, listChildren)
    }
}
