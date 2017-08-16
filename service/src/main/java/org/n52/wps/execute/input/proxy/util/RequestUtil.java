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

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.n52.wps.server.request.strategy.ReferenceInputStream;

public class RequestUtil {

    public static String getParameterValue(HttpServletRequest req, String key){

        String value = null;

        Map<String, String[]> parameterMap = req.getParameterMap();

        Iterator<String> parameterKeyIterator = parameterMap.keySet().iterator();

        while (parameterKeyIterator.hasNext()) {
            String parameterKey = (String) parameterKeyIterator.next();

            if(parameterKey.toLowerCase().equals(key.toLowerCase())){
                String[] possibleValueArray = parameterMap.get(parameterKey);
                if(possibleValueArray != null && possibleValueArray.length > 0){
                    return possibleValueArray[0];
                }
            }
        }

        return value;
    }

    /**
     * Make a GET request using mimeType and href
     *
     * TODO: add support for autoretry, proxy
     */
    public static ReferenceInputStream httpGet(final String dataURLString, final String mimeType, String accessToken) throws IOException {

        HttpClient backend = HttpClientBuilder.create().build();

        HttpGet httpget = new HttpGet(dataURLString);

        if (mimeType != null){
            httpget.addHeader(new BasicHeader("Content-type", mimeType));
        }

        if(accessToken != null && !accessToken.isEmpty()){
            httpget.addHeader(new BasicHeader("Authorization", accessToken));
        }

        return processResponse(backend.execute(httpget), mimeType);
    }

    /**
     * Make a POST request using mimeType and href
     *
     * TODO: add support for autoretry, proxy
     */
    public static ReferenceInputStream httpPost(final String dataURLString, final String body, final String mimeType, String accessToken) throws IOException {

        HttpClient backend = HttpClientBuilder.create().build();

        HttpPost httppost = new HttpPost(dataURLString);

        if (mimeType != null){
            httppost.addHeader(new BasicHeader("Content-type", mimeType));
        }

        if(accessToken != null && !accessToken.isEmpty()){
            httppost.addHeader(new BasicHeader("Authorization", accessToken));
        }
        // set body entity
        HttpEntity postEntity = new StringEntity(body);
        httppost.setEntity(postEntity);

        return processResponse(backend.execute(httppost), mimeType);
    }

    private static ReferenceInputStream processResponse(org.apache.http.HttpResponse response, String mimeType) throws IOException {

        HttpEntity entity = response.getEntity();
        org.apache.http.Header header;

        header = entity.getContentType();
//        String mimeType = header == null ? null : header.getValue();

        header = entity.getContentEncoding();
        String encoding = header == null ? null : header.getValue();

        return new ReferenceInputStream(entity.getContent(), mimeType, encoding);
    }

}
