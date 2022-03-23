package io.micronaut.core.beans

import io.micronaut.core.annotation.Introspected

@Introspected
class TestEntity(
        var id: Long,
        var name: String,
        var getSurname: String,
        var isDeleted: Boolean,
        val isImportant: Boolean,
        var corrected: Boolean,
        val upgraded: Boolean,
) {
    val isMyBool: Boolean
        get() = false
    var isMyBool2: Boolean
        get() = false
        set(v) {}
    var myBool3: Boolean
        get() = false
        set(v) {}
    val myBool4: Boolean
        get() = false
    var myBool5: Boolean = false
}