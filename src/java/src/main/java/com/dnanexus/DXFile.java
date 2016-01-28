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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;

import com.dnanexus.DXHTTPRequest.RetryStrategy;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;

/**
 * A file (an opaque sequence of bytes).
 *
 */
public class DXFile extends DXDataObject {

<<<<<<< HEAD
	/**
	 * Builder class for creating a new {@code DXFile} object. To obtain an
	 * instance, call {@link DXFile#newFile()}.
	 */
	public static class Builder extends DXDataObject.Builder<Builder, DXFile> {
		private String media;
		private InputStream uploadData;

		private Builder() {
			super();
		}

		private Builder(DXEnvironment env) {
			super(env);
		}

		/**
		 * Creates the file.
		 *
		 * @return a {@code DXFile} object corresponding to the newly created
		 *         object
		 */
		@Override
		public DXFile build() {
			DXFile file = new DXFile(DXAPI.fileNew(this.buildRequestHash(), ObjectNewResponse.class, this.env).getId(),
					this.project, this.env, null);

			try {
				if (uploadData != null) {
					file.upload(uploadData);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			return file;
		}

		/**
		 * Use this method to test the JSON hash created by a particular builder
		 * call without actually executing the request.
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
		 * @param mediaType
		 *            Internet Media Type
		 *
		 * @return the same {@code Builder} object
		 */
		public Builder setMediaType(String mediaType) {
			Preconditions.checkState(this.media == null, "Cannot call setMediaType more than once");
			this.media = Preconditions.checkNotNull(mediaType, "mediaType may not be null");
			return getThisInstance();
		}

		/**
		 * Uploads the data in the specified byte array to file to be created
		 *
		 * @param data
		 * 				Data to be uploaded
		 *
		 * @return the same {@code Builder} object
		 */
		public Builder upload(byte[] data){
			InputStream dataStream = new ByteArrayInputStream(data);
			return this.upload(dataStream);
		}

		/**
		 * Uploads the data in the specified stream to the file to be created
		 *
		 * @param data
		 * 				Stream containing data to be uploaded
		 *
		 * @return the same {@code Builder} object
		 */
		public Builder upload(InputStream data){
			Preconditions.checkState(this.uploadData == null, "Cannot call upload more than once");
			this.uploadData = Preconditions.checkNotNull(data, "uploadData may not be null");
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
	 * @param value
	 *            JSON object map
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
	 * @throws NullPointerException
	 *             If {@code fileId} is null
	 */
	public static DXFile getInstance(String fileId) {
		return new DXFile(fileId, null);
	}

	/**
	 * Returns a {@code DXFile} associated with an existing file in a particular
	 * project or container.
	 *
	 * @throws NullPointerException
	 *             If {@code fileId} or {@code container} is null
	 */
	public static DXFile getInstance(String fileId, DXContainer project) {
		return new DXFile(fileId, project, null, null);
	}

	/**
	 * Returns a {@code DXFile} associated with an existing file in a particular
	 * project using the specified environment, with the specified cached
	 * describe output.
	 *
	 * <p>
	 * This method is for use exclusively by bindings to the "find" routes when
	 * describe hashes are returned with the find output.
	 * </p>
	 *
	 * @throws NullPointerException
	 *             If any argument is null
	 */
	static DXFile getInstanceWithCachedDescribe(String fileId, DXContainer project, DXEnvironment env,
			JsonNode describe) {
		return new DXFile(fileId, project, Preconditions.checkNotNull(env, "env may not be null"),
				Preconditions.checkNotNull(describe, "describe may not be null"));
	}

	/**
	 * Returns a {@code DXFile} associated with an existing file in a particular
	 * project using the specified environment.
	 *
	 * @throws NullPointerException
	 *             If {@code fileId} or {@code container} is null
	 */
	public static DXFile getInstanceWithEnvironment(String fileId, DXContainer project, DXEnvironment env) {
		return new DXFile(fileId, project, Preconditions.checkNotNull(env, "env may not be null"), null);
	}

	/**
	 * Returns a {@code DXFile} associated with an existing file using the
	 * specified environment.
	 *
	 * @throws NullPointerException
	 *             If {@code fileId} is null
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
	 * Returns a Builder object for creating a new {@code DXFile} using the
	 * specified environment.
	 *
	 * @param env
	 *            environment to use to make API calls
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

	/**
	 * Request to /file-xxxx/upload
	 */
	@JsonInclude(Include.NON_NULL)
	private static class fileUploadRequest {
		@JsonProperty
		private int size;
		@JsonProperty
		private String md5;

		private fileUploadRequest(int size, String md5) {
			this.size = size;
			this.md5 = md5;
		}
	}

	/**
	 * Request to /file-xxxx/upload
	 */
	@VisibleForTesting
    @JsonIgnoreProperties(ignoreUnknown = true)
	static class fileUploadResponse {
		@JsonProperty
		private String url;

		@JsonProperty
		private Map<String, String> headers;
	}

	/**
	 * Uploads byte array data to the file
	 *
	 * @param data
	 *            data to be uploaded
	 * @throws JsonProcessingException
	 */
	public void upload(byte[] data) throws JsonProcessingException {
		// MD5 digest as 32 character hex string
		String dataMD5 = DigestUtils.md5Hex(data);

		// API call returns URL and headers
		JsonNode output = apiCallOnObject("upload",
				MAPPER.valueToTree(new fileUploadRequest(data.length, dataMD5)),
				RetryStrategy.SAFE_TO_RETRY);

		fileUploadResponse apiResponse = MAPPER.treeToValue(output, fileUploadResponse.class);

		// Check that the content-length received by the apiserver is the same
		// as the length of the data
		Integer apiserverContentLength = Integer.parseInt(apiResponse.headers.get("content-length"));
		if (apiserverContentLength != data.length) {
			throw new AssertionError(
					"Content-length received by the apiserver did not match that of the input data");
		}

		// HTTP PUT request to upload URL and headers
		HttpPut request = new HttpPut(apiResponse.url);
		for (Map.Entry<String, String> header: apiResponse.headers.entrySet()) {
			String key = header.getKey();
			if (key.equals("content-length")) {
				continue;
			}
			request.setHeader(key, header.getValue());
		}

		request.setEntity(new ByteArrayEntity(data));

		HttpClient httpclient = HttpClientBuilder.create().setUserAgent(USER_AGENT).build();
		try {
			httpclient.execute(request);
		} catch (IOException e) {
			throw new RuntimeException();
		}
	}

	/**
	 * Uploads data from stream to the file
	 *
	 * @param data data from stream to be uploaded
	 * @throws IOException
	 */
	public void upload(InputStream data) throws IOException {
		this.upload(ByteStreams.toByteArray(data));
	}

	/**
	 * Request to /file-xxxx/download
	 */
	@JsonInclude(Include.NON_NULL)
	private static class fileDownloadRequest {
		@JsonProperty("preauthenticated")
		private boolean preauth;

		private fileDownloadRequest(boolean preauth) {
			this.preauth = preauth;
		}
	}

	/**
	 * Deserialized output from the /file-xxxx/download route.
	 */
	@VisibleForTesting
    @JsonIgnoreProperties(ignoreUnknown = true)
	static class fileDownloadResponse {
		@JsonProperty
		private String url;
	}

	/**
	 * Downloads data from file
	 *
	 * @return byte array containing data
	 * @throws IOException
	 */
	// TODO: set project ID containing the file to be downloaded
	public byte[] downloadBytes() throws IOException {
		// API call returns URL for HTTP GET requests
		JsonNode output = apiCallOnObject("download", MAPPER.valueToTree(new fileDownloadRequest(true)),
				RetryStrategy.SAFE_TO_RETRY);

		fileDownloadResponse apiResponse = MAPPER.treeToValue(output, fileDownloadResponse.class);

		// HTTP GET request to download URL
		HttpClient httpclient = HttpClientBuilder.create().setUserAgent(USER_AGENT).build();
		HttpGet request = new HttpGet(apiResponse.url);
		InputStream content = null;
		try {
			HttpResponse response = httpclient.execute(request);
			content = response.getEntity().getContent();
		} catch (IOException e) {
			throw new RuntimeException();
		}

		byte[] data = IOUtils.toByteArray(content);
		content.close();

		return data;
	}

	/**
	 * Downloads stream containing data from file
	 *
	 * @return stream containing data
	 * @throws IOException
	 */
	public ByteArrayOutputStream downloadStream() throws IOException {
		byte[] dataBytes = this.downloadBytes();
		ByteArrayOutputStream data = new ByteArrayOutputStream(dataBytes.length);
		data.write(dataBytes, 0, dataBytes.length);

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
		return DXJSON.safeTreeToValue(apiCallOnObject("describe", RetryStrategy.SAFE_TO_RETRY), Describe.class);
	}

	@Override
	public Describe describe(DescribeOptions options) {
		return DXJSON.safeTreeToValue(
				apiCallOnObject("describe", MAPPER.valueToTree(options), RetryStrategy.SAFE_TO_RETRY), Describe.class);
	}

	@Override
	public Describe getCachedDescribe() {
		this.checkCachedDescribeAvailable();
		return DXJSON.safeTreeToValue(this.cachedDescribe, Describe.class);
	}
}
