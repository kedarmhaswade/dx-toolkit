// Copyright (C) 2013-2015 DNAnexus, Inc.
//
// This file is part of dx-toolkit (DNAnexus platform client libraries).
//
//   Licensed under the Apache License, Version 2.0 (the "License"); you may
//   not use this file except in compliance with the License. You may obtain a
//   copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
//   WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
//   License for the specific language governing permissions and limitations
//   under the License.

package com.dnanexus;

import java.io.*;
import java.lang.*;
import java.nio.charset.Charset;
import java.security.*;
import java.util.*;

import com.dnanexus.DXHTTPRequest.RetryStrategy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.StringEntity;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

/**
 * A file (an opaque sequence of bytes).
 *
 * <p>
 * Note that these Java bindings do not supply any high-level way to upload or download data to or
 * from a file. Please use the command-line tools <code>dx upload</code> and
 * <code>dx download</code>, or see the <a
 * href="https://wiki.dnanexus.com/API-Specification-v1.0.0/Files">API documentation for files</a>.
 * </p>
 */
public class DXFile extends DXDataObject {

    /**
     * Builder class for creating a new {@code DXFile} object. To obtain an instance, call
     * {@link DXFile#newFile()}.
     */
    public static class Builder extends DXDataObject.Builder<Builder, DXFile> {
        private String media;

        private Builder() {
            super();
        }

        private Builder(DXEnvironment env) {
            super(env);
        }

        /**
         * Creates the file.
         *
         * @return a {@code DXFile} object corresponding to the newly created object
         */
        @Override
        public DXFile build() {
            return new DXFile(DXAPI.fileNew(this.buildRequestHash(), ObjectNewResponse.class,
                    this.env).getId(), this.project, this.env, null);
        }

        /**
         * Use this method to test the JSON hash created by a particular builder call without
         * actually executing the request.
         *
         * @return a JsonNode
         */
        @VisibleForTesting
        JsonNode buildRequestHash() {
            checkAndFixParameters();
            return MAPPER.valueToTree(new FileNewRequest(this));
        }

        /*
         * (non-Javadoc)
         *
         * @see com.dnanexus.DXDataObject.Builder#getThisInstance()
         */
        @Override
        protected Builder getThisInstance() {
            return this;
        }

        /**
         * Sets the Internet Media Type of the file to be created.
         *
         * @param mediaType Internet Media Type
         *
         * @return the same {@code Builder} object
         */
        public Builder setMediaType(String mediaType) {
            Preconditions.checkState(this.media == null, "Cannot call setMediaType more than once");
            this.media = Preconditions.checkNotNull(mediaType, "mediaType may not be null");
            return getThisInstance();
        }

    }

    /**
     * Contains metadata for a file.
     */
    public static class Describe extends DXDataObject.Describe {
        @JsonProperty
        private String media;

        private Describe() {
            super();
        }

        /**
         * Returns the Internet Media Type of the file.
         *
         * @return Internet Media Type
         */
        public String getMediaType() {
            Preconditions.checkState(this.media != null,
                    "media type is not accessible because it was not retrieved with the describe call");
            return media;
        }
    }

    @JsonInclude(Include.NON_NULL)
    private static class FileNewRequest extends DataObjectNewRequest {
        @JsonProperty
        private final String media;

        public FileNewRequest(Builder builder) {
            super(builder);
            this.media = builder.media;
        }
    }

    /**
     * Deserializes a DXFile from JSON containing a DNAnexus link.
     *
     * @param value JSON object map
     *
     * @return data object
     */
    @JsonCreator
    private static DXFile create(Map<String, Object> value) {
        checkDXLinkFormat(value);
        // TODO: how to set the environment?
        return DXFile.getInstance((String) value.get("$dnanexus_link"));
    }

    /**
     * Returns a {@code DXFile} associated with an existing file.
     *
     * @throws NullPointerException If {@code fileId} is null
     */
    public static DXFile getInstance(String fileId) {
        return new DXFile(fileId, null);
    }

    /**
     * Returns a {@code DXFile} associated with an existing file in a particular project or
     * container.
     *
     * @throws NullPointerException If {@code fileId} or {@code container} is null
     */
    public static DXFile getInstance(String fileId, DXContainer project) {
        return new DXFile(fileId, project, null, null);
    }

    /**
     * Returns a {@code DXFile} associated with an existing file in a particular project using the
     * specified environment, with the specified cached describe output.
     *
     * <p>
     * This method is for use exclusively by bindings to the "find" routes when describe hashes are
     * returned with the find output.
     * </p>
     *
     * @throws NullPointerException If any argument is null
     */
    static DXFile getInstanceWithCachedDescribe(String fileId, DXContainer project,
            DXEnvironment env, JsonNode describe) {
        return new DXFile(fileId, project, Preconditions.checkNotNull(env, "env may not be null"),
                Preconditions.checkNotNull(describe, "describe may not be null"));
    }

