package io.micronaut.core.beans


import io.micronaut.core.annotation.Introspected
import java.util.*

@Introspected
abstract class Item<T : Item<T>> {

    var id: Long? = null

    var revisions: MutableList<T> = ArrayList()
}
