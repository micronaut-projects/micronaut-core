package example.storefront

import example.storefront.ui.NavBar
import groovy.transform.CompileStatic
import io.reactivex.Single

import javax.inject.Inject
import javax.inject.Singleton

@CompileStatic
@Singleton
class HomeHtmlRendererImpl implements HomeHtmlRenderer {

    @Inject
    HtmlRenderer htmlRenderer

    @Override
    Single<String> render() {
        htmlRenderer.renderContainer(NavBar.HOME, Single.just(''))
    }
}
