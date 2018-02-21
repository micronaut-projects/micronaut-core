package example.storefront

import example.api.v1.HealthStatus
import example.api.v1.HealthStatusUtils
import example.storefront.ui.NavBar
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

    @Inject
    MailClient mailClient

    @Override
    Single<String> renderPet(String slug) {
        Single<String> container = fetcher.findBySlug(slug)
                .map { PetViewModel x -> htmlRenderer.renderPet(x) }
                .concatWith(requestInfoForm(slug))
                .reduce('', { String s, String s2 -> s + s2 })
        htmlRenderer.renderContainer(NavBar.PET, container)
    }

    Single<String> requestInfoForm(String slug) {
        mailClient.health().onErrorReturn({
            new HealthStatus("DOWN")
        }).map { HealthStatus x ->
                HealthStatusUtils.isUp(x) ? htmlRenderer.renderRequestInfoForm(slug) : ''
            }
    }
}
