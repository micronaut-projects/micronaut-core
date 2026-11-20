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

import io.micronaut.core.annotation.AnnotationBuilder
import io.micronaut.core.annotation.AnnotationBuilderRegistry
import io.micronaut.core.annotation.RegisterAnnotations
import jakarta.inject.Named
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

@Retention(AnnotationRetention.RUNTIME)
annotation class KtLeaf(val value: Int = 7)

@Retention(AnnotationRetention.RUNTIME)
annotation class KtInner(val name: String, val leaves: Array<KtLeaf> = [KtLeaf()])

@Retention(AnnotationRetention.RUNTIME)
annotation class KtOuter(val value: Array<KtInner>)

// only the outer annotation is listed: the annotation types of its members get a builder with it
@RegisterAnnotations(KtOuter::class, Named::class)
class KtNestedBuilders

@KtLeaf
@KtInner("inner")
@KtOuter([KtInner("a", [KtLeaf(1), KtLeaf(2)]), KtInner("b")])
@Named("library")
class KtNestedAnnotated

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

    @Test
    fun readsTheMembersAsTypedValues() {
        val builder = AnnotationBuilder.find(KtSample::class.java).orElseThrow()
        val sample = builder.build(mapOf("name" to "n", "shade" to "LIGHT", "tags" to listOf(mapOf("value" to "t1"), mapOf("value" to "t2"))))
        val members = builder.toMap(sample)
        assertEquals(listOf("name", "count", "flag", "shade", "type", "names", "numbers", "tag", "tags"), members.keys.toList())
        assertEquals("n", members["name"])
        assertEquals(3, members["count"])
        assertSame(Shade.LIGHT, members["shade"])
        assertEquals(Any::class.java, members["type"])
        assertArrayEquals(arrayOf("a", "b"), members["names"] as Array<*>)
        assertArrayEquals(intArrayOf(1, 2), members["numbers"] as IntArray)
        assertEquals("default", (members["tag"] as KtTag).value)
        assertInstanceOf(Array<KtTag>::class.java, members["tags"])
        assertEquals(listOf("t1", "t2"), (members["tags"] as Array<*>).map { (it as KtTag).value })
    }

    @Test
    fun readsTheMembersOfTheAnnotationOfTheJvm() {
        val builder = AnnotationBuilder.find(KtSample::class.java).orElseThrow()
        val jvm = KtAnnotated::class.java.getAnnotation(KtSample::class.java)
        val members = builder.toMap(jvm)
        assertEquals("jvm", members["name"])
        assertEquals(9, members["count"])
        assertEquals(jvm, builder.build(members))
    }

    @Test
    fun theArraysOfTheMembersAreCopies() {
        val builder = AnnotationBuilder.find(KtSample::class.java).orElseThrow()
        val sample = builder.build(mapOf("name" to "n"))
        val members = builder.toMap(sample)
        @Suppress("UNCHECKED_CAST")
        (members["names"] as Array<String>)[0] = "changed"
        (members["numbers"] as IntArray)[0] = 42
        assertArrayEquals(arrayOf("a", "b"), sample.names)
        assertArrayEquals(intArrayOf(1, 2), sample.numbers)
        assertNotSame(members["names"], builder.toMap(sample)["names"])
    }

    @Test
    fun theAnnotationTypesOfTheMembersGetABuilder() {
        val outer = AnnotationBuilder.find(KtOuter::class.java).orElseThrow()
        val leaf = AnnotationBuilder.find(KtLeaf::class.java).orElseThrow()
        assertTrue(AnnotationBuilder.find(KtInner::class.java).isPresent)
        val built = outer.build(mapOf("value" to listOf(mapOf("name" to "a", "leaves" to listOf(mapOf("value" to 1), mapOf("value" to 2))), mapOf("name" to "b"))))
        assertEquals(listOf("a", "b"), built.value.map { it.name })
        assertEquals(listOf(1, 2), built.value[0].leaves.map { it.value })
        assertEquals(mapOf("value" to 7), leaf.toMap(built.value[1].leaves[0]))
        assertEquals(KtNestedAnnotated::class.java.getAnnotation(KtOuter::class.java), outer.build(outer.toMap(built)))
        assertInstanceOf(Array<KtInner>::class.java, outer.toMap(built)["value"])
    }

    @Test
    fun anAnnotationOfALibraryGetsABuilder() {
        val named = AnnotationBuilder.find(Named::class.java).orElseThrow()
        val jvm = KtNestedAnnotated::class.java.getAnnotation(Named::class.java)
        assertEquals(mapOf("value" to "library"), named.toMap(jvm))
        assertEquals(jvm, named.build(mapOf("value" to "library")))
    }
}
