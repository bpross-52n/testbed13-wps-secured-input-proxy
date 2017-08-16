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
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.xmlbeans.XmlObject;
import org.n52.geoprocessing.oauth2.AccessTokenResponse;
import org.n52.geoprocessing.oauth2.OAuth2Client;
import org.n52.wps.execute.input.proxy.util.Configuration;
import org.n52.wps.execute.input.proxy.util.RequestUtil;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.request.InputReference;
import org.n52.wps.server.request.strategy.ReferenceInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mashape.unirest.http.exceptions.UnirestException;

import net.opengis.ows.x20.DomainType;
import net.opengis.ows.x20.OperationDocument.Operation;
import net.opengis.ows.x20.ValuesReferenceDocument.ValuesReference;
import net.opengis.wfs.x20.WFSCapabilitiesDocument;
import net.opengis.wps.x20.CapabilitiesDocument;

public class InputChecker {

    private static Logger LOGGER = LoggerFactory.getLogger(InputChecker.class);

    private String bearerTokenReference = "urn:ogc:def:security:authentication:ietf:6750:Bearer";


    public boolean isOAuth2Protected(InputReference input) {

        //try to fetch resource
        XmlObject payload = null;

        if(input.isSetBody()){
            payload = input.getBody();
        }

        String href = input.getHref();

        boolean protectedResource = false;

        try {
            protectedResource = checkIfProtectedResource(href, (payload != null ? payload.xmlText() : null));
        } catch (IOException e) {
            LOGGER.error("Could not fetch from URL: "  + href);
            if(payload != null){
                LOGGER.trace("Payload: " + payload.xmlText());
            }
        }

        if(!protectedResource){
            return false;//strategy not applicable for unprotected resources
        }

        //if 401 not authorized is returned (and if the service is an OWS), try to fetch capabilities
        //if oauth2 protected, return true
        try {
            return checkIfCapabilitiesContainOAuth2Constraint(href);
        } catch (Exception e) {
            LOGGER.error("Could not check capabilities.", e);
        }

        return false;
    }

    public AccessTokenResponse getAccessTokenViaClientCredentials() throws ExceptionReport, UnirestException, IOException{

        Configuration configModule = Configuration.getInstance();

        String clientID = configModule.getClientID();
        String clientSecret = configModule.getClientSecret();
        String audience = configModule.getAudience();
        String tokenEndpointString = configModule.getTokenEndpoint();
        URL tokenEndpoint;

        try {
            tokenEndpoint = new URL(tokenEndpointString);
        } catch (MalformedURLException e) {
            throw new ExceptionReport("Could not create URL from token_endpoint parameter: " + tokenEndpointString, ExceptionReport.NO_APPLICABLE_CODE);
        }

        AccessTokenResponse accessTokenResponse = new OAuth2Client().getAccessToken(tokenEndpoint, clientID, clientSecret, audience);

//        //get new access token with client credentials
//        String accessToken = "";
//
//        try {
//            AccessTokenResponse accessTokenResponse = new OAuth2Client().getAccessToken(tokenEndpoint, clientID, clientSecret, audience);
//            if(accessTokenResponse.isError()){
//                throw new ExceptionReport("Could not get access token URL from token_endpoint. Error: " + accessTokenResponse.getErrorCause(), ExceptionReport.NO_APPLICABLE_CODE);//TODO adjust exception text
//            }
//            accessToken = accessTokenResponse.getAccessToken();
//        } catch (UnirestException | IOException e) {
//            throw new ExceptionReport("Could not get access token URL from token_endpoint: " + tokenEndpointString, ExceptionReport.NO_APPLICABLE_CODE);//TODO adjust exception text
//        }
//
//        if(accessToken == null || accessToken.isEmpty()){
//            throw new ExceptionReport("Could not get access token URL from token_endpoint: " + tokenEndpointString, ExceptionReport.NO_APPLICABLE_CODE);//TODO adjust exception text
//        }

        return accessTokenResponse;

    }

