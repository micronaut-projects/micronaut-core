@file:JvmName("KotlinUtilsNative")
package io.micronaut.core.util

import java.lang.reflect.Method
import kotlin.reflect.jvm.kotlinFunction
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.reflect.KClass

internal fun Method.isKotlinSuspendingFunction(): Boolean =
    kotlinFunction?.isSuspend ?: false

internal fun Any?.isKotlinCoroutineSuspended(): Boolean =
    this == COROUTINE_SUSPENDED

internal fun Method.isKotlinFunctionReturnTypeUnit(): Boolean =
    kotlinFunction?.returnType.let { rt ->
         (rt?.classifier as? KClass<*>)?.java ?: returnType
    } == Unit::class.java
