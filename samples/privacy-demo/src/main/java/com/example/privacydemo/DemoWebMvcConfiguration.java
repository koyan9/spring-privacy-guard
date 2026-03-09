/*
 * Copyright 2026 koyan9
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.privacydemo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(DemoAdminProperties.class)
class DemoWebMvcConfiguration implements WebMvcConfigurer {

    private final DemoAdminTokenInterceptor demoAdminTokenInterceptor;

    DemoWebMvcConfiguration(DemoAdminTokenInterceptor demoAdminTokenInterceptor) {
        this.demoAdminTokenInterceptor = demoAdminTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(demoAdminTokenInterceptor)
                .addPathPatterns(
                        "/audit-dead-letters",
                        "/audit-dead-letters/**",
                        "/demo-alert-receiver/last",
                        "/demo-alert-receiver/replay-store",
                        "/demo-alert-receiver/replay-store/**"
                );
    }
}
