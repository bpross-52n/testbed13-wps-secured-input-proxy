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
package org.n52.wps.execute.input.proxy.db;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.n52.wps.commons.MIMEUtil;
import org.n52.wps.commons.XMLUtil;
import org.n52.wps.server.ExceptionReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;

import net.opengis.wps.x20.StatusInfoDocument;

public class FlatFileDatabase {

    private static Logger LOGGER = LoggerFactory.getLogger(FlatFileDatabase.class);

    private static FlatFileDatabase instance;

    private final static String baseDirectory =
            Joiner.on(File.separator).join(
                System.getProperty("java.io.tmpdir", "."),
                "secured-input-proxy-database",
                "Results");

    private final static String SUFFIX_MIMETYPE = "mime-type";
    private final static String SUFFIX_CONTENT_LENGTH = "content-length";
    private final static String SUFFIX_XML = "xml";
    private final static String SUFFIX_TEMP = "tmp";
    private final static String SUFFIX_GZIP = "gz";
    private final static String SUFFIX_PROPERTIES = "properties";

    // If the delimiter changes, examine Patterns below.
    private final static Joiner JOINER = Joiner.on(".");

    // Grouping is used to pull out integer index of response, if these patterns
    // change examine findLatestResponseIndex(...), generateResponseFile(...)
    // and generateResponseFile(...)
    private final static Pattern PATTERN_RESPONSE = Pattern.compile("([\\d]+)\\." + SUFFIX_XML);
    private final static Pattern PATTERN_RESPONSE_TEMP = Pattern.compile("([\\d]+)\\." + SUFFIX_XML + "(:?\\."
            + SUFFIX_TEMP + ")?");

    protected final boolean gzipComplexValues;

    protected final Object storeResponseSerialNumberLock;

    private boolean indentXML = true;

    public FlatFileDatabase(){

        gzipComplexValues = false;

        storeResponseSerialNumberLock = new Object();

        new File(baseDirectory).mkdirs();
    }

    public boolean saveStatus(String id, Object status){

        boolean success = false;

        // store request in response directory...
        File responseDirectory = generateResponseDirectory(id);
        responseDirectory.mkdir();


        return success;

    }

