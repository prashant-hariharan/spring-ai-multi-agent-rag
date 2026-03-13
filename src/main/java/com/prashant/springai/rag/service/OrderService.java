package com.prashant.springai.rag.service;

import com.prashant.springai.rag.config.CacheConfig;
import com.prashant.springai.rag.dto.OrderDTO;
import com.prashant.springai.rag.exception.InvalidInputException;
import com.prashant.springai.rag.exception.ResourceNotFoundException;
import com.prashant.springai.rag.mapper.OrderMapper;
import com.prashant.springai.rag.model.Order;
import com.prashant.springai.rag.model.OrderStatus;
import com.prashant.springai.rag.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

  private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile("^ORD-\\d{4}$");
  private static final String ORDER_PREFIX = "ORD-";
  private final OrderRepository orderRepository;
  private final OrderMapper orderMapper;
  private final CacheManager cacheManager;

  @Transactional
  public OrderDTO createOrder(String customerName, String productName, int quantity, BigDecimal unitPrice, OrderStatus status,String deliveryInfo) {
    String orderNumber = generateNextOrderNumber();
    Order order = new Order(orderNumber, customerName, productName, quantity, unitPrice, status,deliveryInfo);
    Order savedOrder = orderRepository.save(order);
    return orderMapper.toDto(savedOrder);
  }

  @Transactional(readOnly = true)
  public OrderDTO getOrderByNumber(String orderNumber) {
    String normalizedOrderNumber = validateAndNormalizeOrderNumber(orderNumber);
    Cache cache = cacheManager.getCache(CacheConfig.ORDER_READ_CACHE);
    if (cache != null) {
      OrderDTO cached = cache.get(normalizedOrderNumber, OrderDTO.class);
      if (cached != null) {
        log.info("[{}] HIT key={} -> returning cached order (DB not queried)",
          CacheConfig.ORDER_READ_CACHE, normalizedOrderNumber);
        return cached;
      }
      log.info("[{}] MISS key={} -> querying DB",
        CacheConfig.ORDER_READ_CACHE, normalizedOrderNumber);
    }

    OrderDTO result = orderMapper.toDto(getExistingOrder(normalizedOrderNumber));
    if (cache != null) {
      cache.put(normalizedOrderNumber, result);
      log.info("[{}] STORE key={}", CacheConfig.ORDER_READ_CACHE, normalizedOrderNumber);
    }
    return result;
  }

  private String normalizeOrderNumber(String orderNumber) {
    if (orderNumber == null) {
      return "";
    }
    return orderNumber.trim().replaceAll("[\\p{Punct}]+$", "");
  }

  private String validateAndNormalizeOrderNumber(String orderNumber) {
    String normalizedOrderNumber = normalizeOrderNumber(orderNumber);
    if (!ORDER_NUMBER_PATTERN.matcher(normalizedOrderNumber).matches()) {
      throw new InvalidInputException("Invalid order number format. Expected format: ORD-0003.");
    }
    return normalizedOrderNumber;
  }

  public String normalizeAndValidateForCache(String orderNumber) {
    return validateAndNormalizeOrderNumber(orderNumber);
  }

  private Order getExistingOrder(String normalizedOrderNumber) {
    return orderRepository.findByOrderNumber(normalizedOrderNumber)
      .orElseThrow(() -> new ResourceNotFoundException("Order not found for order number " + normalizedOrderNumber + "."));
  }

  private String generateNextOrderNumber() {
    return orderRepository.findTopByOrderNumberStartingWithOrderByOrderNumberDesc(ORDER_PREFIX)
      .map(Order::getOrderNumber)
      .map(this::extractNumericPart)
      .map(sequence -> sequence + 1)
      .map(sequence -> String.format("%s%04d", ORDER_PREFIX, sequence))
      .orElse(String.format("%s%04d", ORDER_PREFIX, 1));
  }

  private int extractNumericPart(String orderNumber) {
    try {
      return Integer.parseInt(orderNumber.substring(ORDER_PREFIX.length()));
    } catch (RuntimeException ex) {
      log.warn("Unexpected order number format in DB: {}. Restarting sequence from 1.", orderNumber);
      return 0;
    }
  }


  @EventListener(ApplicationReadyEvent.class)
  @Transactional
  public void loadInitialOrders() {
    if (orderRepository.count() > 0) {
      log.info("Orders already exist in DB. Skipping initial seed.");
      return;
    }

    createOrder("Alice Johnson", "Laptop", 1, new BigDecimal("899.99"),OrderStatus.CREATED,"test 1234 Germany");
    createOrder("Bob Smith", "Mechanical Keyboard", 2, new BigDecimal("129.50"),OrderStatus.PROCESSING,"test 5679 India");
    OrderDTO latestOrder = createOrder("Charlie Davis", "27-inch Monitor", 1, new BigDecimal("329.00"),OrderStatus.SHIPPED,"test xyz USA");

    log.info("Initial orders loaded successfully. Latest seeded order number: {}", latestOrder.orderNumber());
  }
}
