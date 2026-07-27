package com.coupons;

import com.coupons.domain.Coupon;
import com.coupons.repository.CouponRedemptionRepository;
import com.coupons.repository.CouponRepository;
import com.coupons.service.CouponService;
import com.coupons.service.exception.CouponExhaustedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the "first come, first served" guarantee: when far more concurrent redeem requests arrive
 * than the coupon allows, exactly {@code maxUses} succeed and the rest are rejected as exhausted —
 * with no over-redemption. This is the core correctness property in a multi-threaded environment.
 */
class CouponConcurrencyIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    CouponService couponService;
    @Autowired
    CouponRepository couponRepository;
    @Autowired
    CouponRedemptionRepository redemptionRepository;

    @BeforeEach
    void cleanUp() {
        redemptionRepository.deleteAll();
        couponRepository.deleteAll();
    }

    @Test
    void neverExceedsMaxUsesUnderConcurrency() throws Exception {
        int maxUses = 50;
        int concurrentRequests = 300;
        int threads = 64;
        couponService.createCoupon("RUSH", maxUses, "PL");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger exhausted = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        // A single start gate: every task blocks on the same latch, then they are released together
        // so the redeems actually contend. (A CyclicBarrier sized to all requests would deadlock
        // against a smaller pool — the pool can never present that many parties at once.)
        CountDownLatch startGate = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            tasks.add(() -> {
                startGate.await();
                try {
                    couponService.redeem("RUSH", "PL", null);
                    successes.incrementAndGet();
                } catch (CouponExhaustedException expected) {
                    exhausted.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }
        startGate.countDown(); // release all workers
        pool.shutdown();
        pool.awaitTermination(60, TimeUnit.SECONDS);
        for (Future<Void> future : futures) {
            future.get(); // surface any unexpected exception
        }

        Coupon coupon = couponRepository.findByCode("RUSH").orElseThrow();
        assertThat(successes.get()).isEqualTo(maxUses);
        assertThat(exhausted.get()).isEqualTo(concurrentRequests - maxUses);
        assertThat(coupon.getCurrentUses()).isEqualTo(maxUses);
    }
}
