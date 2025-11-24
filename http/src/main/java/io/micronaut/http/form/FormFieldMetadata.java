package io.micronaut.http.form;

import io.micronaut.http.MediaType;

public record FormFieldMetadata(
    String name,
    String fileName,
    MediaType mediaType
) {
}
