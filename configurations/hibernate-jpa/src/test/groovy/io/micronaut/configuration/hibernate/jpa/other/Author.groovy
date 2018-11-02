package io.micronaut.configuration.hibernate.jpa.other

import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.validation.constraints.NotBlank

@Entity
class Author {

    @Id
    @GeneratedValue
    Long id

    @NotBlank
    String name
}
