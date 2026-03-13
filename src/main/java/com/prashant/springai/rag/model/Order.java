package com.prashant.springai.rag.model;

import com.prashant.springai.rag.exception.BusinessValidationException;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

  @Id
  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(name = "order_number", nullable = false, unique = true, length = 20)
  private String orderNumber;

  @Column(name = "customer_name", nullable = false, length = 150)
  private String customerName;

  @Column(name = "product_name", nullable = false, length = 150)
  private String productName;

  @Column(name = "quantity", nullable = false)
  private int quantity;

  @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
  private BigDecimal unitPrice;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private OrderStatus status;

  @Column(name = "delivery_info")
  private String deliveryInfo;

  public Order(String orderNumber, String customerName, String productName
    , int quantity, BigDecimal unitPrice, OrderStatus status,String deliveryInfo) {
    this.orderNumber = orderNumber;
    this.customerName = customerName;
    this.productName = productName;
    this.quantity = quantity;
    this.unitPrice = unitPrice;
    this.status = status;
    this.deliveryInfo = deliveryInfo;
  }

  @PrePersist
  public void prePersist() {
    if (this.orderId == null) {
      this.orderId = UUID.randomUUID();
    }
    if (this.createdAt == null) {
      this.createdAt = Instant.now();
    }
  }

  public void updateStatus(OrderStatus newStatus) {
    this.status = Objects.requireNonNull(newStatus, "newStatus must not be null");
  }

  public void cancel() {
    if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
      throw new BusinessValidationException("Since the order is " + this.status.name() + ", it cannot be cancelled.");
    }
    this.status = OrderStatus.CANCELLED;
  }

  public void addDeliveryInfo(String deliveryInfo) {
    if (this.status == OrderStatus.SHIPPED || this.status == OrderStatus.DELIVERED) {
      throw new BusinessValidationException("Since the order is " + this.status.name() + ", delivery info cannot be updated.");
    }
    this.deliveryInfo = deliveryInfo;
  }
}
