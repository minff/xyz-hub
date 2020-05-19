/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package com.here.xyz.hub.rest.admin;

import java.util.concurrent.ConcurrentHashMap;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.util.IOUtils;

/**
 * The {@link S3WebMessageBroker} extends the {@link WebMessageBroker} abstract.
 * 
 * To use the {@link S3WebMessageBroker} you can use the java property
 * "AdminMessageBroker={@link S3WebMessageBroker}" or set the environment
 * variable "ADMIN_MESSAGE_BROKER={@link S3WebMessageBroker}".
 * 
 * The {@link S3WebMessageBroker} must be configured. You can use the java
 * properties "com.here.xyz.hub.rest.admin.S3WebMessageBroker.BUCKET",
 * "com.here.xyz.hub.rest.admin.S3WebMessageBroker.OBJECT",
 * "com.here.xyz.hub.rest.admin.S3WebMessageBroker.PERIODIC_UPDATE" and
 * "com.here.xyz.hub.rest.admin.S3WebMessageBroker.PERIODIC_UPDATE_DELAY" or set
 * the environment variables "S3_WEB_MESSAGE_BROKER_BUCKET",
 * "S3_WEB_MESSAGE_BROKER_OBJECT", "S3_WEB_MESSAGE_BROKER_PERIODIC_UPDATE" and
 * "S3_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY".
 * 
 */

public class S3WebMessageBroker extends WebMessageBroker {

	private static volatile S3WebMessageBroker instance;
	private static volatile AmazonS3 S3_CLIENT;
	private static volatile String BUCKET;
	private static volatile String OBJECT;
	private static volatile Boolean PERIODIC_UPDATE;
	private static volatile Integer PERIODIC_UPDATE_DELAY;
	private static volatile Boolean isInitialized;

	static {
		BUCKET = getConfig("S3_WEB_MESSAGE_BROKER_BUCKET", S3WebMessageBroker.class.getName() + ".BUCKET",
				"xyz-hub-admin-messaging");
		OBJECT = getConfig("S3_WEB_MESSAGE_BROKER_OBJECT", S3WebMessageBroker.class.getName() + ".OBJECT",
				"instances.json");
		PERIODIC_UPDATE = Boolean.valueOf(getConfig("S3_WEB_MESSAGE_BROKER_PERIODIC_UPDATE",
				S3WebMessageBroker.class.getName() + ".PERIODIC_UPDATE", "false"));
		PERIODIC_UPDATE_DELAY = Integer.parseInt(getConfig("S3_WEB_MESSAGE_BROKER_PERIODIC_UPDATE_DELAY",
				S3WebMessageBroker.class.getName() + ".PERIODIC_UPDATE_DELAY", "0"));
		try {
			S3_CLIENT = AmazonS3ClientBuilder.standard().withCredentials(new DefaultAWSCredentialsProviderChain())
					.build();
			isInitialized = true;
			logger.info("The S3WebMessageBroker was initialized.");
		} catch (Exception e) {
			logger.warn("Initializing the S3WebMessageBroker failed with error: {} ", e.getMessage());
			S3_CLIENT = null;
			PERIODIC_UPDATE = false;
			PERIODIC_UPDATE_DELAY = 0;
			isInitialized = false;
		}
		instance = new S3WebMessageBroker();
	}

	@Override
	protected Boolean isInitialized() {
		return isInitialized;
	}

	@Override
	protected Boolean getPeriodicUpdate() {
		return PERIODIC_UPDATE;
	}

	@Override
	protected Integer getPeriodicUpdateDelay() {
		return PERIODIC_UPDATE_DELAY;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected ConcurrentHashMap<String, String> getTargetEndpoints() throws Exception {
		return mapper.get().readValue(new String(IOUtils.toByteArray(S3_CLIENT.getObject(BUCKET, OBJECT).getObjectContent())), ConcurrentHashMap.class);
	}

	static S3WebMessageBroker getInstance() {
		return instance;
	}

}
