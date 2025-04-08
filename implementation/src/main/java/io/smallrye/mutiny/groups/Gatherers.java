package io.smallrye.mutiny.groups;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.smallrye.mutiny.tuples.Tuple2;

public interface Gatherers {

    static <I, ACC, O> Gatherer<I, ACC, O> of(Supplier<ACC> initialAccumulatorSupplier,
            BiFunction<ACC, I, ACC> accumulatorFunction,
            BiFunction<ACC, Boolean, Optional<Tuple2<ACC, O>>> extractor,
            Function<ACC, Optional<O>> finalizer) {
        return new DefaultGatherer<>(1, initialAccumulatorSupplier, accumulatorFunction, extractor, finalizer);
    }

    static <I, ACC, O> Gatherer<I, ACC, O> of(int concurrency, Supplier<ACC> initialAccumulatorSupplier,
            BiFunction<ACC, I, ACC> accumulatorFunction,
            BiFunction<ACC, Boolean, Optional<Tuple2<ACC, O>>> extractor,
            Function<ACC, Optional<O>> finalizer) {
        return new DefaultGatherer<>(concurrency, initialAccumulatorSupplier, accumulatorFunction, extractor, finalizer);
    }

    static <I> Gatherer<I, I, I> scan(Supplier<I> initialAccumulatorSupplier, BiFunction<I, I, I> accumulatorFunction) {
        return of(initialAccumulatorSupplier, accumulatorFunction,
                (acc, done) -> done ? Optional.empty() : Optional.of(Tuple2.of(acc, acc)), Optional::of);
    }

    static <I> Gatherer<I, I, I> fold(Supplier<I> initialAccumulatorSupplier, BiFunction<I, I, I> accumulatorFunction) {
        return of(initialAccumulatorSupplier, accumulatorFunction, (acc, done) -> Optional.empty(), Optional::of);
    }

    static <I> Gatherer<I, List<I>, List<I>> window(int size) {
        return of(ArrayList::new, (acc, next) -> {
            acc.add(next);
            return acc;
        }, (acc, done) -> {
            if (acc.size() == size) {
                return Optional.of(Tuple2.of(new ArrayList<>(), new ArrayList<>(acc)));
            }
            return Optional.empty();
        }, acc -> acc.isEmpty()
                ? Optional.empty()
                : Optional.of(acc));
    }

    static <I> Gatherer<I, List<I>, List<I>> windowSliding(int size) {
        return of(ArrayList::new, (acc, item) -> {
            acc.add(item);
            return acc;
        }, (acc, done) -> {
            if (acc.size() == size) {
                return Optional.of(Tuple2.of(acc.stream().skip(1).collect(Collectors.toList()), new ArrayList<>(acc)));
            }
            return Optional.empty();
        }, acc -> acc.isEmpty()
                ? Optional.empty()
                : Optional.of(acc));
    }

    class DefaultGatherer<I, ACC, O> implements Gatherer<I, ACC, O> {

        private final int concurrency;
        private final Supplier<ACC> initialAccumulatorSupplier;
        private final BiFunction<ACC, I, ACC> accumulatorFunction;
        private final BiFunction<ACC, Boolean, Optional<Tuple2<ACC, O>>> extractor;
        private final Function<ACC, Optional<O>> finalizer;

        public DefaultGatherer(int concurrency,
                Supplier<ACC> initialAccumulatorSupplier,
                BiFunction<ACC, I, ACC> accumulatorFunction,
                BiFunction<ACC, Boolean, Optional<Tuple2<ACC, O>>> extractor,
                Function<ACC, Optional<O>> finalizer) {
            this.concurrency = concurrency;
            this.initialAccumulatorSupplier = initialAccumulatorSupplier;
            this.accumulatorFunction = accumulatorFunction;
            this.extractor = extractor;
            this.finalizer = finalizer;
        }

        @Override
        public int concurrency() {
            return concurrency;
        }

        @Override
        public ACC accumulator() {
            return initialAccumulatorSupplier.get();
        }

        @Override
        public ACC accumulate(ACC accumulator, I item) {
            return accumulatorFunction.apply(accumulator, item);
        }

        @Override
        public Optional<Tuple2<ACC, O>> extract(ACC accumulator, boolean done) {
            return extractor.apply(accumulator, done);
        }

        @Override
        public Optional<O> finalize(ACC accumulator) {
            return finalizer.apply(accumulator);
        }
    }

    /**
     * Creates a new {@link Gatherer} builder.
     *
     * @param <I> the type of the items emitted by the upstream
     * @return the builder
     */
    static <I> Gatherer.Builder<I> builder() {
        return new Gatherer.Builder<>();
    }

}
