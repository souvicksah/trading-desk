package com.trading.desk.model.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "portfolio",
       uniqueConstraints = @UniqueConstraint(columnNames = {"trader_id", "stock"}))
public class Portfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trader_id", nullable = false)
    private Trader trader;

    @Column(nullable = false)
    private String stock;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private Integer quantity;

    public Portfolio() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Trader getTrader() { return trader; }
    public void setTrader(Trader trader) { this.trader = trader; }

    public String getStock() { return stock; }
    public void setStock(String stock) { this.stock = stock; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}