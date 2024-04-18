package io.micronaut.core.beans

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate.now
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KotlinBeanIntrospectionSpec {

    @Test
    fun testWithValueOnKotlinDataClassWithDefaultValues() {
        val introspection = BeanIntrospection.getIntrospection(SomeEntity::class.java)

        val instance = introspection.instantiate(10L, "foo")

        assertEquals(10, instance.id)
        assertEquals("foo", instance.something)

        val changed = introspection.getRequiredProperty("something", String::class.java)
            .withValue(instance, "changed")

        assertEquals(10, changed.id)
        assertEquals("changed", changed.something)

    }

    @Test
    fun testIsProperties() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity::class.java)

        assertEquals(listOf("id", "name", "getSurname", "isDeleted", "isImportant", "corrected", "upgraded", "isMyBool", "isMyBool2", "myBool3", "myBool4", "myBool5"), introspection.propertyNames.asList())

        val introspection2 = BeanIntrospection.getIntrospection(TestEntity2::class.java)

        assertEquals(listOf("id", "name", "getSurname", "isDeleted", "isImportant", "corrected", "upgraded", "isMyBool", "isMyBool2", "myBool3", "myBool4", "myBool5"), introspection2.propertyNames.asList())
    }

    @Test
    fun testDefaults() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity3::class.java)

        val instance1 = introspection.instantiate(null, "Stepanov", null, 123)

        assertEquals("Denis", instance1.firstName)
        assertEquals("Stepanov", instance1.lastName)
        assertEquals("IT", instance1.job)
        assertEquals(123, instance1.age)

        val instance2 = introspection.instantiate("Jeff", "Hello", null, 123)

        assertEquals("Jeff", instance2.firstName)
        assertEquals("Hello", instance2.lastName)
        assertEquals("IT", instance2.job)
        assertEquals(123, instance2.age)

        val instance3 = introspection.instantiate(null, "Hello", "HR", 22)

        assertEquals("Denis", instance3.firstName)
        assertEquals("Hello", instance3.lastName)
        assertEquals("HR", instance3.job)
        assertEquals(22, instance3.age)

        val test1 = introspection.beanMethods.stream().filter { m -> m.name.equals("test1") }.findFirst().get()
        assertEquals("Z B 3", test1.invoke(instance3, "Z", "B", 3))
        assertEquals("A B 99", test1.invoke(instance3, null, "B", 99))
        assertEquals("A Z 99", test1.invoke(instance3, null, "Z", 99))

        val test2 = introspection.beanMethods.stream().filter { m -> m.name.equals("test2") }.findFirst().get()
        assertEquals("A", test2.invoke(instance3, null))
        assertEquals("B", test2.invoke(instance3, "B"))

        val test3 = introspection.beanMethods.stream().filter { m -> m.name.equals("test3") }.findFirst().get()
        assertEquals("678", test3.invoke(instance3, 678))
        assertTrue {
            assertFails {
                test3.invoke(instance3, null)
            } is NullPointerException
        }

        val test4 = introspection.beanMethods.stream().filter { m -> m.name.equals("test4") }.findFirst().get()
        assertEquals("88", test4.invoke(instance3, null))
        assertEquals("99", test4.invoke(instance3, 99))
    }

    @Test
    fun testAllDefaultsConstructor() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity4::class.java)

        val instance0 = introspection.instantiate()

        assertEquals("Denis", instance0.firstName)
        assertEquals("Stepanov", instance0.lastName)
        assertEquals("IT", instance0.job)
        assertEquals(99, instance0.age)

        val instance1 = introspection.instantiate(null, "Stepanov", null, 123)

        assertEquals("Denis", instance1.firstName)
        assertEquals("Stepanov", instance1.lastName)
        assertEquals("IT", instance1.job)
        assertEquals(123, instance1.age)

        val instance2 = introspection.instantiate("Jeff", "Hello", null, 123)

        assertEquals("Jeff", instance2.firstName)
        assertEquals("Hello", instance2.lastName)
        assertEquals("IT", instance2.job)
        assertEquals(123, instance2.age)

        val instance3 = introspection.instantiate(null, "Hello", "HR", 22)

        assertEquals("Denis", instance3.firstName)
        assertEquals("Hello", instance3.lastName)
        assertEquals("HR", instance3.job)
        assertEquals(22, instance3.age)

        val test1 = introspection.beanMethods.stream().filter { m -> m.name.equals("test1") }.findFirst().get()
        assertEquals("Z B 3", test1.invoke(instance3, "Z", "B", 3))
        assertEquals("A B 99", test1.invoke(instance3, null, "B", 99))
        assertEquals("A Z 99", test1.invoke(instance3, null, "Z", 99))

        val test2 = introspection.beanMethods.stream().filter { m -> m.name.equals("test2") }.findFirst().get()
        assertEquals("A", test2.invoke(instance3, null))
        assertEquals("B", test2.invoke(instance3, "B"))

        val test3 = introspection.beanMethods.stream().filter { m -> m.name.equals("test3") }.findFirst().get()
        assertEquals("678", test3.invoke(instance3, 678))
        assertTrue {
            assertFails {
                test3.invoke(instance3, null)
            } is NullPointerException
        }

        val test4 = introspection.beanMethods.stream().filter { m -> m.name.equals("test4") }.findFirst().get()
        assertEquals("88", test4.invoke(instance3, null))
        assertEquals("99", test4.invoke(instance3, 99))
    }

    @Test
    fun testALotOfParams() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity5::class.java)
        val bean = introspection.instantiate()
        for (beanProperty in introspection.beanProperties) {
            assertEquals(beanProperty.name, beanProperty.get(bean))
        }
        assertEquals("s1", bean.s1)
        assertEquals("s2", bean.s2)
        assertEquals("s3", bean.s3)
        assertEquals("s4", bean.s4)
        assertEquals("s5", bean.s5)
        assertEquals("s6", bean.s6)
        assertEquals("s5", bean.s5)
        assertEquals("s50", bean.s50)
    }

    @Test
    fun testALotOfParams2() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity6::class.java)
        val bean = introspection.instantiate()
        for (beanProperty in introspection.beanProperties) {
            assertEquals(beanProperty.name, beanProperty.get(bean))
        }
        assertEquals("s1", bean.s1)
        assertEquals("s2", bean.s2)
        assertEquals("s3", bean.s3)
        assertEquals("s4", bean.s4)
        assertEquals("s5", bean.s5)
        assertEquals("s6", bean.s6)
        assertEquals("s5", bean.s5)
        assertEquals("s32", bean.s32)
    }

    @Test
    fun `Should create a bean with correct attributes`() {
        val introspection = BeanIntrospection.getIntrospection(TestEntity7::class.java)
        val attributes = arrayOf(
            "a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7", "a8", "a9",
            "a10", "a11", "a12", "a13", "a14", "a15", "a16", "a17", "a18", "a19",
            "a20", "a21", "a22", "a23", "a24", "a25", "a26", "a27", "a28", "a29",
            "a30", "a31",
            now().plusDays(32),
            now().plusDays(33),
            now().plusDays(34),
            now().plusDays(35),
            now().plusDays(36),
            now().plusDays(37),
            now().plusDays(38),
            now().plusDays(39),
        )

        val bean = introspection.instantiate(*attributes)

        assertEquals(bean.a0, attributes[0])
        assertEquals(bean.a1, attributes[1])
        assertEquals(bean.a2, attributes[2])
        assertEquals(bean.a3, attributes[3])
        assertEquals(bean.a4, attributes[4])
        assertEquals(bean.a5, attributes[5])
        assertEquals(bean.a6, attributes[6])
        assertEquals(bean.a7, attributes[7])
        assertEquals(bean.a8, attributes[8])
        assertEquals(bean.a9, attributes[9])
        assertEquals(bean.a10, attributes[10])
        assertEquals(bean.a11, attributes[11])
        assertEquals(bean.a12, attributes[12])
        assertEquals(bean.a13, attributes[13])
        assertEquals(bean.a14, attributes[14])
        assertEquals(bean.a15, attributes[15])
        assertEquals(bean.a16, attributes[16])
        assertEquals(bean.a17, attributes[17])
        assertEquals(bean.a18, attributes[18])
        assertEquals(bean.a19, attributes[19])
        assertEquals(bean.a20, attributes[20])
        assertEquals(bean.a21, attributes[21])
        assertEquals(bean.a22, attributes[22])
        assertEquals(bean.a23, attributes[23])
        assertEquals(bean.a24, attributes[24])
        assertEquals(bean.a25, attributes[25])
        assertEquals(bean.a26, attributes[26])
        assertEquals(bean.a27, attributes[27])
        assertEquals(bean.a28, attributes[28])
        assertEquals(bean.a29, attributes[29])
        assertEquals(bean.a30, attributes[30])
        assertEquals(bean.a31, attributes[31])
        assertEquals(bean.a32, attributes[32])
        assertEquals(bean.a33, attributes[33])
        assertEquals(bean.a34, attributes[34])
        assertEquals(bean.a35, attributes[35])
        assertEquals(bean.a36, attributes[36])
        assertEquals(bean.a37, attributes[37])
        assertEquals(bean.a38, attributes[38])
        assertEquals(bean.a39, attributes[39])
    }
}
