package example.storefront.ui

import example.api.v1.PetType
import groovy.transform.CompileStatic

@CompileStatic
class PetListViewModel {
    PetType type
    List<PetViewModel> petList
}
