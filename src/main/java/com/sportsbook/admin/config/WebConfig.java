package com.sportsbook.admin.config;

import com.sportsbook.admin.context.AdminContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

  private final AdminContextArgumentResolver adminContexts;

  WebConfig(AdminContextArgumentResolver adminContexts) {
    this.adminContexts = adminContexts;
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(adminContexts);
  }
}