    /**
     * Returns a {@code DXFile} associated with an existing file in a particular project using the
     * specified environment.
     *
     * @throws NullPointerException If {@code fileId} or {@code container} is null
     */
    public static DXFile getInstanceWithEnvironment(String fileId, DXContainer project,
            DXEnvironment env) {
        return new DXFile(fileId, project, Preconditions.checkNotNull(env, "env may not be null"),
                null);
    }

    /**
     * Returns a {@code DXFile} associated with an existing file using the specified environment.
     *
     * @throws NullPointerException If {@code fileId} is null
     */
    public static DXFile getInstanceWithEnvironment(String fileId, DXEnvironment env) {
        return new DXFile(fileId, Preconditions.checkNotNull(env, "env may not be null"));
    }

    /**
     * Returns a Builder object for creating a new {@code DXFile}.
     *
     * @return a newly initialized builder object
     */
    public static Builder newFile() {
        return new Builder();
    }

    /**
     * Returns a Builder object for creating a new {@code DXFile} using the specified environment.
     *
     * @param env environment to use to make API calls
     *
     * @return a newly initialized builder object
     */
    public static Builder newFileWithEnvironment(DXEnvironment env) {
        return new Builder(env);
    }

    private DXFile(String fileId, DXContainer project, DXEnvironment env, JsonNode describe) {
        super(fileId, "file", project, env, describe);
    }

    private DXFile(String fileId, DXEnvironment env) {
        super(fileId, "file", env, null);
    }

    private static final String USER_AGENT = DXUserAgent.getUserAgent();

    public void upload(byte[] data) {
        HttpClient httpclient = HttpClientBuilder.create().setUserAgent(USER_AGENT).build();

        // Inputs to /file-xxxx/upload API call
        Map inputValues = new HashMap();
        inputValues.put("size", data.length);
        String dataMD5 = new String();
        dataMD5 = DigestUtils.md5Hex(data); // MD5 digest as 32 character hex string
        inputValues.put("md5", dataMD5);
        JsonNode input = MAPPER.valueToTree(inputValues);

        // API call returns URL and headers
        JsonNode output = apiCallOnObject("upload", input, RetryStrategy.SAFE_TO_RETRY);

        String url = new String();
        try {
            url = MAPPER.writeValueAsString(output.get("url"));
            url = url.replace("\"", "");
        } catch(JsonProcessingException e) {
            System.err.println(e);
        }

        // HTTP PUT request to upload URL
        HttpPut request = new HttpPut(url);
        Iterator<Map.Entry<String, JsonNode>> iterator = output.get("headers").fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> headers = iterator.next();
            try {
                String key = headers.getKey();

                // "content-length" field already in headers, so skip over
                if (key == "content-length") continue;

                String value = MAPPER.writeValueAsString(headers.getValue());
                value = value.replace("\"", "");

                request.setHeader(key, value);
            } catch (JsonProcessingException e) {
                System.err.println(e);
            }
        }
        String stringData = new String(data);
        request.setEntity(new StringEntity(stringData, Charset.forName("UTF-8")));

        try {
            HttpResponse response = httpclient.execute(request);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //this.closeAndWait();
    }

    // TODO: set project ID containing the file to be downloaded
    public byte[] download() {
        HttpClient httpclient = HttpClientBuilder.create().setUserAgent(USER_AGENT).build();

        // Inputs to /file-xxxx/download API call
        Map inputValues = new HashMap();
        inputValues.put("preauthenticated", true);
        JsonNode input = MAPPER.valueToTree(inputValues);

        // API call returns URL for HTTP GET requests
        JsonNode output = apiCallOnObject("download", input, RetryStrategy.SAFE_TO_RETRY);

        String url = new String();
        try {
            url = MAPPER.writeValueAsString(output.get("url"));
            url = url.replace("\"", "");
        } catch(JsonProcessingException e) {
            System.err.println(e);
        }

        // HTTP GET request to download URL
        HttpGet request = new HttpGet(url);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            HttpResponse response = httpclient.execute(request);
            InputStream content = response.getEntity().getContent();
            try {
                IOUtils.copy(content, baos);
            } finally {
                content.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] data = baos.toByteArray();

        return data;
    }

    @Override
    public DXFile close() {
        super.close();
        return this;
    }

    @Override
    public DXFile closeAndWait() {
        super.closeAndWait();
        return this;
    }

    @Override
    public Describe describe() {
        return DXJSON.safeTreeToValue(apiCallOnObject("describe", RetryStrategy.SAFE_TO_RETRY),
                Describe.class);
    }

    @Override
    public Describe describe(DescribeOptions options) {
        return DXJSON.safeTreeToValue(
                apiCallOnObject("describe", MAPPER.valueToTree(options),
                        RetryStrategy.SAFE_TO_RETRY), Describe.class);
    }

    @Override
    public Describe getCachedDescribe() {
        this.checkCachedDescribeAvailable();
        return DXJSON.safeTreeToValue(this.cachedDescribe, Describe.class);
    }

}
