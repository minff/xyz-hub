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

/**
 * The {@link StaticWebMessageBroker} extends the {@link WebMessageBroker}
 * abstract.
 * 
 * To use the {@link StaticWebMessageBroker} you can use the java property
 * "AdminMessageBroker={@link StaticWebMessageBroker}" or set the environment
 * variable "ADMIN_MESSAGE_BROKER={@link StaticWebMessageBroker}".
 * 
 * The {@link StaticWebMessageBroker} must be configured. You can use the java
 * property "com.here.xyz.hub.rest.admin.StaticWebMessageBroker.CONFIG" or set
 * the environment variable "STATIC_WEB_MESSAGE_BROKER_CONFIG" to a json string,
 * e.g. { "instance": "port", "instance": "port", ... }.
 * 
 */

public class StaticWebMessageBroker extends WebMessageBroker {

	private static volatile StaticWebMessageBroker instance;
	private static volatile String CONFIG;
	private static volatile Boolean isInitialized;

	static {
		CONFIG = getConfig("STATIC_WEB_MESSAGE_BROKER_CONFIG", StaticWebMessageBroker.class.getName() + ".CONFIG",
				"{}");
		isInitialized = true;
		logger.info("The StaticWebMessageBroker was initialized.");
		instance = new StaticWebMessageBroker();
	}

	@Override
	protected Boolean isInitialized() {
		return isInitialized;
	}

	@Override
	protected Boolean getPeriodicUpdate() {
		return false;
	}

	@Override
	protected Integer getPeriodicUpdateDelay() {
		return 0;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected ConcurrentHashMap<String, String> getTargetEndpoints() throws Exception {
		return mapper.get().readValue(CONFIG, ConcurrentHashMap.class);
	}

	static StaticWebMessageBroker getInstance() {
		return instance;
	}

}
