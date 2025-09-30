/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.beans.executable

import io.micronaut.context.annotation.BeanProperties
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.type.Argument
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.validation.RequiresValidation
import jakarta.annotation.Nonnull
import spock.lang.Issue
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class ExecutableBeanSpec extends Specification {

    void "test executable method return types"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float): Int {
        return num.roundToInt()
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == int.class
    }

    void "test executable method return nullable types"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float): Int? {
        return null
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == Integer.class
    }

    void "test executable method nullable parameter types"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float?): Array<Int> {
        return emptyArray()
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", Float.class).get().returnType.type == Integer[].class
    }

    void "test executable method nullable parameter types 2"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float?): IntArray? {
        return intArrayOf(1, 2, 3)
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", Float.class).get().returnType.type == int[].class
    }

    void "test executable method nullable parameter types 3"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float?): IntArray {
        return intArrayOf(1, 2, 3)
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", Float.class).get().returnType.type == int[].class
    }

    void "test executable method nullable return array type"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.ExecutableBean1','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class ExecutableBean1 {

    fun round(num: Float): Array<Int>? {
        return null
    }
}
''')
        expect:
        definition != null
        definition.findMethod("round", float.class).get().returnType.type == Integer[].class
    }

    @Issue('#2789')
    void "test don't generate executable methods for inherited protected or package private methods"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

@jakarta.inject.Singleton
@Executable
class MyBean: Parent() {

    fun round(num: Float): Int {
        return num.roundToInt()
    }
}

open class Parent {
    protected fun protectedMethod() {
    }

    internal fun packagePrivateMethod() {
    }

    private fun privateMethod() {
    }
}
''')
        expect:
        definition != null
        !definition.findMethod("privateMethod").isPresent()
        !definition.findMethod("packagePrivateMethod").isPresent()
        !definition.findMethod("protectedMethod").isPresent()
    }

    void "bean definition should not be created for class with only executable methods"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.context.annotation.Executable
import kotlin.math.roundToInt

class MyBean {

    @Executable
    fun round(num: Float): Int {
        return num.roundToInt()
    }
}

''')

        expect:
        definition == null
    }

    void "test multiple executable annotations on a method"() {
        given:
        BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.kotlin.processing.beans.executable.RepeatableExecutable

@jakarta.inject.Singleton
class MyBean  {

    @RepeatableExecutable("a")
    @RepeatableExecutable("b")
    fun run() {

    }
}
''')
        expect:
        definition != null
        definition.findMethod("run").isPresent()
    }

    void "test how annotations are preserved"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import jakarta.validation.Valid
import java.util.List
import io.micronaut.kotlin.processing.beans.executable.*

@jakarta.inject.Singleton
open class MyBean {
    @Executable
    open fun saveAll(books: @Valid MutableList<MyBook>) {
    }

    @Executable
    open fun <T : MyBook> saveAll2(book: @Valid MutableList<out T>) {
    }

    @Executable
    open fun <T : MyBook> saveAll3(book: @Valid MutableList<T>) {
    }

    @Executable
    open fun save2(book: @Valid MyBook) {
    }

    @Executable
    open fun <T : MyBook> save3(book: @Valid T) {
    }

    @Executable
    open fun get(): MyBook? {
        return null
    }
}

''')
        when:
            def saveAll = definition.findMethod("saveAll", List.class).get()
            def listTypeArgument = saveAll.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll.hasAnnotation(RequiresValidation)
            !saveAll.hasStereotype(RequiresValidation)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll2 = definition.findMethod("saveAll2", List.class).get()
            def listTypeArgument2 = saveAll2.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll2.hasAnnotation(RequiresValidation)
            !saveAll2.hasStereotype(RequiresValidation)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll3 = definition.findMethod("saveAll3", List.class).get()
            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
        then:
            !saveAll3.hasAnnotation(RequiresValidation)
            !saveAll3.hasStereotype(RequiresValidation)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def save2 = definition.findMethod("save2", MyBook.class).get()
            def parameter2 = save2.getArguments()[0]
        then:
            !save2.hasAnnotation(RequiresValidation)
            !save2.hasStereotype(RequiresValidation)
            !parameter2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !parameter2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !parameter2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !parameter2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !parameter2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def save3 = definition.findMethod("save3", MyBook.class).get()
            def parameter3 = save3.getArguments()[0]
        then:
            !save3.hasAnnotation(RequiresValidation)
            !save3.hasStereotype(RequiresValidation)
            !parameter3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !parameter3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !parameter3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !parameter3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !parameter3.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def get = definition.findMethod("get").get()
            def returnType = get.getReturnType()
        then:
            !returnType.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !returnType.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !returnType.getAnnotationMetadata().hasStereotype(Introspected.class)
            !returnType.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !returnType.getAnnotationMetadata().hasStereotype(BeanProperties.class)
    }

    void "test how type annotations are preserved 2"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test.MyBean','''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.context.annotation.Executable
