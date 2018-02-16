package example.storefront

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class VendorHtmlRendererImplSpec extends Specification {

    @Subject
    @Shared
    VendorsHtmlRendererImpl vendorsHtmlRenderer = new VendorsHtmlRendererImpl()

    def "renderTable returns a string which contains placeholder"() {
        expect:
        vendorsHtmlRenderer.renderTable().contains(vendorsHtmlRenderer.tableplaceHolder)
    }
}
