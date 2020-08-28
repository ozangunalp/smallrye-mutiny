package io.smallrye.mutiny.operators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import io.smallrye.mutiny.TestException;
import io.smallrye.mutiny.Uni;

public class UniOnFailureRetryUntilTest {

    private final Predicate<Throwable> retryTwice = new Predicate<Throwable>() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public boolean test(Throwable throwable) {
            int attempt = counter.getAndIncrement();
            return attempt < 2;
        }
    };

    private final Predicate<Throwable> retryOnIoException = throwable -> throwable instanceof IOException;

    @Test
    public void testWithoutFailure() {
        Uni<Integer> upstream = Uni.createFrom().item(1);
        int result = upstream
                .onFailure().retry().until(t -> true)
                .await().indefinitely();
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void testInfiniteRetry() {
        AtomicInteger count = new AtomicInteger();
        Uni<Integer> upstream = Uni.createFrom().emitter(em -> {
            int i = count.incrementAndGet();
            if (i == 1) {
                em.fail(new Exception("boom"));
                return;
            }
            em.complete(1);
        });

        int result = upstream
                .onFailure().retry().until(t -> true)
                .await().indefinitely();
        assertThat(result).isEqualTo(1);
    }

    @Test
    public void testTwoRetriesAndGiveUp() {
        assertThrows(TestException.class, () -> {
            Uni<Integer> upstream = Uni.createFrom().emitter(em -> em.fail(new TestException("boom")));
            upstream
                    .onFailure().retry().until(retryTwice)
                    .await().indefinitely();
        });
    }

    @Test
    public void testRetryOnSpecificException() {
        AtomicInteger count = new AtomicInteger();
        Uni<Integer> upstream = Uni.createFrom().emitter(em -> {
            int attempt = count.incrementAndGet();
            if (attempt == 1) {
                em.fail(new IOException("boom"));
            }
            em.complete(2);
        });

        int result = upstream
                .onFailure().retry().until(retryOnIoException).await().indefinitely();
        assertThat(result).isEqualTo(2);
    }

    @Test
    public void testRetryOnSpecificExceptionAndNotOther() {
        assertThrows(IllegalStateException.class, () -> {
            final IOException exception = new IOException("boom");
            final IllegalStateException ise = new IllegalStateException("kaboom");

            AtomicInteger count = new AtomicInteger();
            Uni<Integer> upstream = Uni.createFrom().emitter(em -> {
                int attempt = count.incrementAndGet();
                if (attempt == 1) {
                    em.fail(exception);
                }
                em.fail(ise);
            });

            upstream
                    .onFailure().retry().until(retryOnIoException)
                    .await().indefinitely();
        });
    }

    @Test
    public void testWithPredicateThrowingException() {
        assertThrows(IllegalStateException.class, () -> {
            AtomicInteger count = new AtomicInteger();
            Uni<Integer> upstream = Uni.createFrom().emitter(em -> {
                int i = count.incrementAndGet();
                if (i == 1) {
                    em.fail(new Exception("boom"));
                    return;
                }
                em.complete(3);
            });

            upstream
                    .onFailure().retry().until(t -> {
                        throw new IllegalStateException("boom");
                    })
                    .await().indefinitely();
        });
    }

    @Test
    public void testWithPredicateReturningFalse() {
        assertThrows(TestException.class, () -> {
            AtomicInteger count = new AtomicInteger();
            Uni<Integer> upstream = Uni.createFrom().emitter(em -> {
                int i = count.incrementAndGet();
                if (i == 1) {
                    em.fail(new TestException("boom"));
                    return;
                }
                em.complete(2);
            });

            upstream
                    .onFailure().retry().until(t -> false)
                    .await().indefinitely();
        });
    }

    @Test
    public void testWithPredicateThrowException() {
        assertThrows(RuntimeException.class, () -> {
            AtomicInteger count = new AtomicInteger();
            Uni<Integer> upstream = Uni.createFrom().emitter(em -> {
                int i = count.incrementAndGet();
                if (i == 1) {
                    em.fail(new Exception("boom"));
                    return;
                }
                em.complete(2);
            });
            upstream
                    .onFailure().retry().until(t -> {
                        throw new RuntimeException("kaboom");
                    })
                    .await().indefinitely();
        });
    }

    @Test
    public void testJitterValidation() {
        assertThrows(IllegalArgumentException.class, () -> Uni.createFrom().item(1)
                .onFailure().retry().withJitter(2));
    }

    @Test
    public void testThatYouCannotUseUntilIfBackoffIsConfigured() {
        assertThrows(IllegalArgumentException.class, () -> Uni.createFrom().item("hello")
                .onFailure().retry().withBackOff(Duration.ofSeconds(1)).until(t -> true));
    }

}
