package com.trading.desk.model.dto.response;

import com.trading.desk.model.enums.OrderSide;
import com.trading.desk.model.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class OrderResponse {

    private UUID id;
    private String traderId;
    private String stock;
    private String sector;
    private Integer quantity;
    private OrderSide side;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public OrderResponse() {}

    public OrderResponse(UUID id, String traderId, String stock, String sector,
                         Integer quantity, OrderSide side, OrderStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.traderId = traderId;
        this.stock = stock;
        this.sector = sector;
        this.quantity = quantity;
        this.side = side;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getTraderId() { return traderId; }
    public void setTraderId(String traderId) { this.traderId = traderId; }

    public String getStock() { return stock; }
    public void setStock(String stock) { this.stock = stock; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