import jakarta.validation.Valid
import java.util.List
import io.micronaut.kotlin.processing.beans.executable.*

@jakarta.inject.Singleton
internal open class MyBean {
    @Executable
    open fun saveAll(books: @Valid MutableList<@TypeUseRuntimeAnn MyBook>) {
    }

    @Executable
    open fun <@TypeUseRuntimeAnn T : MyBook> saveAll2(book: @Valid MutableList<out T>) {
    }

    @Executable
    open fun <@TypeUseRuntimeAnn T : MyBook> saveAll3(book: @Valid MutableList<T>) {
    }

    @Executable
    open fun save2(book: @Valid @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    open fun <@TypeUseRuntimeAnn T : MyBook> save3(book: @Valid T) {
    }

    @Executable
    open fun get(): @TypeUseRuntimeAnn MyBook? {
        return null
    }
}

''')
        when:
            def saveAll = definition.findMethod("saveAll", List.class).get()
            def listTypeArgument = saveAll.getArguments()[0].getTypeParameters()[0]
        then:
            listTypeArgument.getAnnotationMetadata().hasAnnotation(TypeUseRuntimeAnn.class)

            !saveAll.hasAnnotation(RequiresValidation)
            !saveAll.hasStereotype(RequiresValidation)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll2 = definition.findMethod("saveAll2", List.class).get()
            def listTypeArgument2 = saveAll2.getArguments()[0].getTypeParameters()[0]
        then:
            listTypeArgument2.getAnnotationMetadata().hasAnnotation(TypeUseRuntimeAnn.class)

            !saveAll2.hasAnnotation(RequiresValidation)
            !saveAll2.hasStereotype(RequiresValidation)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def saveAll3 = definition.findMethod("saveAll3", List.class).get()
            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
        then:
            listTypeArgument3.getAnnotationMetadata().hasAnnotation(TypeUseRuntimeAnn.class)

            !saveAll3.hasAnnotation(RequiresValidation)
            !saveAll3.hasStereotype(RequiresValidation)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !listTypeArgument3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !listTypeArgument3.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def save2 = definition.findMethod("save2", MyBook.class).get()
            def parameter2 = save2.getArguments()[0]
        then:
            parameter2.getAnnotationMetadata().hasAnnotation(TypeUseRuntimeAnn.class)

            !save2.hasAnnotation(RequiresValidation)
            !save2.hasStereotype(RequiresValidation)
            !parameter2.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !parameter2.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !parameter2.getAnnotationMetadata().hasStereotype(Introspected.class)
            !parameter2.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !parameter2.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def save3 = definition.findMethod("save3", MyBook.class).get()
            def parameter3 = save3.getArguments()[0]
        then:
            parameter3.getAnnotationMetadata().hasAnnotation(TypeUseRuntimeAnn.class)

            !save3.hasAnnotation(RequiresValidation)
            !save3.hasStereotype(RequiresValidation)
            !parameter3.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !parameter3.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !parameter3.getAnnotationMetadata().hasStereotype(Introspected.class)
            !parameter3.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !parameter3.getAnnotationMetadata().hasStereotype(BeanProperties.class)

        when:
            def get = definition.findMethod("get").get()
            def returnType = get.getReturnType()
        then:
            returnType.getAnnotationMetadata().hasAnnotation(TypeUseRuntimeAnn.class)

            !returnType.getAnnotationMetadata().hasAnnotation(MyEntity.class)
            !returnType.getAnnotationMetadata().hasAnnotation(Introspected.class)
            !returnType.getAnnotationMetadata().hasStereotype(Introspected.class)
            !returnType.getAnnotationMetadata().hasAnnotation(BeanProperties.class)
            !returnType.getAnnotationMetadata().hasStereotype(BeanProperties.class)
    }

    void "test how the type annotations from the type are preserved 2"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.context.annotation.Executable
import java.util.List
import io.micronaut.kotlin.processing.beans.executable.*

@jakarta.inject.Singleton
class MyBean {
    @Executable
    fun saveAll(books: List<@TypeUseRuntimeAnn MyBook>) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> saveAll2(book: List<T>) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> saveAll3(book: List<T>) {
    }

    @Executable
    fun <T : MyBook> saveAll4(book: List<out @TypeUseRuntimeAnn T>) {
    }

    @Executable
    fun <T : MyBook> saveAll5(book: List<@TypeUseRuntimeAnn T>) {
    }

    @Executable
    fun save2(book: @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> save3(book: T) {
    }

    @Executable
    fun <T : @TypeUseRuntimeAnn MyBook> save4(book: T) {
    }

    @Executable
    fun <T : MyBook> save5(book: @TypeUseRuntimeAnn T) {
    }

    @Executable
    fun get(): @TypeUseRuntimeAnn MyBook? {
        return null
    }
}

''')
        when:
            def saveAllList = bd.findMethod("saveAll", List).get()
            def saveAllListArgument = saveAllList.getArguments()[0]
        then:
            // The list itself should be marked as Nonnull
            saveAllListArgument.getAnnotationMetadata().getAnnotationNames() == [Nonnull.class.name] as Set<String>

        when:
            def saveAll = bd.findMethod("saveAll", List).get()
            def listTypeArgument = saveAll.getArguments()[0].getTypeParameters()[0]
        then:
            // this is not Nonnull as we are checking the list content type, not the list itself
            validateMyBookArgument(listTypeArgument)

        when:
            def saveAll2 = bd.findMethod("saveAll2", List).get()
            def listTypeArgument2 = saveAll2.getArguments()[0].getTypeParameters()[0]
        then:
            validateMyBookArgument(listTypeArgument2)

        when:
            def saveAll3 = bd.findMethod("saveAll3", List).get()
            def listTypeArgument3 = saveAll3.getArguments()[0].getTypeParameters()[0]
        then:
            validateMyBookArgument(listTypeArgument3)

        when:
            def saveAll4 = bd.findMethod("saveAll4", List).get()
            def listTypeArgument4 = saveAll4.getArguments()[0].getTypeParameters()[0]
        then:
            validateMyBookArgument(listTypeArgument4)

