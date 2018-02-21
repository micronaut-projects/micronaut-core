package example.storefront

import example.storefront.ui.NavBar
import example.storefront.ui.PetListViewModel
import groovy.transform.CompileStatic
import io.reactivex.Single
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@CompileStatic
class PetGridHtmlRendererImpl implements PetGridHtmlRenderer {

    @Inject
    PetFetcher fetcher

    @Inject
    HtmlRenderer htmlRenderer

    @Override
    Single<String> renderPetGrid() {
        Single<String> container = fetcher.fetchPets()
                .map { List<PetListViewModel> l ->
            l.collect { x -> htmlRenderer.renderPetGrid(x) }.join('')
        }
        htmlRenderer.renderContainer(NavBar.PETS, container)
    }
}
