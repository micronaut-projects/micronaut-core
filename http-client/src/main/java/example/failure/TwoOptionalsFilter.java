package example.failure;

import io.micronaut.context.annotation.Value;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.ClientFilterChain;
import io.micronaut.http.filter.HttpClientFilter;
import org.reactivestreams.Publisher;

import java.util.Optional;
import javax.inject.Inject;

@Filter("/**")
public class TwoOptionalsFilter implements HttpClientFilter {

    @Inject
    public TwoOptionalsFilter(
            @Value("${test.property1}") Optional<String> p1,
            @Value("${test.property2}") Optional<String> p2
    ) {
    }

    @Override
    public Publisher<? extends HttpResponse<?>> doFilter(MutableHttpRequest<?> targetRequest,
            ClientFilterChain chain) {

        return chain.proceed(targetRequest);
    }
}