package io.micronaut.kotlin.processing.aop.introduction.with_around

@ProxyIntroductionAndAroundAndIntrospectedAndExecutable
open class MyBean9 {
    private var multidim: Array<Array<String>>? = null
    private var primitiveMultidim: Array<IntArray>? = null

    open fun getMultidim(): Array<Array<String>>? {
        return multidim
    }

    open fun setMultidim(multidim: Array<Array<String>>?) {
        this.multidim = multidim
    }

    open fun getPrimitiveMultidim(): Array<IntArray>? {
        return primitiveMultidim
    }

    open fun setPrimitiveMultidim(primitiveMultidim: Array<IntArray>?) {
        this.primitiveMultidim = primitiveMultidim
    }
}
