package example.storefront

import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import groovy.transform.CompileStatic
import io.reactivex.Flowable
import io.reactivex.Single

@CompileStatic
interface PetFetcher {
    Single<List<PetListViewModel>> fetchPets()
    Single<PetViewModel> findBySlug(String slug)
}