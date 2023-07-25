package io.micronaut.kotlin.processing.aop.compile

interface MyClient {
    fun getUsersById(id: String): String

    val users: List<String>
}

class MyClientImpl : MyClient {
    override fun getUsersById(id: String): String {
        return "bob"
    }

    override val users: List<String>
        get() = listOf("Fred", "Bob")

}
