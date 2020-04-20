/*
 * Copyright 2020 Armory, Inc.
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
package io.armory.plugin.events.listener.datadog

import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.EventListener
import org.slf4j.LoggerFactory
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.Logger

class DataDogEventListenerPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {
    private val logger = LoggerFactory.getLogger(DataDogEventListenerPlugin::class.java)

    override fun start() {
        logger.info("DataDogEventListenerPlugin.start()")
    }

    override fun stop() {
        logger.info("DataDogEventListenerPlugin.stop()")
    }
}

data class DataDogEvent(
        val title: String,
        val text: String,
        val priority: String,
        val tags: Set<String>,
        val alert_type: String
)

@Extension
open class DataDogEventListener(val configuration: DataDogEventListenerConfig) : EventListener {

    private val log = LoggerFactory.getLogger(DataDogEventListener::class.java)

    private val mapper = jacksonObjectMapper()

    protected open fun getHttpClient() : OkHttpClient {
        return OkHttpClient()
    }

    protected open fun getLogger() : Logger {
        return log
    }

    override fun processEvent(event: Event) {

        val tags = mutableSetOf(
                "source:${event.details.source}",
                "eventType:${event.details.type}",
                "application:${event.details.application}"
        )
        val execution = event.content["execution"] as Map<String, Any?>
        tags.add("executionType:${execution["type"]}")
        tags.add("executionStatus:${execution["status"]}")
        tags.add("executionId:${execution["id"]}")
        execution["name"]?.let {
            tags.add("pipelineName:$it")
        }
        execution["pipelineConfigId"]?.let {
            tags.add("pipelineConfigId:$it")
        }

        val dataDogEvent = DataDogEvent(
                "Spinnaker Event",
                mapper.writeValueAsString(event),
                "normal",
                tags,
                "info"
        )
        val dataDogEventJson = mapper.writeValueAsString(dataDogEvent)
        val dataDogUrl = "https://api.datadoghq.com/api/v1/events"

        val body = RequestBody.create(
                MediaType.parse("application/json"), dataDogEventJson)
        val request = Request.Builder()
                .url("$dataDogUrl?api_key=${configuration.apiKey}")
                .post(body)
                .build()
        val call = getHttpClient().newCall(request)
        val response = call.execute()

        if (!response.isSuccessful) {
            getLogger().error("DataDog event listener failed with response: ${response.toString()}")
        }
    }
}
