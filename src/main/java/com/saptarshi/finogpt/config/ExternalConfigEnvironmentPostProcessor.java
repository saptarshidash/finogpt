package com.saptarshi.finogpt.config;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ExternalConfigEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String CONFIG_DIR_PROPERTY = "config.dir";
    static final String CONFIG_FILE_NAME = "finogpt.properties";
    private static final String PROPERTY_SOURCE_NAME = "finogptExternalConfig";

    private static final Log log = LogFactory.getLog(ExternalConfigEnvironmentPostProcessor.class);

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String configDir = environment.getProperty(CONFIG_DIR_PROPERTY);
        if (!StringUtils.hasText(configDir)) {
            return;
        }

        Path configFile = Path.of(configDir).resolve(CONFIG_FILE_NAME).normalize().toAbsolutePath();
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalStateException("Expected external config file at " + configFile);
        }

        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(configFile)) {
            properties.load(inputStream);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load external config from " + configFile, exception);
        }

        PropertiesPropertySource propertySource = new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties);
        MutablePropertySources propertySources = environment.getPropertySources();

        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            propertySources.replace(PROPERTY_SOURCE_NAME, propertySource);
        } else if (propertySources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else if (propertySources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            propertySources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            propertySources.addFirst(propertySource);
        }

        log.info("Loaded external configuration from " + configFile);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
