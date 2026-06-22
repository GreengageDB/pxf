package org.greenplum.pxf.service.security;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String LOCALHOST_IPV4_ADDRESS = "127.0.0.1";
    private static final String LOCALHOST_IPV6_ADDRESS = "::1";
    private static final List<IpAddressMatcher> ipAddressMatchers = List.of(
            new IpAddressMatcher(LOCALHOST_IPV4_ADDRESS),
            new IpAddressMatcher(LOCALHOST_IPV6_ADDRESS)
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/pxf/reload").access(hasLocalhostIpAddress())
                        .requestMatchers("/**").permitAll());

        return http.build();
    }

    private static AuthorizationManager<RequestAuthorizationContext> hasLocalhostIpAddress() {
        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            boolean allowed = ipAddressMatchers.stream()
                    .anyMatch(matcher -> matcher.matches(request));
            return new AuthorizationDecision(allowed);
        };
    }
}
