package example.storefront

import example.storefront.ui.NavBar
import example.storefront.ui.VendorViewModel
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.MarkupBuilder
import io.reactivex.Single

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@CompileStatic
class VendorsHtmlRendererImpl implements VendorsHtmlRenderer {
    String tableplaceHolder = '{{rows}}'

    @Inject
    VendorFetcher vendorFetcher

    @Inject
    HtmlRenderer htmlRenderer

    @Override
    Single<String> renderVendorsTable() {

        String tableText = renderTable()
        Single<String> tableContainer = vendorFetcher.fetchVendors()
                .map { VendorViewModel x -> renderVendorRow(x) }
                .reduce('<h2>Vendors</h2>', { String s, String s2 -> s + s2 })

        Single<String> container = htmlRenderer.container(tableplaceHolder, tableText, tableContainer)
        htmlRenderer.renderContainer(NavBar.VENDORS, container)
    }

    @CompileDynamic
    String renderTable() {
        StringWriter writer = new StringWriter()
        MarkupBuilder html = new MarkupBuilder(writer)
        html.table(class:"table") {
            thead {
                tr {
                    th(scope:"col", 'Name')
                    th(scope:"col", 'Telephone')
                    th(scope:"col", 'Email')
                }
            }
            tbody {
                mkp.yieldUnescaped tableplaceHolder
            }

        }
        writer.toString()
    }

    @CompileDynamic
    String renderVendorRow(VendorViewModel vendor) {
        StringWriter writer = new StringWriter()
        MarkupBuilder html = new MarkupBuilder(writer)
        html.tr {
            td {
                mkp.yield vendor.name
            }
            td {
                mkp.yield vendor.telephone
            }
            td {
                mkp.yield vendor.email
            }
        }
        writer.toString()
    }

}
