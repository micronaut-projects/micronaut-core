package example.storefront

import example.api.v1.HealthStatusOperation
import groovy.transform.CompileStatic
import org.particleframework.http.client.Client

@CompileStatic
@Client(value = 'offers')
interface OffersClient extends HealthStatusOperation {
}
