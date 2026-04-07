package com.trading.desk.model.dto.request;

import com.trading.desk.model.enums.OrderSide;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class PlaceOrderRequest {

    @NotBlank(message = "traderId is required")
    private String traderId;

    @NotBlank(message = "stock is required")
    private String stock;

    @NotBlank(message = "sector is required")
    private String sector;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "side is required (BUY or SELL)")
    private OrderSide side;

    public PlaceOrderRequest() {}

    public PlaceOrderRequest(String traderId, String stock, String sector, Integer quantity, OrderSide side) {
        this.traderId = traderId;
        this.stock = stock;
        this.sector = sector;
        this.quantity = quantity;
        this.side = side;
    }

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
}
