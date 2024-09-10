package io.micronaut.views;

import com.fasterxml.jackson.annotation.JsonView;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Controller("/views")
public class UserController {

    private static final User USER = new User(1, "Joe", "secret");

    @JsonView(Views.Public.class)
    @Get("/pojo")
    public User getUserPojo() {
        return USER;
    }

    @JsonView(Views.Public.class)
    @Get("/list")
    public List<User> getUserList() {
        return List.of(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/optional")
    public Optional<User> getUserOptional() {
        return Optional.of(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/mono")
    public Mono<User> getUserMono() {
        return Mono.just(USER);
    }

    @JsonView(Views.Public.class)
    @Get("/flux")
    public Flux<User> getUserFlux() {
        return Flux.just(USER);
    }
}
