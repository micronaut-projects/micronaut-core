package example.storefront

import example.storefront.ui.NavBar
import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import groovy.transform.CompileStatic
import io.reactivex.Single

import javax.inject.Inject
import javax.inject.Singleton

@CompileStatic
@Singleton
class PetHtmlRendererImpl implements PetHtmlRenderer {

    @Inject
    PetFetcher fetcher

    @Inject
    HtmlRenderer htmlRenderer

    @Override
    Single<String> renderPet(Long id) {
        Single<String> container = fetcher.findById(id)
                .map { PetViewModel x -> htmlRenderer.renderPet(x) }
        htmlRenderer.renderContainer(NavBar.PET, container)
    }
}