    public ReferenceInputStream fetchData(InputReference input, String accessToken) throws ExceptionReport {

        //let the Thread sleep a bit, as the access token might not be valid already
        try {
            Thread.sleep(60000);
        } catch (Exception e) {
            // TODO: handle exception
        }

        //send token to service in Authorization header
        String href = input.getHref();
        String mimeType = input.getMimeType();

        try {
            if (input.isSetBody()) {
                String body = input.getBody().toString();
                return RequestUtil.httpPost(href, body, mimeType, accessToken);
            }

            // Handle get request
            else {
                return RequestUtil.httpGet(href, mimeType, accessToken);
            }

        }
        catch(RuntimeException e) {
            throw new ExceptionReport("Error occured while parsing XML",
                                        ExceptionReport.NO_APPLICABLE_CODE, e);
        }
        catch(MalformedURLException e) {
            String inputID = input.getIdentifier();
            throw new ExceptionReport("The inputURL of the execute is wrong: inputID: " + inputID + " | dataURL: " + href,
                                        ExceptionReport.INVALID_PARAMETER_VALUE );
        }
        catch(IOException e) {
             String inputID = input.getIdentifier();
             throw new ExceptionReport("Error occured while receiving the complexReferenceURL: inputID: " + inputID + " | dataURL: " + href,
                                     ExceptionReport.INVALID_PARAMETER_VALUE );
        }
    }



    private boolean checkIfCapabilitiesContainOAuth2Constraint(String originalRequest) throws Exception{

        ServiceType serviceType = null;

        if(originalRequest.toLowerCase().contains("wps")){

            serviceType = ServiceType.WPS;

        }else if(originalRequest.toLowerCase().contains("wfs")){

            serviceType = ServiceType.WFS;
        }

        OperationType operationType = getOperationType(originalRequest);

        try {
            InputStream capabilitiesInputStream = requestCapabilities(originalRequest, serviceType);

            switch (serviceType) {
            case WPS:
                CapabilitiesDocument wpsCapsDoc = CapabilitiesDocument.Factory.parse(capabilitiesInputStream);

                return checkIfOperationIsProtected(wpsCapsDoc.getCapabilities().getOperationsMetadata().getOperationArray(), operationType);

            case WFS:
                WFSCapabilitiesDocument wfsCapsDoc = WFSCapabilitiesDocument.Factory.parse(capabilitiesInputStream);

                return checkIfOperationIsProtected(wfsCapsDoc.getWFSCapabilities().getOperationsMetadata().getOperationArray(), operationType);

            default:
                break;
            }

        } catch (IOException e) {
            LOGGER.error("Could not request capabilities for original request:" + originalRequest);
            return false;
        }

        return false;
    }

    private boolean checkIfOperationIsProtected(net.opengis.ows.x11.OperationDocument.Operation[] operationArray,
            OperationType operationType) {

        for (net.opengis.ows.x11.OperationDocument.Operation operation : operationArray) {

            if(operation.getName().toLowerCase().equals(operationType.toString().toLowerCase())){

                net.opengis.ows.x11.DomainType[] constraintArray = operation.getConstraintArray();

                for (net.opengis.ows.x11.DomainType domainType : constraintArray) {
                    if(domainType.isSetValuesReference()){
                        net.opengis.ows.x11.ValuesReferenceDocument.ValuesReference valuesReference = domainType.getValuesReference();

                        String referenceValue = valuesReference.getStringValue();

                        if(referenceValue != null && referenceValue.equals(bearerTokenReference)){
                            return true;
                        }

                    }
                }

            }

        }
        return false;
    }

