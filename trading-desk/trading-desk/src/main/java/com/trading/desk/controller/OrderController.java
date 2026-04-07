package com.trading.desk.controller;

import com.trading.desk.model.dto.request.PlaceOrderRequest;
import com.trading.desk.model.dto.response.OrderResponse;
import com.trading.desk.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** Endpoint 1 — Place an order */
    @PostMapping
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(request));
    }

    /** Endpoint 2 — Fill a pending order */
    @PostMapping("/{orderId}/fill")
    public ResponseEntity<OrderResponse> fillOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.fillOrder(orderId));
    }

    /** Endpoint 3 — Cancel a pending order */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.cancelOrder(orderId));
    }
}
