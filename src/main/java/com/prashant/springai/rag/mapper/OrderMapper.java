package com.prashant.springai.rag.mapper;

import com.prashant.springai.rag.dto.OrderDTO;
import com.prashant.springai.rag.model.Order;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
  OrderDTO toDto(Order order);
}
