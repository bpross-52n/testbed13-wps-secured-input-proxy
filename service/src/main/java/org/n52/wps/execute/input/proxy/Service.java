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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.n52.wps.execute.input.proxy.db.FlatFileDatabase;
import org.n52.wps.execute.input.proxy.util.Configuration;
import org.n52.wps.execute.input.proxy.util.RequestUtil;
import org.n52.wps.execute.input.proxy.util.StatusUtil;
import org.n52.wps.server.ExceptionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.context.ServletConfigAware;
import org.springframework.web.context.ServletContextAware;

import net.opengis.wps.x20.ExecuteDocument;

@Controller
@RequestMapping(
        value = "/service", consumes = "*/*", produces = "*/*")
public class Service implements ServletContextAware, ServletConfigAware{

    private static Logger LOGGER = LoggerFactory.getLogger(Service.class);

    private ServletContext ctx;

    private Map<String, String> idMap;

    public void init(){
        idMap = new HashMap<>();
        Configuration.getInstance(ctx.getResourceAsStream("/WEB-INF/config/config.json"));
    }

    @RequestMapping(method = RequestMethod.GET)
    public void get(HttpServletRequest req,
            HttpServletResponse res){

        // check, whether request is GetCapabilities
        String requestParam = RequestUtil.getParameterValue(req, "request");
//        String queryString = req.getQueryString();
//        String serviceParam = getParameterValue(req, "service");

        if(requestParam.equals("GetStatus")){

            String id = RequestUtil.getParameterValue(req, "jobid");

            id = getID(id);

            try {
                InputStream statusStream = FlatFileDatabase.getInstance().lookupStatus(id);

                IOUtils.copy(statusStream, res.getOutputStream());

                res.setContentType("application/xml");

            } catch (ExceptionReport e) {
                LOGGER.error(e.getMessage());
                e.printStackTrace();
            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }

        }else if(requestParam.equals("GetResult")){

            String id = RequestUtil.getParameterValue(req, "jobid");

            id = getID(id);

            try {
                InputStream statusStream = FlatFileDatabase.getInstance().lookupResponse(id);

                IOUtils.copy(statusStream, res.getOutputStream());

                res.setContentType("application/xml");

            } catch (IOException e) {
                LOGGER.error(e.getMessage());
            }

        }

    }

    @RequestMapping(method = RequestMethod.POST)
    public void post(HttpServletRequest req,
            HttpServletResponse res){
        try {
            XmlObject postRequest = XmlObject.Factory.parse(req.getInputStream());

            if(postRequest instanceof ExecuteDocument){

                String id = UUID.randomUUID().toString();

                StatusUtil.updateStatusAccepted(id);

                InputStream statusStream;
                try {
                    statusStream = FlatFileDatabase.getInstance().lookupStatus(id);

                    IOUtils.copy(statusStream, res.getOutputStream());

                    res.setContentType("application/xml");
                } catch (ExceptionReport e) {
                    LOGGER.error("Could not return status.", e);
                }

                //store id, use same id for now
                //will be changed after forwarding execute request
                putID(id, id);

                new Thread(){

                    public void run() {

                        FlatFileDatabase.getInstance().insertRequest(id, postRequest.newInputStream(), true);

                        //check inputs
                        ExecuteDocument executeDoc = (ExecuteDocument)postRequest;

                        ExecuteRequest executeRequest = new ExecuteRequest(executeDoc, id);

                        executeRequest.startInputCheck();

//                        DataInputType[] dataInputArray = executeDoc.getExecute().getInputArray();
//
//                        for (DataInputType dataInputType : dataInputArray) {
//
//                            if(dataInputType.isSetReference()){
//
//                                updateStatusInputCheck(dataInputType.getId(), id);
//
//                                checkDataInput(dataInputType, id);
//                            }
//
//                        }

                    };

                }.start();

                //get token

            }

//            String redirectURL = "https://bpross-52n.eu.auth0.com/authorize?scope=GetFeature&audience=http://tb12.dev.52north.org/geoserver/tb13/ows&response_type=code&client_id=DVS5yCJAYM0dahfjGSj8cnX02M1pBG4H&redirect_uri=http://localhost:8080/SecurityProxy/oauth2callback";
//
//            res.setStatus(303);
//            res.addHeader("Location", redirectURL);


        } catch (XmlException | IOException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private synchronized void putID(String id, String mappedID){
        idMap.put(id, mappedID);
    }

    private synchronized String getID(String id){
        return idMap.get(id);
    }

    @Override
    public void setServletConfig(ServletConfig arg0) {}

    @Override
    public void setServletContext(ServletContext arg0) {
       ctx = arg0;
    }

}
