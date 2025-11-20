package org.budgetanalyzer.permission.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({"org.budgetanalyzer.core", "org.budgetanalyzer.service"})
public class PermissionServiceConfig {}
