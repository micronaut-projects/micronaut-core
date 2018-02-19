package example.api.v1;

import io.reactivex.Single;
import org.particleframework.http.annotation.Get;

public interface HealthOperation {
    @Get("/health")
    Single<HealthStatus> health();
}
