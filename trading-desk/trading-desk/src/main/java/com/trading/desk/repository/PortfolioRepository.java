package com.trading.desk.repository;

import com.trading.desk.model.entity.Portfolio;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioRepository extends JpaRepository<Portfolio, UUID> {

    List<Portfolio> findByTraderId(String traderId);

    Optional<Portfolio> findByTraderIdAndStock(String traderId, String stock);

    /**
     * Exclusive lock on the portfolio row — used when incrementing or
     * decrementing holdings to prevent lost updates under concurrent fills.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Portfolio p WHERE p.trader.id = :traderId AND p.stock = :stock")
    Optional<Portfolio> findByTraderIdAndStockWithLock(
            @Param("traderId") String traderId,
            @Param("stock") String stock);
}
