package io.micronaut.docs.server.suspend.multiple

import kotlinx.coroutines.flow.Flow

interface CoroutineCrudRepository<E, ID> {

    /**
     * Saves the given valid entity, returning a possibly new entity representing the saved state. Note that certain implementations may not be able to detect whether a save or update should be performed and may always perform an insert. The [.update] method can be used in this case to explicitly request an update.
     *
     * @param entity The entity to save. Must not be null.
     * @return The saved entity will never be null.
     * @param <S> The generic type
     */
    suspend fun <S : E> save(entity: S): S

    /**
     * This method issues an explicit update for the given entity. The method differs from [.save] in that an update will be generated regardless if the entity has been saved previously or not. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entity The entity to save. Must not be null.
     * @return The updated entity will never be null.
     * @param <S> The generic type
     */
    suspend fun <S : E> update(entity: S): S

    /**
     * This method issues an explicit update for the given entities. The method differs from [.saveAll] in that an update will be generated regardless if the entity has been saved previously or not. If the entity has no assigned ID then an exception will be thrown.
     *
     * @param entities The entities to update. Must not be null.
     * @return The updated entities will never be null.
     * @param <S> The generic type
     */
    fun <S : E> updateAll(entities: Iterable<S>): Flow<S>

    /**
     * Saves all given entities, possibly returning new instances representing the saved state.
     *
     * @param entities The entities to save. Must not be null.
     * @param <S> The generic type
     * @return The saved entities objects. will never be null.
     */
    fun <S : E> saveAll(entities: Iterable<S>): Flow<S>

    /**
     * Retrieves an entity by its id.
     *
     * @param id The ID of the entity to retrieve. Must not be null.
     * @return the entity with the given id or none.
     */
    suspend fun findById(id: ID): E?

    /**
     * Returns whether an entity with the given id exists.
     *
     * @param id must not be null.
     * @return true if an entity with the given id exists, false otherwise.
     */
    suspend fun existsById(id: ID): Boolean

    /**
     * Returns all instances of the type.
     *
     * @return all entities
     */
    fun findAll(): Flow<E>

    /**
     * Returns the number of entities available.
     *
     * @return the number of entities
     */
    suspend fun count(): Long

    /**
     * Deletes the entity with the given id.
     *
     * @param id the id.
     */
    suspend fun deleteById(id: ID): Int

    /**
     * Deletes a given entity.
     *
     * @param entity The entity to delete
     * @return the number of entities deleted
     */
    suspend fun delete(entity: E): Int

    /**
     * Deletes the given entities.
     *
     * @param entities The entities to delete
     * @return the number of entities deleted
     */
    suspend fun deleteAll(entities: Iterable<E>): Int

    /**
     * Deletes all entities managed by the repository.
     * @return the number of entities deleted
     */
    suspend fun deleteAll(): Int
}
