package example.storefront

import example.storefront.ui.NavBar
import example.storefront.ui.PetListViewModel
import groovy.transform.CompileStatic
import io.reactivex.Single
import io.reactivex.annotations.NonNull
import io.reactivex.functions.BiFunction
import org.particleframework.context.annotation.Value

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
                .map { PetListViewModel x -> htmlRenderer.renderPetGrid(x) }
                .reduce('<h2>Pets</h2>', { String s, String s2 -> s + s2 })

        htmlRenderer.renderContainer(NavBar.PETS, container)
    }
}
