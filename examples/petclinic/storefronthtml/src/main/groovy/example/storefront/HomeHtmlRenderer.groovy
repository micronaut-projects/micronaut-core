package example.storefront

import groovy.transform.CompileStatic
import io.reactivex.Single

@CompileStatic
interface HomeHtmlRenderer {

    Single<String> render()
}