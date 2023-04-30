package io.micronaut.kotlin.processing.aop.introduction.with_around

@ProxyIntroductionAndAroundAndIntrospectedAndExecutable
open class MyBean6 {
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
