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

import org.n52.wps.commons.XMLBeansHelper;
import org.n52.wps.execute.input.proxy.db.FlatFileDatabase;

import net.opengis.wps.x20.StatusInfoDocument;

public class StatusUtil {

    public static void updateStatusAccepted(String jobId) {
        updateStatus("Accepted", "", "", jobId);
    }

    public static void updateStatusInputCheck(String inputId, String jobId) {
        updateStatus("Running", inputId, "Checking input.", jobId);
    }

    public static void updateStatusRedirect(String id,
            String jobId) {

        String state = jobId + ":" + id;

        updateStatus("Authorization needed.", id, "https://bpross-52n.eu.auth0.com/authorize?scope=GetFeature&audience=http://tb12.dev.52north.org/geoserver/tb13/ows&response_type=code&client_id=" + Configuration.getInstance().getClientID() + "&redirect_uri=" + Configuration.getInstance().getRedirectUrl() + "&state=" + state, jobId);
    }

    public static void updateStatusSuccess(String id) {
        updateStatus("Succeeded", "", "", id);
    }

    private static void updateStatus(String status, String inputID, String message, String jobID){

        StatusInfoDocument document = StatusInfoDocument.Factory.newInstance();

        document.addNewStatusInfo().setStatus(status);

        document.getStatusInfo().setJobID(jobID);

        if(inputID != null && !inputID.isEmpty()){
            document.getStatusInfo().setInput(inputID);
        }
        if(message != null && !message.isEmpty()){
            document.getStatusInfo().setMessage(message);
        }

        XMLBeansHelper.addSchemaLocationToXMLObject(document, "http://www.opengis.net/wps/2.0 http://schemas.opengis.net/wps/2.0/wps.xsd");

        FlatFileDatabase.getInstance().storeResponse(jobID, document.newInputStream(XMLBeansHelper.getXmlOptions()));

    }

}
