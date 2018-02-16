package example.storefront

import example.api.v1.PetType
import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import io.reactivex.Flowable
import io.reactivex.Single

import javax.inject.Singleton
import example.storefront.ui.PetViewModel

@Singleton
class PetFetcherImpl implements PetFetcher {
    List<PetListViewModel> petList = [
            new PetListViewModel(type: PetType.DOG,
                    petList: [
                            new PetViewModel(id: 1, image: "/assets/images/photo-1457914109735-ce8aba3b7a79.jpeg", type: PetType.DOG),
                            new PetViewModel(id: 2, image: "/assets/images/photo-1442605527737-ed62b867591f.jpeg", type: PetType.DOG),
                            new PetViewModel(id: 3, image: "/assets/images/photo-1446231855385-1d4b0f025248.jpeg", type: PetType.DOG),
                    ]),
            new PetListViewModel(type: PetType.CAT,
                    petList: [
                            new PetViewModel(id: 4, image: "/assets/images/photo-1489911646836-b08d6ca60ffe.jpeg", type: PetType.CAT),
                            new PetViewModel(id: 5, image: "/assets/images/photo-1512616643169-0520ad604fc2.jpeg", type: PetType.CAT),
                            new PetViewModel(id: 6, image: "/assets/images/photo-1505481354248-2ba5d3b9338e.jpeg", type: PetType.CAT),
                    ]),]

    @Override
    Flowable<PetListViewModel> fetchPets() {


        Flowable.fromArray(petList as PetListViewModel[])
    }

    @Override
    Single<PetViewModel> findById(Long id) {
        Single.just(findPetViewModelById(id))
    }

    PetViewModel findPetViewModelById(Long id) {
        for ( PetListViewModel petListViewModel : petList) {
            for ( PetViewModel pet : petListViewModel.petList ) {
                if ( pet.id == id ) {
                    return pet
                }
            }
        }
        null
    }
}
