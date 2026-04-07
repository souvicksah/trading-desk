package com.trading.desk.integration;

import com.trading.desk.exception.TradingException;
import com.trading.desk.model.dto.request.PlaceOrderRequest;
import com.trading.desk.model.dto.request.AddToPortfolioRequest;
import com.trading.desk.model.entity.Trader;
import com.trading.desk.model.enums.OrderSide;
import com.trading.desk.repository.OrderRepository;
import com.trading.desk.repository.PortfolioRepository;
import com.trading.desk.repository.TraderRepository;
import com.trading.desk.service.OrderService;
import com.trading.desk.service.PortfolioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests that validate concurrency invariants against a real
 * (H2 in-memory) database using actual Spring transactions.
 *
 * <p>These tests prove that the pessimistic-locking strategy prevents
 * the pending-order cap from being violated even under parallel load.
 */
@SpringBootTest
class OrderConcurrencyTest {

    @Autowired private OrderService orderService;
    @Autowired private PortfolioService portfolioService;
    @Autowired private TraderRepository traderRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PortfolioRepository portfolioRepository;

    private static final String TRADER_ID = "CONC_T_" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void createTrader() {
        traderRepository.save(new Trader(TRADER_ID, "Concurrent Trader"));
    }

    @AfterEach
    void cleanup() {
        portfolioRepository.deleteAll(portfolioRepository.findByTraderId(TRADER_ID));
        orderRepository.deleteAll(orderRepository.findByTraderId(TRADER_ID));
        traderRepository.deleteById(TRADER_ID);
    }

    @Test
    void concurrentPlacement_neverExceedsThreePending() throws InterruptedException {
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go    = new CountDownLatch(1);

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final String stock = "STOCK" + i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();  // all threads start simultaneously
                    PlaceOrderRequest req = new PlaceOrderRequest(TRADER_ID, stock, "TECH", 1, OrderSide.BUY);
                    orderService.placeOrder(req);
                    successes.incrementAndGet();
                } catch (TradingException e) {
                    rejections.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        ready.await();  // wait until all threads are poised at the gate
        go.countDown(); // release all at once

        executor.shutdown();
        assertThat(executor.awaitTermination(15, TimeUnit.SECONDS)).isTrue();

        // Exactly 3 should have succeeded; the rest must have been rejected
        assertThat(successes.get()).isEqualTo(3);
        assertThat(rejections.get()).isEqualTo(3);

        // Verify DB state
        long pendingInDb = orderRepository.countPendingByTraderId(TRADER_ID);
        assertThat(pendingInDb).isEqualTo(3);
    }

    @Test
    void concurrentFill_sameOrder_onlyOneSucceeds() throws InterruptedException {
        // Seed portfolio so SELL passes holdings check
        portfolioService.addToPortfolio(TRADER_ID,
                new AddToPortfolioRequest("AAPL", "TECH", 1000));

        // Place a single order
        PlaceOrderRequest req = new PlaceOrderRequest(TRADER_ID, "AAPL", "TECH", 10, OrderSide.BUY);
        UUID orderId = orderService.placeOrder(req).getId();

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);

        AtomicInteger successes  = new AtomicInteger();
        AtomicInteger conflicts  = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    go.await();
                    orderService.fillOrder(orderId);
                    successes.incrementAndGet();
                } catch (TradingException e) {
                    conflicts.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        go.countDown();
        executor.shutdown();
        executor.awaitTermination(15, TimeUnit.SECONDS);

        // Only one fill can succeed for a given order
        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(threads - 1);
    }
}
