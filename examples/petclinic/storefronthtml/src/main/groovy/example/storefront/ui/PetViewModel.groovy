package example.storefront.ui

import example.api.v1.PetType
import groovy.transform.CompileStatic
import groovy.transform.ToString

@ToString
@CompileStatic
class PetViewModel {
    String slug
    String image
    PetType type
}
