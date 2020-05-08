/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

/**
 * The MessageBroker provides the infrastructural implementation of how to send
 * & receive {@link AdminMessage}s.
 * 
 * NOTE: The {@link MessageBroker#getInstance()} method decides which
 * implementation to return.
 * 
 * The default {@link MessageBroker} implementation is the {@link SnsMessageBroker}.
 * 
 * To set the {@link MessageBroker} you can use the java property
 * "AdminMessageBroker={@link HttpMessageBroker}" or set the environment
 * variable "ADMIN_MESSAGE_BROKER={@link HttpMessageBroker}".
 * 
 */
public interface MessageBroker {

  void sendMessage(AdminMessage message);

  void receiveRawMessage(byte[] rawJsonMessage);

  static MessageBroker getInstance() {
    switch (System.getenv("ADMIN_MESSAGE_BROKER") != null ? System.getenv("ADMIN_MESSAGE_BROKER")
        : System.getProperty("AdminMessageBroker", "SnsMessageBroker")) {
      case "HttpMessageBroker":
        return HttpMessageBroker.getInstance();
      case "SnsMessageBroker":
      default:
        return SnsMessageBroker.getInstance();
    }
  }

}
