package io.micronaut.validation.validator

import io.micronaut.core.type.Argument
import io.micronaut.serde.Serde
import jakarta.inject.Singleton

@Singleton
// Validate that the processor doesn't fail
class ByteArraySerde: Serde<ByteArray> {
    override fun deserialize(
        type: Argument<in ByteArray>?
    ): ByteArray = ByteArray(0)

    override fun serialize(
        type: Argument<out ByteArray>?,
        value: ByteArray
    ) {
    }
}
