package example.storefront

import example.storefront.ui.VendorViewModel
import groovy.transform.CompileStatic
import io.reactivex.Flowable

import javax.inject.Singleton

@Singleton
@CompileStatic
class VendorFetcherImpl implements VendorFetcher {

    List<VendorViewModel> vendorList = [
            new VendorViewModel(name: 'Sergio del Amo', email: 'sergio@email.com', telephone: '1234567'),
            new VendorViewModel(name: 'Graeme Rocher', email: 'graeme@email.com',  telephone: '8925687')
    ]

    @Override
    Flowable<VendorViewModel> fetchVendors() {
        Flowable.fromArray(vendorList as VendorViewModel[])
    }
}
