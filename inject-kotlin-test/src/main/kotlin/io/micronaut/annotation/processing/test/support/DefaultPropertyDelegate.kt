package io.micronaut.annotation.processing.test.support

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("MemberVisibilityCanBePrivate")
internal class DefaultPropertyDelegate<R,T>(private val createDefault: () -> T) : ReadWriteProperty<R, T> {
    val hasDefaultValue
        @Synchronized get() = (value == DEFAULT)

    private var value: Any? = DEFAULT
    val defaultValue by lazy { createDefault() }

    @Synchronized
    override operator fun getValue(thisRef: R, property: KProperty<*>): T {
        @Suppress("UNCHECKED_CAST")
        return if(hasDefaultValue)
            defaultValue
        else
            value as T
    }

    @Synchronized
    override operator fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        this.value = value
    }

    companion object {
        private object DEFAULT
    }
}

internal fun <R,T> default(createDefault: () -> T) = DefaultPropertyDelegate<R,T>(createDefault)
