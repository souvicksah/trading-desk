package com.trading.desk.model.entity;

import com.trading.desk.model.enums.OrderSide;
import com.trading.desk.model.enums.OrderStatus;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Lazy-loaded so fetching an order does not pull the entire trader graph.
     * Services that need the trader ID call order.getTrader().getId(), which
     * is available without hitting the DB when the proxy is already loaded.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trader_id", nullable = false)
    private Trader trader;

    @Column(nullable = false)
    private String stock;

    @Column(nullable = false)
    private String sector;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderSide side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

//    /**
//     * Optimistic lock version — Hibernate increments this on every UPDATE.
//     * Acts as a second line of defence: if two concurrent transactions both
//     * read the same version and both try to write, the second one will receive
//     * an OptimisticLockException rather than silently overwriting the first.
//     */
//    @Version
//    private Long version;

    public Order() {}

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

    public OrderSide getSide() { return side; }
    public void setSide(OrderSide side) { this.side = side; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

//    public Long getVersion() { return version; }
//    public void setVersion(Long version) { this.version = version; }
}