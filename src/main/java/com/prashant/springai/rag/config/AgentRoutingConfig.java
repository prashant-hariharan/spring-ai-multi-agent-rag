package com.prashant.springai.rag.config;

import com.prashant.springai.rag.model.AgentRoute;
import com.prashant.springai.rag.service.ai.route.AgentRouteHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class AgentRoutingConfig {

  @Bean
  public Map<AgentRoute, AgentRouteHandler> routeHandlerMap(List<AgentRouteHandler> routeHandlers) {
    return routeHandlers.stream()
      .collect(Collectors.toMap(
        AgentRouteHandler::route,
        Function.identity(),
        (first, second) -> {
          throw new IllegalStateException("Duplicate AgentRouteHandler for route: " + first.route());
        }
      ));
  }
}
