package com.trading.desk.service;

import com.trading.desk.exception.TradingException;
import com.trading.desk.model.dto.request.PlaceOrderRequest;
import com.trading.desk.model.dto.response.OrderResponse;
import com.trading.desk.model.entity.Order;
import com.trading.desk.model.entity.Trader;
import com.trading.desk.model.enums.OrderSide;
import com.trading.desk.model.enums.OrderStatus;
import com.trading.desk.repository.OrderRepository;
import com.trading.desk.repository.TraderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    static final int MAX_PENDING_ORDERS = 3;

    private final OrderRepository orderRepository;
    private final TraderRepository traderRepository;
    private final PortfolioService portfolioService;

    public OrderService(OrderRepository orderRepository,
                        TraderRepository traderRepository,
                        PortfolioService portfolioService) {
        this.orderRepository = orderRepository;
        this.traderRepository = traderRepository;
        this.portfolioService = portfolioService;
    }

    /**
     * Places a new order.
     *
     * <p>Concurrency strategy: the trader row is locked with PESSIMISTIC_WRITE
     * for the duration of this transaction. This serialises all concurrent
     * "place order" requests for the same trader, ensuring the pending-count
     * check and the INSERT are atomic — no two threads can both observe
     * "count < 3" and both succeed beyond the limit.
     *
     * <p>For SELL orders the check uses current committed holdings. The fill
     * path also validates holdings under a portfolio row lock, providing
     * defence-in-depth against rare edge cases where multiple pending SELLs
     * are placed against the same stock before any fill occurs.
     */
    @Transactional
    public OrderResponse placeOrder(PlaceOrderRequest request) {
        // Lock trader row — serialises concurrent placements for this trader
        Trader trader = traderRepository.findByIdWithLock(request.getTraderId())
                .orElseThrow(() -> new TradingException(
                        "Trader not found: " + request.getTraderId(), HttpStatus.NOT_FOUND));
        long pendingCount = orderRepository.countPendingByTraderId(request.getTraderId());
        if (pendingCount >= MAX_PENDING_ORDERS) {
            throw new TradingException(
                    "Trader " + request.getTraderId() + " already has " + MAX_PENDING_ORDERS +
                    " pending orders. Cancel or fill existing orders first.",
                    HttpStatus.CONFLICT);
        }

        if (request.getSide() == OrderSide.SELL) {
            int holdings = portfolioService.getHoldings(request.getTraderId(), request.getStock());
            if (holdings < request.getQuantity()) {
                throw new TradingException(
                        "Insufficient holdings for SELL. Trader holds " + holdings +
                        " share(s) of " + request.getStock() +
                        " but requested to sell " + request.getQuantity() + ".",
                        HttpStatus.BAD_REQUEST);
            }
        }

        Order order = new Order();
        order.setTrader(trader);
        order.setStock(request.getStock().toUpperCase());
        order.setSector(request.getSector().toUpperCase());
        order.setQuantity(request.getQuantity());
        order.setSide(request.getSide());
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());

        order = orderRepository.save(order);
        log.info("Order placed id={} trader={} side={} stock={} qty={}",
                order.getId(), trader.getId(), order.getSide(), order.getStock(), order.getQuantity());

        return toResponse(order);
    }

    /**
     * Fills a PENDING order and updates the trader's portfolio.
     *
     * <p>The order row is locked with PESSIMISTIC_WRITE so concurrent fill
     * requests on the same order are serialised — only the first one will see
     * status=PENDING; the second will receive a clear conflict error.
     */
    @Transactional
    public OrderResponse fillOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new TradingException("Order not found: " + orderId, HttpStatus.NOT_FOUND));

        requireStatus(order, OrderStatus.PENDING, "fill");

        order.setStatus(OrderStatus.FILLED);

        String traderId = order.getTrader().getId();
        if (order.getSide() == OrderSide.BUY) {
            portfolioService.incrementHoldings(traderId, order.getStock(), order.getSector(), order.getQuantity());
        } else {
            portfolioService.decrementHoldings(traderId, order.getStock(), order.getQuantity());
        }
        //order = orderRepository.saveAndFlush(order);
        log.info("Order filled id={} trader={} side={} stock={} qty={}",
                order.getId(), traderId, order.getSide(), order.getStock(), order.getQuantity());

        return toResponse(order);
    }

    /**
     * Cancels a PENDING order. No portfolio change is made.
     *
     * <p>Uses the same PESSIMISTIC_WRITE locking pattern as fillOrder to
     * prevent concurrent cancel + fill on the same order both succeeding.
     */
    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = orderRepository.findByIdWithLock(orderId)
                .orElseThrow(() -> new TradingException("Order not found: " + orderId, HttpStatus.NOT_FOUND));

        requireStatus(order, OrderStatus.PENDING, "cancel");

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        log.info("Order cancelled id={}", orderId);

        return toResponse(order);
    }

    // ── Private helpers ───────────────────────────────────────

    private void requireStatus(Order order, OrderStatus required, String action) {
        if (order.getStatus() != required) {
            throw new TradingException(
                    "Cannot " + action + " order " + order.getId() +
                    ". Current status is " + order.getStatus() +
                    ". Only " + required + " orders can be " + action + "ed.",
                    HttpStatus.CONFLICT);
        }
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getTrader().getId(),
                order.getStock(),
                order.getSector(),
                order.getQuantity(),
                order.getSide(),
                order.getStatus(),
                order.getCreatedAt());
    }
}
