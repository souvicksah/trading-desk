package com.trading.desk.model.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class AddToPortfolioRequest {

    @NotBlank(message = "stock is required")
    private String stock;

    @NotBlank(message = "sector is required")
    private String sector;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    private Integer quantity;

    public AddToPortfolioRequest() {}

    public AddToPortfolioRequest(String stock, String sector, Integer quantity) {
        this.stock = stock;
        this.sector = sector;
        this.quantity = quantity;
    }

    public String getStock() { return stock; }
    public void setStock(String stock) { this.stock = stock; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
