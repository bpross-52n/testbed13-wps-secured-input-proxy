/*
 * Copyright 2017-2017 52°North Initiative for Geospatial Open Source
 * Software GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.n52.wps.execute.input.proxy.util;

import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Configuration {

    private static final Logger LOGGER = LoggerFactory.getLogger(Configuration.class);

    private static Configuration instance;

    private String clientID;
    private String clientSecret;
    private String audience;
    private String tokenEndpoint;
    private String backendServiceURL;

    private Configuration(InputStream configJSON) {
        parseConfig(configJSON);
    }

    public static Configuration getInstance(InputStream configJSON) {
        if (instance != null) {
            return instance;
        } else {
            instance = new Configuration(configJSON);
            return instance;
        }

    }

    public static Configuration getInstance() {
        if (instance == null) {
            throw new RuntimeException("SecurityProxyConfiguration not initialized!");
        } else {
            return instance;
        }

    }

    private void parseConfig(InputStream configJSON) {
        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode root = m.readTree(configJSON);
            clientID = root.findPath("clientID").asText();
            clientSecret = root.findPath("clientSecret").asText();
            audience = root.findPath("audience").asText();
            tokenEndpoint = root.findPath("tokenEndpoint").asText();
            backendServiceURL = root.findPath("backendServiceURL").asText();
        } catch (Exception e) {
            LOGGER.error("Error while reading SecurityProxyConfiguration!");
            throw new RuntimeException("Error while reading SecurityProxyConfiguration!");
        }
    }

    public String getClientID() {
        return clientID;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getAudience() {
        return audience;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public String getBackendServiceURL() {
        return backendServiceURL;
    }

}
