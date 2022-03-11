package io.micronaut.kotlin.processing.aop.itfce

abstract class AbstractInterfaceImpl<A> : InterfaceClass<A> {

    override fun test(name: String): String {
        return "Name is $name"
    }

    override fun test(name: String, age: Int): String {
        return "Name is $name and age is $age"
    }
}
