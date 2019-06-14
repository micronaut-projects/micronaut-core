package io.micronaut.inject.visitor.beans

import io.kotlintest.matchers.boolean.shouldBeFalse
import io.kotlintest.matchers.boolean.shouldBeTrue
import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec
import io.micronaut.core.beans.BeanIntrospector

class BeanIntrospectorSpec : StringSpec({

    "test get introspection" {
        val introspection = BeanIntrospector.SHARED.getIntrospection(TestBean::class.java)

        introspection.propertyNames.size.shouldBe(5)
        introspection.getProperty("age").isPresent.shouldBeTrue()
        introspection.getProperty("name").isPresent.shouldBeTrue()

        val testBean = introspection.instantiate("fred", 10, arrayOf("one"))

        testBean.name.shouldBe("fred")
        testBean.flag.shouldBeFalse()

        shouldThrow<UnsupportedOperationException> {
            introspection.getProperty("name").get().set(testBean, "bob")
        }

        testBean.stuff.shouldBe("default")

        introspection.getProperty("stuff").get().set(testBean, "newvalue")
        introspection.getProperty("flag").get().set(testBean, true)
        introspection.getProperty("flag", Boolean::class.java).get().get(testBean)?.shouldBeTrue()

        testBean.stuff.shouldBe("newvalue")
    }
})