    private boolean checkIfOperationIsProtected(Operation[] operationArray, OperationType operationType){

        for (Operation operation : operationArray) {

            if(operation.getName().toLowerCase().equals(operationType.toString().toLowerCase())){

                DomainType[] constraintArray = operation.getConstraintArray();

                for (DomainType domainType : constraintArray) {
                    if(domainType.isSetValuesReference()){

                        ValuesReference valuesReference = domainType.getValuesReference();

                        String referenceValue = valuesReference.getStringValue();

                        if(referenceValue != null && referenceValue.equals(bearerTokenReference)){
                            return true;
                        }

                    }
                }

            }

        }

        return false;

    }

    private OperationType getOperationType(String originalRequest) {

        originalRequest = originalRequest.toLowerCase();

        if(originalRequest.contains(OperationType.EXECUTE.toString().toLowerCase())){
            return OperationType.EXECUTE;
        }else if(originalRequest.contains(OperationType.DESCRIBEPROCESS.toString().toLowerCase())){
            return OperationType.DESCRIBEPROCESS;
        }else if(originalRequest.contains(OperationType.INSERTPROCESS.toString().toLowerCase())){
            return OperationType.INSERTPROCESS;
        }else if(originalRequest.contains(OperationType.DESCRIBEFEATURETYPE.toString().toLowerCase())){
            return OperationType.DESCRIBEFEATURETYPE;
        }else if(originalRequest.contains(OperationType.GETFEATURE.toString().toLowerCase())){
            return OperationType.GETFEATURE;
        }

        return OperationType.UNDEFINED;
    }

    private String createGetCapabilitiesRequest(String originalRequest, ServiceType serviceType){

        String capabilitiesURL = "";

        //get index of question mark as separator between base url and request
        int indexOfQuestionmark = originalRequest.indexOf("?");

        if(indexOfQuestionmark < 0){
            LOGGER.info("Request doesn't seem to be OWS request. It doesn't contain a question mark: " + originalRequest);
            return capabilitiesURL;
        }

        capabilitiesURL = originalRequest.substring(0, indexOfQuestionmark + 1);

        capabilitiesURL = capabilitiesURL.concat("request=GetCapabilities");

        //we have to differentiate bewteen wps and wfs, because of the different version handling
        //we only will request version 2.0.0 of each service
        switch (serviceType) {
        case WPS:
            capabilitiesURL = capabilitiesURL.concat("&service=WPS&acceptVersions=2.0.0");

            break;
        case WFS:
            capabilitiesURL = capabilitiesURL.concat("&service=WFS&version=2.0.0");

            break;

        default:
            break;
        }

        return capabilitiesURL;
    }

    private InputStream requestCapabilities(String originalRequest, ServiceType serviceType) throws IOException{

        String getCapabilitiesURL = createGetCapabilitiesRequest(originalRequest, serviceType);

        if(getCapabilitiesURL == null || getCapabilitiesURL.isEmpty()){
            return null;
        }

        // Send data
        URL url = new URL(getCapabilitiesURL);

        URLConnection conn = url.openConnection();

        conn.setDoOutput(true);

        return conn.getInputStream();
    }

    private boolean checkIfProtectedResource(String targetURL,
            String payload) throws IOException {
        // Send data
        URL url = new URL(targetURL);

        URLConnection conn = url.openConnection();

        conn.setDoOutput(true);

        if (payload != null) {

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());

            wr.write(payload);
            wr.close();

        }

        try {
            conn.getInputStream();
            // resource can be accessed, so ot protected
            return false;
        } catch (IOException e) {
            LOGGER.info("Exception while trying to fetch resource.");
        }

        int statusCode = ((HttpURLConnection) conn).getResponseCode();

        LOGGER.info("Status code: " + statusCode);

        if (statusCode == 401) {
            return true;
        }
        return false;
    }

    enum ServiceType{

        WPS, WFS

    }

    enum OperationType{

        DESCRIBEPROCESS, EXECUTE, GETFEATURE, DESCRIBEFEATURETYPE, INSERTPROCESS, UNDEFINED
    }

}
