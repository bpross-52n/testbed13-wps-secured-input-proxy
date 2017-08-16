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

import org.n52.geoprocessing.oauth2.AccessTokenResponse;
import org.n52.geoprocessing.oauth2.util.JSONUtil;
import org.n52.wps.execute.input.proxy.db.FlatFileDatabase;
import org.n52.wps.execute.input.proxy.util.Configuration;
import org.n52.wps.execute.input.proxy.util.RequestUtil;
import org.n52.wps.execute.input.proxy.util.StatusUtil;
import org.n52.wps.server.ExceptionReport;
import org.n52.wps.server.request.InputReference;
import org.n52.wps.server.request.strategy.ReferenceInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import net.opengis.wps.x20.DataInputType;
import net.opengis.wps.x20.ExecuteDocument;

public class ExecuteRequest {

    private static Logger LOGGER = LoggerFactory.getLogger(ExecuteRequest.class);

    private ExecuteDocument executeDocument;

    private String id;

    private InputChecker checker;

    public ExecuteRequest(ExecuteDocument document, String id){

        try {
            checker = new InputChecker();
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            return;
        }

        this.executeDocument = document;
        this.id = id;
    }

    public ExecuteRequest(String id) throws Exception, IOException{
        //get executeDocument from db
        this(ExecuteDocument.Factory.parse(FlatFileDatabase.getInstance().lookupRequest(id)), id);
    }

    public void startInputCheck(){

        DataInputType[] dataInputArray = executeDocument.getExecute().getInputArray();

        for (DataInputType dataInputType : dataInputArray) {

            if(dataInputType.isSetReference()){

                StatusUtil.updateStatusInputCheck(dataInputType.getId(), id);

                checkDataInput(dataInputType, id);
            }

        }

        if(checkInputs()){
            removeSecurityCheckMarks();
            try {
                forwardExecuteRequest();
            } catch (IOException e) {
                LOGGER.error("Could not forward execute request", e);
            }
        }

    }

    //checks, if data input is an protected OWS service
    //tries to add access token to authorization header
    private void checkDataInput(DataInputType dataInputType, String jobId) {

        InputReference inputReference = new InputReference(dataInputType);

        if(checker.isOAuth2Protected(inputReference)){

            AccessTokenResponse accessTokenResponse = null;

            String accessToken = "";

            try {
                accessTokenResponse = checker.getAccessTokenViaClientCredentials();
            } catch (Exception e) {
                LOGGER.info("Could not get access token via client credentials flow. Trying authorization code flow.", e);
            }

            if(accessTokenResponse.getAccessToken() != null && !accessTokenResponse.getAccessToken().isEmpty()){
                accessToken = accessTokenResponse.getAccessToken();

                try {
                    ReferenceInputStream referenceInputStream = checker.fetchData(inputReference, accessToken);

                    String inputId = dataInputType.getId();

                    String filePath = saveReferenceStreamToFile(referenceInputStream, inputId);

                    replaceDataInputReference(filePath, inputId);

                    //get data and replace input of execute document with inline data

                } catch (ExceptionReport e) {
                    LOGGER.error("Could not fetch input: " + dataInputType.getId(), e);
                }
            }else if(accessTokenResponse.isError()){

                //TODO check error

                StatusUtil.updateStatusRedirect(dataInputType.getId(), jobId);
            }
        }else{
            markSecurityChecked(dataInputType);
        }
    }

    private void markSecurityChecked(DataInputType dataInputType) {
        //mark input checked
        dataInputType.setId(dataInputType.getId() + "-security-checked");
    }

    public void getAccessTokenWithCode(String inputId,
            String code) {
        try {
            HttpResponse<String> response = Unirest.post(Configuration.getInstance().getTokenEndpoint())
                    .header("content-type", "application/json")
                    .body("{\"grant_type\":\"authorization_code\",\"client_id\": \"" + Configuration.getInstance().getClientID() + "\",\"client_secret\": \"" + Configuration.getInstance().getClientSecret() + "\",\"code\": \"" + code + "\",\"redirect_uri\": \"" + Configuration.getInstance().getRedirectUrl() + "\"}")
                    .asString();

            AccessTokenResponse accessTokenResponse = new AccessTokenResponse(new JSONUtil().parseJSONString(response.getBody()));

            String accessToken = accessTokenResponse.getAccessToken();

            InputReference inputReference = getInputReference(inputId);

            try {
                ReferenceInputStream referenceInputStream = checker.fetchData(inputReference, accessToken);

                String filePath = saveReferenceStreamToFile(referenceInputStream, inputId);

                //get data, store on disk and replace input of execute document with reference to data file

                replaceDataInputReference(filePath, inputId);

            } catch (ExceptionReport e) {
                LOGGER.error("Could not fetch input: " + inputId, e);
            }

        } catch (UnirestException | IOException e) {
            LOGGER.error(e.getMessage());
        }

    }

    private InputReference getInputReference(String inputId){

        DataInputType dataInputType = getDataInput(inputId);

        if(dataInputType != null){
            return new InputReference(dataInputType);
        }

        return null;
    }

    private DataInputType getDataInput(String inputId){
        DataInputType[] datainputs = executeDocument.getExecute().getInputArray();

        for (DataInputType dataInputType : datainputs) {
            if(dataInputType.getId().equals(inputId)){
                return dataInputType;
            }
        }
        return null;
    }

    private String saveReferenceStreamToFile(ReferenceInputStream inputStream, String id){

        String filePath = FlatFileDatabase.getInstance().storeComplexValue(id, inputStream, inputStream.getMimeType());

        return filePath;
    }

    public void replaceDataInputReference(String filePath, String inputId){

        DataInputType dataInputType = getDataInput(inputId);

        dataInputType.getReference().setHref("file://" + filePath);

        markSecurityChecked(dataInputType);

        FlatFileDatabase.getInstance().insertRequest(id, executeDocument.newInputStream(), true);

        if(checkInputs()){
            removeSecurityCheckMarks();
            try {
                forwardExecuteRequest();
            } catch (IOException e) {
                LOGGER.error("Could not forward execute request", e);
            }
        }

    }

    public ExecuteDocument getExecuteDocument() {
        return executeDocument;
    }

    public boolean checkInputs(){

        boolean allInputsSecurityChecked = true;

        DataInputType[] datainputs = executeDocument.getExecute().getInputArray();

        for (DataInputType dataInputType : datainputs) {
            if(dataInputType.isSetReference() && !dataInputType.getId().contains("-security-checked")){
                allInputsSecurityChecked = false;
                break;
            }
        }

        return allInputsSecurityChecked;
    }

    public void removeSecurityCheckMarks(){
        DataInputType[] datainputs = executeDocument.getExecute().getInputArray();

        for (DataInputType dataInputType : datainputs) {
            if(dataInputType.isSetReference() && dataInputType.getId().contains("-security-checked")){
                dataInputType.setId(dataInputType.getId().replace("-security-checked", ""));
            }
        }
    }

    public void forwardExecuteRequest() throws IOException{

        ReferenceInputStream referenceInputStream = RequestUtil.httpPost(Configuration.getInstance().getBackendServiceURL(), executeDocument.xmlText(), "application/xml", null);

        StatusUtil.updateStatusSuccess(id);

        FlatFileDatabase.getInstance().storeResponse(id, referenceInputStream);
    }
}
