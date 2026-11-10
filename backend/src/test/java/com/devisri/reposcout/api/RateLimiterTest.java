package com.devisri.reposcout.api;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000);

    @Test
    void limitsEachVisitorSeparately() {
        RateLimiter limiter = new RateLimiter(2, 100, now::get);

        limiter.check("1.1.1.1");
        limiter.check("1.1.1.1");
        assertThatThrownBy(() -> limiter.check("1.1.1.1"))
                .isInstanceOf(RateLimiter.LimitReachedException.class)
                .hasMessageContaining("2 questions in the last hour");
        assertThatCode(() -> limiter.check("2.2.2.2")).doesNotThrowAnyException();
    }

    @Test
    void capsTheWholeServer() {
        RateLimiter limiter = new RateLimiter(10, 3, now::get);

        limiter.check("a");
        limiter.check("b");
        limiter.check("c");
        assertThatThrownBy(() -> limiter.check("d")).hasMessageContaining("very busy");
    }

    @Test
    void allowsQuestionsAgainAfterAnHour() {
        RateLimiter limiter = new RateLimiter(1, 100, now::get);
        limiter.check("1.1.1.1");
        assertThatThrownBy(() -> limiter.check("1.1.1.1")).isInstanceOf(RateLimiter.LimitReachedException.class);

        now.addAndGet(60 * 60 * 1000L);

        assertThatCode(() -> limiter.check("1.1.1.1")).doesNotThrowAnyException();
    }
}
