package com.coupons.repository;

import com.coupons.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /**
     * Atomically consumes one use of the coupon.
     *
     * <p>The whole check-and-increment happens in a single conditional {@code UPDATE}, so it is safe
     * under concurrency: the database serializes the row updates and the {@code current_uses < max_uses}
     * predicate guarantees the limit is never exceeded — this is the "first come, first served" rule.
     *
     * @return {@code 1} if a use was consumed, {@code 0} if the coupon is already exhausted.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Coupon c
               SET c.currentUses = c.currentUses + 1
             WHERE c.id = :id
               AND c.currentUses < c.maxUses
            """)
    int tryConsume(@Param("id") Long id);
}
