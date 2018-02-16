package example.storefront

import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import groovy.transform.CompileStatic
import io.reactivex.Flowable
import io.reactivex.Single

@CompileStatic
interface PetFetcher {
    Flowable<PetListViewModel> fetchPets()
    Single<PetViewModel> findById(Long id)
}