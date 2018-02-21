package example.storefront

import example.api.v1.PetType
import example.storefront.ui.PetListViewModel
import example.storefront.client.v1.Pet
import example.storefront.client.v1.PetClient
import io.reactivex.Maybe
import io.reactivex.Single
import javax.inject.Singleton
import example.storefront.ui.PetViewModel

@Singleton
class PetFetcherImpl implements PetFetcher {

    final PetClient petClient

    PetFetcherImpl(PetClient petClient) {
        this.petClient = petClient
    }

    @Override
    Single<List<PetListViewModel>> fetchPets() {
        petClient.list().map { List<Pet> petList ->
            Set<PetType> petTypes = petList*.type
            petTypes.collect { PetType type ->
                List<PetViewModel> petViewModelList = petList.findAll { Pet pet -> pet.type == type }.collect { Pet pet -> pet
                    new PetViewModel(slug: pet.slug, type: pet.type, image: pet.image)
                }
                new PetListViewModel(type: type, petList: petViewModelList)
            }
        }
    }

    @Override
    Single<PetViewModel> findBySlug(String slug) {
        Maybe<Pet> petMaybe = petClient.find(slug)
        petMaybe.toSingle().map { Pet pet ->
            new PetViewModel(slug: pet.slug, type: pet.type, image: pet.image)
        }
    }
}
