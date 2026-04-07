package com.trading.desk.repository;

import com.trading.desk.model.entity.Trader;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TraderRepository extends JpaRepository<Trader, String> {

    /**
     * Acquires a row-level exclusive lock on the trader row.
     * Used during order placement so that the pending-count check and
     * order insertion are serialised per trader — preventing two concurrent
     * requests from both passing the "< 3 pending" guard.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Trader t WHERE t.id = :id")
    Optional<Trader> findByIdWithLock(@Param("id") String id);
}
