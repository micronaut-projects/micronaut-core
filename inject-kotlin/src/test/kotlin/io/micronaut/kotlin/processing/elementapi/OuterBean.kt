package io.micronaut.kotlin.processing.elementapi

class OuterBean {

    class InnerBean {
        private var name: String? = null
    }

    interface InnerInterface {
        fun getName(): String
    }
}
