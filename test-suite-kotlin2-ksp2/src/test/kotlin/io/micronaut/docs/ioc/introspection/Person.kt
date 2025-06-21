package io.micronaut.docs.ioc.introspection

// tag::class[]
import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected

@Introspected
@AccessorsStyle(readPrefixes = [""], writePrefixes = [""]) // <1>
class Person(private var name: String, private var age: Int) {
    fun name(): String { // <2>
        return name
    }

    fun name(name: String) { // <2>
        this.name = name
    }

    fun age(): Int { // <2>
        return age
    }

    fun age(age: Int) { // <2>
        this.age = age
    }
}
// end::class[]
