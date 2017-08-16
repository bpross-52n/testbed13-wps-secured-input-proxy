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
package org.n52.security.test;

import java.io.File;
import java.io.IOException;

import org.apache.xmlbeans.XmlException;
import org.n52.wps.execute.input.proxy.ExecuteRequest;

import net.opengis.wps.x20.ExecuteDocument;

public class Test {

    public static void main(String[] args) throws XmlException, IOException {

        String pathname = "D:/dev/tomcat/apache-tomcat-8.5.5/temp/secured-input-proxy-database/Results/3820d1df-fe64-44c1-abc8-04bd675c5b26/request.xml";

        ExecuteDocument document = ExecuteDocument.Factory.parse(new File(pathname));

        ExecuteRequest executeRequest = new ExecuteRequest(document, "46346346rsgsg");

        executeRequest.replaceDataInputReference("d:/tmp/data.xml", "data");

        System.out.println(executeRequest.getExecuteDocument().xmlText());
    }

}
