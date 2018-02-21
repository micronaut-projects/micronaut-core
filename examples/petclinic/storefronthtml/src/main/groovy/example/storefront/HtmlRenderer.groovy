package example.storefront

import example.storefront.ui.NavBar
import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import groovy.transform.CompileStatic
import io.reactivex.Single

@CompileStatic
interface HtmlRenderer {
    String renderPetCell(PetViewModel pet)
    String renderPetGrid(PetListViewModel petType)
    Single<String> renderContainer(NavBar current, Single<String> container)
    Single<String> container(String placeHolder, String text, Single<String> container)
    String renderPet(PetViewModel petViewModel)
    String renderRequestInfoForm(String slug)
}
