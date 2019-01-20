package io.micronaut.http.server.netty.jackson

import com.fasterxml.jackson.annotation.JsonView
import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode
@JsonView(Views.Public)
class TestModel {

    String firstName

    String lastName

    @JsonView(Views.Internal)
    String birthdate

    @JsonView(Views.Admin)
    String password
}
