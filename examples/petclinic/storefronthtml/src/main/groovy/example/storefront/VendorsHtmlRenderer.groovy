package example.storefront

import groovy.transform.CompileStatic
import io.reactivex.Single

@CompileStatic
interface VendorsHtmlRenderer {
    Single<String> renderVendorsTable()
}