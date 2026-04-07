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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private TraderRepository traderRepository;
    @Mock private PortfolioService portfolioService;

    @InjectMocks
    private OrderService orderService;

    private static final String TRADER_ID = "T001";
    private Trader trader;

    @BeforeEach
    void setUp() {
        trader = new Trader(TRADER_ID, "Alice");
    }

    // ── placeOrder ───────────────────────────────────────────

    @Test
    void placeOrder_buy_succeeds_whenBelowPendingLimit() {
        when(traderRepository.findByIdWithLock(TRADER_ID)).thenReturn(Optional.of(trader));
        when(orderRepository.countPendingByTraderId(TRADER_ID)).thenReturn(2L);
        when(orderRepository.save(any())).thenAnswer(inv -> stubId(inv.getArgument(0)));

        OrderResponse resp = orderService.placeOrder(buyRequest(50));

        assertThat(resp.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(resp.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(resp.getQuantity()).isEqualTo(50);
        verify(orderRepository).save(any());
    }

    @Test
    void placeOrder_rejected_whenPendingLimitReached() {
        when(traderRepository.findByIdWithLock(TRADER_ID)).thenReturn(Optional.of(trader));
        when(orderRepository.countPendingByTraderId(TRADER_ID)).thenReturn(3L);

        assertThatThrownBy(() -> orderService.placeOrder(buyRequest(10)))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("3 pending orders")
                .extracting(e -> ((TradingException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_sell_rejected_whenInsufficientHoldings() {
        when(traderRepository.findByIdWithLock(TRADER_ID)).thenReturn(Optional.of(trader));
        when(orderRepository.countPendingByTraderId(TRADER_ID)).thenReturn(0L);
        when(portfolioService.getHoldings(TRADER_ID, "AAPL")).thenReturn(30);

        PlaceOrderRequest req = new PlaceOrderRequest(TRADER_ID, "AAPL", "TECH", 50, OrderSide.SELL);

        assertThatThrownBy(() -> orderService.placeOrder(req))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("Insufficient holdings")
                .extracting(e -> ((TradingException) e).getHttpStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void placeOrder_sell_accepted_whenHoldingsSufficient() {
        when(traderRepository.findByIdWithLock(TRADER_ID)).thenReturn(Optional.of(trader));
        when(orderRepository.countPendingByTraderId(TRADER_ID)).thenReturn(1L);
        when(portfolioService.getHoldings(TRADER_ID, "AAPL")).thenReturn(100);
        when(orderRepository.save(any())).thenAnswer(inv -> stubId(inv.getArgument(0)));

        PlaceOrderRequest req = new PlaceOrderRequest(TRADER_ID, "AAPL", "TECH", 50, OrderSide.SELL);
        OrderResponse resp = orderService.placeOrder(req);

        assertThat(resp.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(resp.getSide()).isEqualTo(OrderSide.SELL);
    }

    @Test
    void placeOrder_sell_rejected_whenZeroHoldings() {
        when(traderRepository.findByIdWithLock(TRADER_ID)).thenReturn(Optional.of(trader));
        when(orderRepository.countPendingByTraderId(TRADER_ID)).thenReturn(0L);
        when(portfolioService.getHoldings(TRADER_ID, "AAPL")).thenReturn(0);

        PlaceOrderRequest req = new PlaceOrderRequest(TRADER_ID, "AAPL", "TECH", 1, OrderSide.SELL);

        assertThatThrownBy(() -> orderService.placeOrder(req))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void placeOrder_traderNotFound_returns404() {
        when(traderRepository.findByIdWithLock("UNKNOWN")).thenReturn(Optional.empty());

        PlaceOrderRequest req = new PlaceOrderRequest("UNKNOWN", "AAPL", "TECH", 10, OrderSide.BUY);

        assertThatThrownBy(() -> orderService.placeOrder(req))
                .isInstanceOf(TradingException.class)
                .extracting(e -> ((TradingException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── fillOrder ────────────────────────────────────────────

    @Test
    void fillOrder_buy_setsFilledAndIncreasesHoldings() {
        Order order = pendingOrder(OrderSide.BUY, 100);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse resp = orderService.fillOrder(order.getId());

        assertThat(resp.getStatus()).isEqualTo(OrderStatus.FILLED);
        verify(portfolioService).incrementHoldings(TRADER_ID, "AAPL", "TECH", 100);
        verify(portfolioService, never()).decrementHoldings(any(), any(), anyInt());
    }

    @Test
    void fillOrder_sell_setsFilledAndDecreasesHoldings() {
        Order order = pendingOrder(OrderSide.SELL, 40);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse resp = orderService.fillOrder(order.getId());

        assertThat(resp.getStatus()).isEqualTo(OrderStatus.FILLED);
        verify(portfolioService).decrementHoldings(TRADER_ID, "AAPL", 40);
        verify(portfolioService, never()).incrementHoldings(any(), any(), any(), anyInt());
    }

    @Test
    void fillOrder_rejected_whenAlreadyFilled() {
        Order order = orderWithStatus(OrderStatus.FILLED);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.fillOrder(order.getId()))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("FILLED")
                .extracting(e -> ((TradingException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void fillOrder_rejected_whenAlreadyCancelled() {
        Order order = orderWithStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.fillOrder(order.getId()))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    void fillOrder_notFound_returns404() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findByIdWithLock(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.fillOrder(id))
                .isInstanceOf(TradingException.class)
                .extracting(e -> ((TradingException) e).getHttpStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ── cancelOrder ──────────────────────────────────────────

    @Test
    void cancelOrder_success() {
        Order order = pendingOrder(OrderSide.BUY, 50);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResponse resp = orderService.cancelOrder(order.getId());

        assertThat(resp.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verifyNoInteractions(portfolioService);
    }

    @Test
    void cancelOrder_rejected_whenAlreadyCancelled() {
        Order order = orderWithStatus(OrderStatus.CANCELLED);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("CANCELLED")
                .extracting(e -> ((TradingException) e).getHttpStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancelOrder_rejected_whenAlreadyFilled() {
        Order order = orderWithStatus(OrderStatus.FILLED);
        when(orderRepository.findByIdWithLock(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(order.getId()))
                .isInstanceOf(TradingException.class)
                .hasMessageContaining("FILLED");
    }

    // ── Helpers ───────────────────────────────────────────────

    private PlaceOrderRequest buyRequest(int qty) {
        return new PlaceOrderRequest(TRADER_ID, "AAPL", "TECH", qty, OrderSide.BUY);
    }

    private Order pendingOrder(OrderSide side, int qty) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTrader(trader);
        order.setStock("AAPL");
        order.setSector("TECH");
        order.setQuantity(qty);
        order.setSide(side);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setVersion(0L);
        return order;
    }

    private Order orderWithStatus(OrderStatus status) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTrader(trader);
        order.setStock("AAPL");
        order.setSector("TECH");
        order.setQuantity(50);
        order.setSide(OrderSide.BUY);
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        order.setVersion(0L);
        return order;
    }

    /** Simulates JPA assigning a UUID after save. */
    private Order stubId(Order order) {
        if (order.getId() == null) {
            order.setId(UUID.randomUUID());
            order.setVersion(0L);
        }
        return order;
    }
}