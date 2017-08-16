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
package org.n52.wps.execute.input.proxy;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.n52.wps.execute.input.proxy.util.RequestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

@RequestMapping("/oauth2callback")
public class OAuth2Callback implements ServletConfigAware, ServletContextAware {

    protected static Logger LOGGER = LoggerFactory.getLogger(OAuth2Callback.class);

    @RequestMapping(
            method = RequestMethod.GET)
    public void doGet(HttpServletRequest req,
            HttpServletResponse res) {

        String code = RequestUtil.getParameterValue(req, "code");
        String state = RequestUtil.getParameterValue(req, "state");

        LOGGER.info("Code: " + code);
        LOGGER.info("State: " + state);

        String[] jobIdAndInputIdArray = state.split(":");

        String jobId = jobIdAndInputIdArray[0];
        String inputId = jobIdAndInputIdArray[1];

        // continue execution..

        new Thread() {

            public void run() {

                ExecuteRequest executeRequest;
                try {
                    executeRequest = new ExecuteRequest(jobId);

                    executeRequest.getAccessTokenWithCode(inputId, code);
                } catch (Exception e) {
                    LOGGER.error(e.getMessage());
                }
            };

        }.start();

    }

    @Override
    public void setServletContext(ServletContext arg0) {
    }// TODO maybe remove

    @Override
    public void setServletConfig(ServletConfig arg0) {
    }// TODO maybe remove

}
