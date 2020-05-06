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

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.netflix.spinnaker.echo.api.events.Event
import com.netflix.spinnaker.echo.api.events.EventListener
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

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

    private val dataDogUrl = "https://api.datadoghq.com/"

    private val log = LoggerFactory.getLogger(DataDogEventListener::class.java)

    private val mapper = jacksonObjectMapper()

    private var datadogClient: DatadogClient? = null

    protected open fun getDataDogClient() : DatadogClient {
        if (datadogClient == null) {
            val retrofit = Retrofit.Builder()
                    .baseUrl(dataDogUrl)
                    .addConverterFactory(JacksonConverterFactory.create())
                    .build()
            datadogClient = retrofit.create(DatadogClient::class.java)
        }
        return datadogClient!!
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

        event.content["execution"]?.let {
            val execution = it as Map<String, Any?>
            tags.add("executionType:${execution["type"]}")
            tags.add("executionStatus:${execution["status"]}")
            tags.add("executionId:${execution["id"]}")
            execution["name"]?.let {
                tags.add("pipelineName:$it")
            }
            execution["pipelineConfigId"]?.let {
                tags.add("pipelineConfigId:$it")
            }
        }

        val dataDogEvent = DataDogEvent(
                "Spinnaker Event",
                mapper.writeValueAsString(event),
                "normal",
                tags,
                "info"
        )

        val response = getDataDogClient().sendEvent(configuration.apiKey, dataDogEvent).execute()
        if (!response.isSuccessful) {
            getLogger().error("DataDog event listener failed with response: ${response.code()} - ${response.message()}")
        }
    }
}
