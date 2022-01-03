package io.micronaut.kotlin.processing.elementapi

class OuterBean {

    class InnerBean {
        var name: String? = null
    }

    interface InnerInterface {
        fun getName(): String
    }
}
