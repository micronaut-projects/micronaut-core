package io.micronaut.http.client;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;

class RequestTimeoutsTest {

    @Test
    void theTimeoutsMustBePositive() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> RequestTimeouts.builder().readTimeout(Duration.ZERO));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RequestTimeouts.builder().readTimeout(Duration.ofSeconds(-1)));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RequestTimeouts.builder().requestTimeout(Duration.ZERO));
        Assertions.assertThrows(IllegalArgumentException.class, () -> RequestTimeouts.builder().requestTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void theDefaultTakesEveryTimeoutFromTheConfiguration() {
        Assertions.assertNull(RequestTimeouts.getDefault().getReadTimeout());
        Assertions.assertNull(RequestTimeouts.getDefault().getRequestTimeout());
        Assertions.assertEquals(RequestTimeouts.getDefault(), RequestTimeouts.builder().build());
    }

    @Test
    void toBuilderCopiesTheTimeouts() {
        RequestTimeouts timeouts = RequestTimeouts.builder()
            .readTimeout(Duration.ofSeconds(5))
            .requestTimeout(Duration.ofSeconds(10))
            .build();
        Assertions.assertEquals(timeouts, timeouts.toBuilder().build());
        Assertions.assertEquals(timeouts.hashCode(), timeouts.toBuilder().build().hashCode());
        Assertions.assertNotEquals(timeouts, timeouts.toBuilder().readTimeout(null).build());
    }

    @Test
    void theTimeoutsAreAttributesOfTheRequest() {
        MutableHttpRequest<?> request = HttpRequest.GET("/");
        Assertions.assertTrue(ClientAttributes.getTimeouts(request).isEmpty());
        RequestTimeouts timeouts = RequestTimeouts.builder().readTimeout(Duration.ofSeconds(5)).build();
        ClientAttributes.setTimeouts(request, timeouts);
        Assertions.assertSame(timeouts, ClientAttributes.getTimeouts(request).orElseThrow());
        Assertions.assertEquals(Duration.ofSeconds(5), AbstractHttpClient.readTimeoutOverride(request));
    }

    @Test
    void theReadIdleTimeoutOfARawExchangeTakesPrecedence() {
        MutableHttpRequest<?> request = HttpRequest.GET("/");
        Assertions.assertNull(AbstractHttpClient.readTimeoutOverride(request));
        ClientAttributes.setTimeouts(request, RequestTimeouts.builder().readTimeout(Duration.ofSeconds(5)).build());
        request.setAttribute(AbstractHttpClient.READ_IDLE_TIMEOUT, Duration.ofMinutes(5));
        Assertions.assertEquals(Duration.ofMinutes(5), AbstractHttpClient.readTimeoutOverride(request));
    }
}
