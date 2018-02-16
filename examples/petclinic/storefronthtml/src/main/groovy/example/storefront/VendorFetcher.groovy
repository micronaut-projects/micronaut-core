package example.storefront

import example.storefront.ui.VendorViewModel
import groovy.transform.CompileStatic
import io.reactivex.Flowable

@CompileStatic
interface VendorFetcher {
    Flowable<VendorViewModel> fetchVendors()
}
