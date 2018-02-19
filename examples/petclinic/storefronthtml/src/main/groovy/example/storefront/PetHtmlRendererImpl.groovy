package example.storefront

import example.api.v1.HealthStatus
import example.api.v1.HealthStatusUtils
import example.storefront.ui.NavBar
import example.storefront.ui.PetListViewModel
import example.storefront.ui.PetViewModel
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.reactivex.Single
import io.reactivex.annotations.NonNull
import io.reactivex.functions.Function
import org.particleframework.http.HttpResponse
import org.particleframework.http.client.exceptions.HttpClientException

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
    Single<String> renderPet(Long id) {
        Single<String> container = fetcher.findById(id)
                .map { PetViewModel x -> htmlRenderer.renderPet(x) }
                .concatWith(requestInfoForm(id))
                .reduce('', { String s, String s2 -> s + s2 })
        htmlRenderer.renderContainer(NavBar.PET, container)
    }

    Single<String> requestInfoForm(Long petId) {
        mailClient.health().map { HealthStatus x ->
                HealthStatusUtils.isUp(x) ? htmlRenderer.renderRequestInfoForm(petId) : ''
        }.onErrorReturn { throwable ->
            ''
        }
    }
}
