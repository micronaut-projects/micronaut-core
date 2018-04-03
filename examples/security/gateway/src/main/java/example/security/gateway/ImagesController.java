package example.security.gateway;

import io.micronaut.discovery.consul.client.v1.ConsulClient;
import io.micronaut.discovery.consul.client.v1.HealthEntry;
import io.micronaut.discovery.consul.client.v1.ServiceEntry;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.reactivex.Flowable;

import javax.inject.Singleton;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Singleton
@Controller("/images")
public class ImagesController {

    protected final ConsulClient consulClient;

    protected ImagesController(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    @Produces("image/png")
    @Get("/{name}.png")
    HttpResponse pngs(String name) {
        return httpResponseWithNameAndSuffix(name, ".png");
    }

    @Produces("image/jpeg")
    @Get("/{name}.jpg")
    HttpResponse jpgs(String name) {
        return httpResponseWithNameAndSuffix(name, ".jpg");
    }

    @Produces("image/gif")
    @Get("/{name}.gif")
    HttpResponse gifs(String name) {
        return httpResponseWithNameAndSuffix(name, ".gif");
    }

    private HttpResponse httpResponseWithNameAndSuffix(String name, String suffix) {
        String url = buildUrl(name, suffix);
        if ( url != null ) {
            return HttpResponse.temporaryRedirect(URI.create(url));
        }
        return HttpResponse.notFound();
    }

    private String buildUrl(String name, String suffix) {
        List<HealthEntry> entries = Flowable.fromPublisher(consulClient.getHealthyServices("books", Optional.of(true), Optional.empty(), Optional.empty())).blockingFirst();
        if ( entries != null ) {
            for ( HealthEntry healthEntry : entries ) {
                ServiceEntry service = healthEntry.getService();
                int port = service.getPort().getAsInt();
                String hostName = service.getAddress().get().getHostName();
                return "http://"+ hostName + ":"+String.valueOf(port)+"/images/"+name+suffix;
            }
        }
        return null;

    }
}
