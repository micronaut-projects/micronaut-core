package example.storefront

import example.api.v1.HealthStatus
import example.api.v1.HealthStatusUtils
import example.storefront.ui.NavBar
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.reactivex.Single
import org.particleframework.context.annotation.Value

import javax.inject.Inject
import javax.inject.Singleton

@Slf4j
@CompileStatic
@Singleton
class HomeHtmlRendererImpl implements HomeHtmlRenderer {

    @Value('${petstore.url}')
    String url

    @Inject
    HtmlRenderer htmlRenderer

    @Inject
    OffersClient offersClient

    @Override
    Single<String> render() {

        String homePage = """
<h1>Offers</h1>
<div id="offers">Loading ...</div>
<script type="text/javascript">
var source = new EventSource('${url}/offers'); 
source.onmessage = function(event) {
    console.log(event.data);
    var data = JSON.parse(event.data);
    var html = "<table class='table'><thead><tr><th>Vendor</th><th>Name</th><th>Type</th><th>description</th><th>Price</th></tr></thead><tbody><tr><td>" +  data.pet.vendor + "</td><td>" + data.pet.name + "</td><td>" +  data.pet.type + "</td><td>" +  data.description + "</td><td>" +  data.price +  data.currency + "</td></tr><tr><td colspan='5'><img src='/assets/images/" + data.pet.image + "' alt='" + data.pet.name + "' class='img-thumbnail'/></td></tr></tbody></table>";
    document.getElementById("offers").innerHTML = html;                        
};
</script>""".toString()
        Single<String> homeSingle = offersClient.health().onErrorReturn({
            new HealthStatus("DOWN")
        }).map { HealthStatus x ->
            HealthStatusUtils.isUp(x) ? homePage : ''
        }
        htmlRenderer.renderContainer(NavBar.HOME, homeSingle)
    }
}
