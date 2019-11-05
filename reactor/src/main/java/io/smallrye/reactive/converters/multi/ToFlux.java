package io.smallrye.reactive.converters.multi;

import java.util.function.Function;

import io.smallrye.reactive.Multi;
import reactor.core.publisher.Flux;

public class ToFlux<T> implements Function<Multi<T>, Flux<T>> {

    public final static ToFlux INSTANCE = new ToFlux();

    private ToFlux() {
        // Avoid direct instantiation
    }

    @Override
    public Flux<T> apply(Multi<T> multi) {
        return Flux.from(multi);
    }
}