//        when:
//            def saveAll5 = bd.findMethod("saveAll5", List).get()
//            def listTypeArgument5 = saveAll5.getArguments()[0].getTypeParameters()[0]
//        then:
//            validateMyBookArgument(listTypeArgument5)

        when:
            def save2 = bd.findMethod("save2", MyBook).get()
            def parameter2 = save2.getArguments()[0]
        then:
            validateMyBookArgument(parameter2, true)

        when:
            def save3 = bd.findMethod("save3", MyBook).get()
            def parameter3 = save3.getArguments()[0]
        then:
            validateMyBookArgument(parameter3, true)

        when:
            def save4 = bd.findMethod("save4", MyBook).get()
            def parameter4 = save4.getArguments()[0]
        then:
            validateMyBookArgument(parameter4, true)

//        when:
//            def save5 = bd.findMethod("save5", MyBook).get()
//            def parameter5 = save5.getArguments()[0]
//        then:
//            validateMyBookArgument(parameter5)

        when:
            def get = bd.findMethod("get").get()
            def returnType = get.getReturnType().asArgument()
        then:
            def am = returnType.getAnnotationMetadata()
            assert am.hasAnnotation(TypeUseRuntimeAnn.class)
            assert !am.hasAnnotation(MyEntity.class)
            assert !am.hasAnnotation(Introspected.class)
            // + Class annotations
            assert am.hasStereotype(AnnotationUtil.SINGLETON)
            assert am.hasStereotype(Executable)
    }

    @PendingFeature
    void "test how the type annotations from the type are preserved - pending 1"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.context.annotation.Executable
import java.util.List
import io.micronaut.kotlin.processing.beans.executable.*

@jakarta.inject.Singleton
class MyBean {

    @Executable
    fun <T : MyBook> saveAll5(book: List<@TypeUseRuntimeAnn T>) {
    }
}

''')

        when:
            def saveAll5 = bd.findMethod("saveAll5", List).get()
            def listTypeArgument5 = saveAll5.getArguments()[0].getTypeParameters()[0]
        then:
            validateMyBookArgument(listTypeArgument5)

    }

    @PendingFeature
    void "test how the type annotations from the type are preserved - pending 2"() {
        given:
            BeanDefinition bd = buildBeanDefinition('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import io.micronaut.context.annotation.Executable
import java.util.List
import io.micronaut.kotlin.processing.beans.executable.*

@jakarta.inject.Singleton
class MyBean {

    @Executable
    fun <T : MyBook> save5(book: @TypeUseRuntimeAnn T) {
    }
}

''')

        when:
            def save5 = bd.findMethod("save5", MyBook).get()
            def parameter5 = save5.getArguments()[0]
        then:
            validateMyBookArgument(parameter5)
    }

    void validateMyBookArgument(Argument argument, boolean shouldBeNonnull = false) {
        // The argument should only have type annotations and potentially Nonnull
        def am = argument.getAnnotationMetadata()
        assert am.hasAnnotation(TypeUseRuntimeAnn.class)
        assert !am.hasAnnotation(MyEntity.class)
        assert !am.hasAnnotation(Introspected.class)
        if (shouldBeNonnull) {
            assert am.hasAnnotation(Nonnull.class)
            assert am.getAnnotationNames() == [Nonnull.class.name, TypeUseRuntimeAnn.class.name] as Set<String>
        } else {
            assert am.getAnnotationNames() == [TypeUseRuntimeAnn.class.name] as Set<String>
        }
    }
}

