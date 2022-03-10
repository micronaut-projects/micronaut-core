package io.micronaut.kotlin.processing.aop.introduction

import jakarta.inject.Singleton

@Singleton
class InjectParentInterface(parentInterface: ParentInterface<*>)
