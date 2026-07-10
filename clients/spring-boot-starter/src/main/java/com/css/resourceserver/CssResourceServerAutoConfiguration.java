package com.css.resourceserver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@AutoConfiguration
@EnableConfigurationProperties(CssResourceServerProperties.class)
@ConditionalOnProperty(prefix = "css.resource-server", name = "enabled", havingValue = "true")
public class CssResourceServerAutoConfiguration {

    @Bean
    public CssJwtValidator cssJwtValidator(CssResourceServerProperties properties) {
        return new CssJwtValidator(properties);
    }

    @Bean
    public CssJwtAuthenticationFilter cssJwtAuthenticationFilter(CssJwtValidator validator) {
        return new CssJwtAuthenticationFilter(validator);
    }

    @Bean
    public FilterRegistrationBean<CssJwtAuthenticationFilter> cssJwtFilterRegistration(
            CssJwtAuthenticationFilter filter
    ) {
        FilterRegistrationBean<CssJwtAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
