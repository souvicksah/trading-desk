package com.trading.desk.repository;

import com.trading.desk.model.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Exclusive lock on the order row — used by fill/cancel to prevent
     * two concurrent state transitions on the same order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdWithLock(@Param("id") UUID id);

    /** Count used by the "max 3 pending" rule during order placement. */
    @Query("SELECT COUNT(o) FROM Order o WHERE o.trader.id = :traderId AND o.status = 'PENDING'")
    long countPendingByTraderId(@Param("traderId") String traderId);

    List<Order> findByTraderId(String traderId);
}
