/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.annotationbuilders

import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.RegisterAnnotations
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

enum class Shade { LIGHT, DARK }

@Retention(AnnotationRetention.RUNTIME)
annotation class KtTag(val value: String = "none", val weight: Int = 1)

@Retention(AnnotationRetention.RUNTIME)
annotation class KtSample(
    val name: String,
    val count: Int = 3,
    val flag: Boolean = true,
    val shade: Shade = Shade.DARK,
    val type: kotlin.reflect.KClass<*> = Any::class,
    val names: Array<String> = ["a", "b"],
    val numbers: IntArray = [1, 2],
    val tag: KtTag = KtTag("default"),
    val tags: Array<KtTag> = [KtTag("one")]
)

@RegisterAnnotations(KtSample::class, KtTag::class)
class KtSampleBuilders

// KSP reads the defaults of an annotation type from a usage of it, so each type is used at least once
@KtTag
@KtSample(name = "jvm", count = 9, tag = KtTag("jvm-tag"))
class KtAnnotated

class AnnotationBuildersKotlinTest {

    private val registry = AnnotationBuilderRegistry.shared()

    @Test
    fun buildsFromMembersAndDefaults() {
        val sample = registry.build(KtSample::class.java, mapOf("name" to "n", "shade" to "LIGHT", "tag" to mapOf("value" to "nested")))
        assertEquals("n", sample.name)
        assertEquals(3, sample.count)
        assertEquals(true, sample.flag)
        assertEquals(Shade.LIGHT, sample.shade)
        assertEquals(Any::class.java, sample.type.java)
        assertArrayEquals(arrayOf("a", "b"), sample.names)
        assertArrayEquals(intArrayOf(1, 2), sample.numbers)
        assertEquals("nested", sample.tag.value)
        assertEquals(1, sample.tag.weight)
        assertEquals(listOf("one"), sample.tags.map { it.value })
    }

    @Test
    fun equalsTheAnnotationOfTheJvm() {
        val jvm = KtAnnotated::class.java.getAnnotation(KtSample::class.java)
        val built = registry.build(KtSample::class.java, mapOf("name" to "jvm", "count" to 9, "tag" to mapOf("value" to "jvm-tag")))
        assertEquals(jvm, built)
        assertEquals(built, jvm)
    }
}
