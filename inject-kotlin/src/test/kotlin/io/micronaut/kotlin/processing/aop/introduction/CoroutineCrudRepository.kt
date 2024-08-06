package io.micronaut.kotlin.processing.aop.introduction

import kotlinx.coroutines.flow.Flow

interface CoroutineCrudRepository<E, ID> {

    suspend fun <S : E> save(entity: S): S

    suspend fun <S : E> update(entity: S): S

    fun <S : E> updateAll(entities: Iterable<S>): Flow<S>

    fun <S : E> saveAll(entities: Iterable<S>): Flow<S>

    suspend fun findById(id: ID): E?

    suspend fun existsById(id: ID): Boolean

    fun findAll(): Flow<E>

    suspend fun count(): Long

    suspend fun deleteById(id: ID): Int

    suspend fun delete(entity: E): Int

    suspend fun deleteAll(entities: Iterable<E>): Int

    suspend fun deleteAll(): Int
}
