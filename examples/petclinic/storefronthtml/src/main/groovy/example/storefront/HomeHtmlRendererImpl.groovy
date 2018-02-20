package example.storefront

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

    @Override
    Single<String> render() {
        // TODO replace http://localhost:8092/v1/offers with ${url}/offers
        String homePage = '''
<h1>Offers</h1>
<div id="offers">Loading ...</div>
<script type="text/javascript">
var source = new EventSource("http://localhost:8092/v1/offers"); 
source.onmessage = function(event) {
    console.log(event.data);
    var data = JSON.parse(event.data);
    var html = "<table class='table'><thead><tr><th>Vendor</th><th>Name</th><th>Type</th><th>description</th><th>Price</th></tr></thead><tbody><tr><td>" +  data.pet.vendor + "</td><td>" +  data.pet.name + "</td><td>" +  data.pet.type + "</td><td>" +  data.description + "</td><td>" +  data.price +  data.currency + "</td></tr></tbody></table>";
    document.getElementById("offers").innerHTML = html;                        
};
</script>'''
        htmlRenderer.renderContainer(NavBar.HOME, Single.just(homePage))
    }
}
