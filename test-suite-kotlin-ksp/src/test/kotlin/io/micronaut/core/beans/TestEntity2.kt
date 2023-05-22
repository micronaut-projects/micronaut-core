package io.micronaut.core.beans

import io.micronaut.core.annotation.Introspected

@Introspected
class TestEntity2(id: Long, name: String, getSurname: String, isDeleted: Boolean, isImportant: Boolean, corrected: Boolean, upgraded: Boolean) : AbstractTestEntity(id, name, getSurname, isDeleted, isImportant, corrected, upgraded) {
}