    public void insertRequest(String id, InputStream inputStream, boolean xml) {
        // store request in response directory...
        File responseDirectory = generateResponseDirectory(id);
        responseDirectory.mkdir();
        BufferedOutputStream outputStream = null;
        try {
            if (xml) {
                outputStream = new BufferedOutputStream(
                        new FileOutputStream(
                            new File(
                                responseDirectory,
                                JOINER.join("request", SUFFIX_XML)),
                        false));
                XMLUtil.copyXML(inputStream, outputStream, indentXML );
            } else {
                outputStream = new BufferedOutputStream(
                        new FileOutputStream(
                            new File(
                                responseDirectory,
                                JOINER.join("request", SUFFIX_PROPERTIES)),
                        false));
                IOUtils.copy(inputStream, outputStream);
            }
        }
        catch (Exception e) {
            LOGGER.error("Exception storing request for id {}: {}", id, e);
        }
        finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
        }
    }

    public String insertResponse(String id, InputStream outputStream) {
        return this.storeResponse(id, outputStream);
    }


    public InputStream lookupRequest(String id) {
        File requestFile = lookupRequestAsFile(id);
        if (requestFile != null && requestFile.exists()) {
            LOGGER.debug("Request file for {} is {}", id, requestFile.getPath());
            try {
                return new FileInputStream(requestFile);
            }
            catch (FileNotFoundException ex) {
                // should never get here due to checks above...
                LOGGER.warn("Request not found for id {}", id);
            }
        }
        LOGGER.warn("Response not found for id {}", id);
        return null;
    }


    public InputStream lookupResponse(String id) {
        File responseFile = lookupResponseAsFile(id);
        if (responseFile != null && responseFile.exists()) {
            LOGGER.debug("Response file for {} is {}", id, responseFile.getPath());
            try {
                return responseFile.getName().endsWith(SUFFIX_GZIP) ? new GZIPInputStream(new FileInputStream(responseFile))
                                                                   : new FileInputStream(responseFile);
            }
            catch (FileNotFoundException ex) {
                // should never get here due to checks above...
                LOGGER.warn("Response not found for id {}", id);
            }
            catch (IOException ex) {
                LOGGER.warn("Error processing response for id {}", id);
            }
        }
        LOGGER.warn("Response not found for id {}", id);
        return null;
    }

    public File lookupRequestAsFile(String id) {
        File requestAsFile = null;
        // request is stored in response directory...
        File responseDirectory = generateResponseDirectory(id);
        if (responseDirectory.exists()) {
            synchronized (storeResponseSerialNumberLock) {
                requestAsFile = new File(responseDirectory, JOINER.join("request", SUFFIX_XML));
                if ( !requestAsFile.exists()) {
                    requestAsFile = new File(responseDirectory, JOINER.join("request", SUFFIX_PROPERTIES));
                }
                if ( !requestAsFile.exists()) {
                    requestAsFile = null;
                }
            }
        }
        return requestAsFile;
    }

    public File lookupResponseAsFile(String id) {
        File responseFile = null;
        // if response resolved to directory, this means the response is a status update
        File responseDirectory = generateResponseDirectory(id);
        if (responseDirectory.exists()) {
            synchronized (storeResponseSerialNumberLock) {
                return findLatestResponseFile(responseDirectory);
            }
        }
        else {
            String mimeType = getMimeTypeForStoreResponse(id);
            if (mimeType != null) {
                // ignore gzipComplexValues in case file was stored when value
                // was inconsistent with current value;
                responseFile = generateComplexDataFile(id, mimeType, false);
                if ( !responseFile.exists()) {
                    responseFile = generateComplexDataFile(id, mimeType, true);
                }
                if ( !responseFile.exists()) {
                    responseFile = null;
                }
            }
        }
        return responseFile;
    }

    public void shutdown() {
    }

    public String storeComplexValue(String id, InputStream resultInputStream, String mimeType) {

        String resultId = JOINER.join(id, UUID.randomUUID().toString());
        try {
            File resultFile = generateComplexDataFile(resultId, mimeType, gzipComplexValues);
            File mimeTypeFile = generateComplexDataMimeTypeFile(resultId);
            File contentLengthFile = generateComplexDataContentLengthFile(resultId);

            LOGGER.debug("initiating storage of complex value for {} as {}", id, resultFile.getPath());

            long contentLength = -1;

            OutputStream resultOutputStream = null;
            try {
                resultOutputStream = gzipComplexValues ? new GZIPOutputStream(new FileOutputStream(resultFile))
                                                      : new BufferedOutputStream(new FileOutputStream(resultFile));
                contentLength = IOUtils.copyLarge(resultInputStream, resultOutputStream);
            }
            finally {
                IOUtils.closeQuietly(resultInputStream);
                IOUtils.closeQuietly(resultOutputStream);
            }

            OutputStream mimeTypeOutputStream = null;
            try {
                mimeTypeOutputStream = new BufferedOutputStream(new FileOutputStream(mimeTypeFile));
                IOUtils.write(mimeType, mimeTypeOutputStream);
            }
            finally {
                IOUtils.closeQuietly(mimeTypeOutputStream);
            }

            OutputStream contentLengthOutputStream = null;
            try {
                contentLengthOutputStream = new BufferedOutputStream(new FileOutputStream(contentLengthFile));
                IOUtils.write(Long.toString(contentLength), contentLengthOutputStream);
            }
            finally {
                IOUtils.closeQuietly(contentLengthOutputStream);
            }

            LOGGER.debug("completed storage of complex value for {} as {}", id, resultFile.getPath());

            return resultFile.getAbsolutePath();

        }
        catch (IOException e) {
            throw new RuntimeException("Error storing complex value for " + resultId, e);
        }
    }

    public String storeResponse(String id, InputStream inputStream) {

        try {
            File responseTempFile;
            File responseFile;
            synchronized (storeResponseSerialNumberLock) {
                File responseDirectory = generateResponseDirectory(id);
                responseDirectory.mkdir();
                int responseIndex = findLatestResponseIndex(responseDirectory, true);
                if (responseIndex < 0) {
                    responseIndex = 0;
                }
                else {
                    responseIndex++;
                }
                responseFile = generateResponseFile(responseDirectory, responseIndex);
                responseTempFile = generateResponseTempFile(responseDirectory, responseIndex);
                try {
                    // create the file so that the reponse serial number is correctly
                    // incremented if this method is called again for this reponse
                    // before this reponse is completed.
                    responseTempFile.createNewFile();
                }
                catch (IOException e) {
                    throw new RuntimeException("Error storing response to {}", e);
                }
                LOGGER.debug("Creating temp file for {} as {}", id, responseTempFile.getPath());
            }
            InputStream responseInputStream = null;
            OutputStream responseOutputStream = null;
            try {
                responseInputStream = inputStream;
                responseOutputStream = new BufferedOutputStream(new FileOutputStream(responseTempFile));
                // In order to allow the prior response to be available we write
                // to a temp file and rename these when completed. Large responses
                // can cause the call below to take a significant amount of time.
                IOUtils.copy(responseInputStream, responseOutputStream);
            }
            finally {
                IOUtils.closeQuietly(responseInputStream);
                IOUtils.closeQuietly(responseOutputStream);
            }

            synchronized (storeResponseSerialNumberLock) {
                responseTempFile.renameTo(responseFile);
                LOGGER.debug("Renamed temp file for {} to {}", id, responseFile.getPath());
            }

            return id;//TODO

        }
        catch (FileNotFoundException e) {
            throw new RuntimeException("Error storing response for " + id, e);
        }
        catch (IOException e) {
            throw new RuntimeException("Error storing response for " + id, e);
        }
    }

    public void updateResponse(String id, InputStream inputStream) {
        this.storeResponse(id, inputStream);
    }

    public String getMimeTypeForStoreResponse(String id) {

        File responseDirectory = generateResponseDirectory(id);
        if (responseDirectory.exists()) {
            return "text/xml";
        }
        else {
            File mimeTypeFile = generateComplexDataMimeTypeFile(id);
            if (mimeTypeFile.canRead()) {
                InputStream mimeTypeInputStream = null;
                try {
                    mimeTypeInputStream = new FileInputStream(mimeTypeFile);
                    return IOUtils.toString(mimeTypeInputStream);
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                finally {
                    IOUtils.closeQuietly(mimeTypeInputStream);
                }
            }
        }
        return null;
    }

    public long getContentLengthForStoreResponse(String id) {

        File responseDirectory = generateResponseDirectory(id);
        if (responseDirectory.exists()) {
            synchronized (storeResponseSerialNumberLock) {
                File responseFile = findLatestResponseFile(responseDirectory);
                return responseFile.length();
            }
        }
        else {
            File contentLengthFile = generateComplexDataContentLengthFile(id);
            if (contentLengthFile.canRead()) {
                InputStream contentLengthInputStream = null;
                try {
                    contentLengthInputStream = new FileInputStream(contentLengthFile);
                    return Long.parseLong(IOUtils.toString(contentLengthInputStream));
                }
                catch (IOException e) {
                    LOGGER.error("Unable to extract content-length for response id {} from {}, exception message: {}",
                                 new Object[] {id, contentLengthFile.getAbsolutePath(), e.getMessage()});
                }
                catch (NumberFormatException e) {
                    LOGGER.error("Unable to parse content-length for response id {} from {}, exception message: {}",
                                 new Object[] {id, contentLengthFile.getAbsolutePath(), e.getMessage()});
                }
                finally {
                    IOUtils.closeQuietly(contentLengthInputStream);
                }
            }
            return -1;
        }
    }

    public boolean deleteStoredResponse(String id) {
        return false;
    }

    public InputStream lookupStatus(String request_id) throws ExceptionReport {
        File responseFile = lookupResponseAsFile(request_id);
        if (responseFile != null && responseFile.exists()) {
            LOGGER.debug("Response file for {} is {}", request_id, responseFile.getPath());
            try {

                InputStream inputStream = responseFile.getName().endsWith(SUFFIX_GZIP) ? new GZIPInputStream(new FileInputStream(responseFile)) : new FileInputStream(responseFile);

                /*
                 * Check if status doc
                 * Status docs and result docs are saved in the same folder and
                 * there is no other possibility to differentiate between them other than the following
                 */
                XmlObject object = null;
                String objectString = "";

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                String line = null;

                while((line = bufferedReader.readLine()) != null){
                    objectString = objectString.concat(line);
                }

                bufferedReader.close();

                try {
                    object = XmlObject.Factory.parse(objectString);
                } catch (XmlException e) {
                    LOGGER.error("Could not look up status. XMLException while trying to parse xml file.", e);
                    //check exception code
//                    throw new ExceptionReport("Status info for specified JobID not found.", ExceptionReport.NO_APPLICABLE_CODE, "JobID");
                }

                if (object != null && object instanceof StatusInfoDocument) {
                    return object.newInputStream();
                }else if(objectString.contains("html")){
                    return new ByteArrayInputStream(objectString.getBytes());
                } else {

                    LOGGER.info("Last response file not of type status info document.");

                    File responseDirectory = generateResponseDirectory(request_id);

                    int lastFileIndex = findLatestResponseIndex(responseDirectory, false);

                    File latestStatusFile = generateResponseFile(responseDirectory, lastFileIndex - 1);

                    inputStream = latestStatusFile.getName().endsWith(SUFFIX_GZIP) ? new GZIPInputStream(new FileInputStream(latestStatusFile)) : new FileInputStream(latestStatusFile);
                }
                return inputStream;
            } catch (FileNotFoundException ex) {
                // should never get here due to checks above...
                LOGGER.warn("Response not found for id {}", request_id);
            } catch (IOException ex) {
                LOGGER.warn("Error processing response for id {}", request_id);
            }
        }
        LOGGER.warn("Response not found for id {}", request_id);
        return null;
    }

    private int findLatestResponseIndex(File responseDirectory, boolean includeTemp) {
        int responseIndex = Integer.MIN_VALUE;
        for (File file : responseDirectory.listFiles()) {
            Matcher matcher = includeTemp ? PATTERN_RESPONSE_TEMP.matcher(file.getName())
                                         : PATTERN_RESPONSE.matcher(file.getName());
            if (matcher.matches()) {
                int fileIndex = Integer.parseInt(matcher.group(1));
                if (fileIndex > responseIndex) {
                    responseIndex = fileIndex;
                }
            }
        }
        return responseIndex;
    }

    private File findLatestResponseFile(File responseDirectory) {
        int responseIndex = findLatestResponseIndex(responseDirectory, false);
        return responseIndex < 0 ? null : generateResponseFile(responseDirectory, responseIndex);
    }

    private File generateResponseFile(File responseDirectory, int index) {
        return new File(responseDirectory, JOINER.join(index, SUFFIX_XML));
    }

    private File generateResponseTempFile(File responseDirectory, int index) {
        return new File(responseDirectory, JOINER.join(index, SUFFIX_XML, SUFFIX_TEMP));
    }

    private File generateResponseDirectory(String id) {
        return new File(baseDirectory, id);
    }

    private File generateComplexDataFile(String id, String mimeType, boolean gzip) {
        String fileName = gzip ? JOINER.join(id, MIMEUtil.getSuffixFromMIMEType(mimeType), SUFFIX_GZIP)
                              : JOINER.join(id, MIMEUtil.getSuffixFromMIMEType(mimeType));
        return new File(baseDirectory, fileName);
    }

    private File generateComplexDataMimeTypeFile(String id) {
        return new File(baseDirectory, JOINER.join(id, SUFFIX_MIMETYPE));
    }

    private File generateComplexDataContentLengthFile(String id) {
        return new File(baseDirectory, JOINER.join(id, SUFFIX_CONTENT_LENGTH));
    }

    public static FlatFileDatabase getInstance() {

        if(instance == null){
           instance = new FlatFileDatabase();
        }

        return instance;
    }

}
