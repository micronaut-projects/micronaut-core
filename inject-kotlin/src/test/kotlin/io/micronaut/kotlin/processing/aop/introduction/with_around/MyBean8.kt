package io.micronaut.kotlin.processing.aop.introduction.with_around

import io.micronaut.context.annotation.Executable

@Executable
@ProxyAround
open class MyBean8 {
    private var id: Long? = null
    private var name: String? = null

    open fun getId(): Long? = id

    open fun setId(id: Long?) {
        this.id = id
    }

    open fun getName(): String? = name

    open fun setId(name: String?) {
        this.name = name
    }
}
