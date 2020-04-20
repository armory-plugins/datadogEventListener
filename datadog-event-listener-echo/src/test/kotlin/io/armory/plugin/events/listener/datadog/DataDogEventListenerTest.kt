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
import com.netflix.spinnaker.echo.api.events.Metadata
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.*
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import okhttp3.RequestBody
import org.slf4j.Logger
import strikt.assertions.startsWith

class DataDogEventListenerTest : JUnit5Minutests {

    private val mapper = jacksonObjectMapper()

    class HttpMockableDataDogEventListener(
            val okHttpClient: OkHttpClient,
            val log: Logger) : DataDogEventListener(DataDogEventListenerConfig("asdf")) {

        override fun getHttpClient() : OkHttpClient {
            return okHttpClient
        }

        override fun getLogger() : Logger {
            return log
        }
    }

    private fun bodyToString(request: RequestBody): String {
        val buffer = okio.Buffer()
        request.writeTo(buffer)
        return buffer.readUtf8()
    }

    fun tests() = rootContext {

        test("process pipeline event") {
            val event = Event()
            event.eventId = "123"
            event.details = Metadata(
                    "orca",
                    "orca:task:complete",
                    "1583776971240",
                    null,
                    null,
                    "plugintest",
                    null,
                    null,
                    null
            )
            event.content = mapOf(
                    "execution" to mapOf(
                            "type" to "PIPELINE",
                            "id" to "01E307DBPNB1YJ9D0BW5X4NAEY",
                            "application" to "plugintest",
                            "name" to "testNewStageFromPlugin",
                            "status" to "RUNNING",
                            "pipelineConfigId" to "f514b57a-63af-4f5f-ac0a-2bc12d6c363b"
                    )
            )
            val okHttpClient = mockk<OkHttpClient> {
                every { newCall(any()) } returns mockk {
                    every { execute() } returns mockk {
                        every { isSuccessful } returns true
                    }
                }
            }
            val log = mockk<Logger>()

            val eventListener = HttpMockableDataDogEventListener(okHttpClient, log)
            eventListener.processEvent(event)

            verify(exactly = 1) {
                okHttpClient.newCall(
                        withArg {
                            expectThat(it.method()).isEqualTo("POST")
                            expectThat(it.url().toString()).isEqualTo("https://api.datadoghq.com/api/v1/events?api_key=asdf")
                            val dataDogEvent = mapper.readValue(bodyToString(it.body()!!), DataDogEvent::class.java)
                            expectThat(dataDogEvent.tags).isEqualTo(setOf(
                                    "source:orca",
                                    "eventType:orca:task:complete",
                                    "application:plugintest",
                                    "executionId:01E307DBPNB1YJ9D0BW5X4NAEY",
                                    "executionType:PIPELINE",
                                    "executionStatus:RUNNING",
                                    "pipelineName:testNewStageFromPlugin",
                                    "pipelineConfigId:f514b57a-63af-4f5f-ac0a-2bc12d6c363b"
                            ))
                            val actualJson = mapper.readValue(dataDogEvent.text, Map::class.java)
                            expectThat(actualJson).isEqualTo(mapOf(
                                    "details" to mapOf(
                                            "source" to "orca",
                                            "type" to "orca:task:complete",
                                            "created" to "1583776971240",
                                            "organization" to null,
                                            "project" to null,
                                            "application" to "plugintest",
                                            "_content_id" to null,
                                            "attributes" to null,
                                            "requestHeaders" to null
                                    ),
                                    "content" to mapOf(
                                            "execution" to mapOf(
                                                    "type" to "PIPELINE",
                                                    "id" to "01E307DBPNB1YJ9D0BW5X4NAEY",
                                                    "application" to "plugintest",
                                                    "name" to "testNewStageFromPlugin",
                                                    "status" to "RUNNING",
                                                    "pipelineConfigId" to "f514b57a-63af-4f5f-ac0a-2bc12d6c363b"
                                            )
                                    ),
                                    "rawContent" to null,
                                    "payload" to null,
                                    "eventId" to "123"
                            ))
                        })
            }
            verify { log wasNot Called }
        }

        test("process execution event") {
            val event = Event()
            event.eventId = "123"
            event.details = Metadata(
                    "orca",
                    "orca:orchestration:complete",
                    "1583776971240",
                    null,
                    null,
                    "plugintest",
                    null,
                    null,
                    null
            )
            event.content = mapOf(
                    "execution" to mapOf(
                            "type" to "ORCHESTRATION",
                            "id" to "01E307DBPNB1YJ9D0BW5X4NAEY",
                            "application" to "plugintest",
                            "name" to null,
                            "status" to "SUCCEEDED"
                    )
            )
            val okHttpClient = mockk<OkHttpClient> {
                every { newCall(any()) } returns mockk {
                    every { execute() } returns mockk {
                        every { isSuccessful } returns true
                    }
                }
            }
            val log = mockk<Logger>()

            val eventListener = HttpMockableDataDogEventListener(okHttpClient, log)
            eventListener.processEvent(event)

            verify(exactly = 1) {
                okHttpClient.newCall(
                        withArg {
                            expectThat(it.method()).isEqualTo("POST")
                            expectThat(it.url().toString()).isEqualTo("https://api.datadoghq.com/api/v1/events?api_key=asdf")
                            val dataDogEvent = mapper.readValue(bodyToString(it.body()!!), DataDogEvent::class.java)
                            expectThat(dataDogEvent.tags).isEqualTo(setOf(
                                    "source:orca",
                                    "eventType:orca:orchestration:complete",
                                    "application:plugintest",
                                    "executionId:01E307DBPNB1YJ9D0BW5X4NAEY",
                                    "executionType:ORCHESTRATION",
                                    "executionStatus:SUCCEEDED"
                            ))
                            val actualJson = mapper.readValue(dataDogEvent.text, Map::class.java)
                            expectThat(actualJson).isEqualTo(mapOf(
                                    "details" to mapOf(
                                            "source" to "orca",
                                            "type" to "orca:orchestration:complete",
                                            "created" to "1583776971240",
                                            "organization" to null,
                                            "project" to null,
                                            "application" to "plugintest",
                                            "_content_id" to null,
                                            "attributes" to null,
                                            "requestHeaders" to null
                                    ),
                                    "content" to mapOf(
                                            "execution" to mapOf(
                                                    "type" to "ORCHESTRATION",
                                                    "id" to "01E307DBPNB1YJ9D0BW5X4NAEY",
                                                    "application" to "plugintest",
                                                    "name" to null,
                                                    "status" to "SUCCEEDED"
                                            )
                                    ),
                                    "rawContent" to null,
                                    "payload" to null,
                                    "eventId" to "123"
                            ))
                        })
            }
            verify { log wasNot Called }
        }

        test("log error when processing execution event fails") {
            val event = Event()
            event.details = Metadata(
                    "orca",
                    "orca:orchestration:complete",
                    "1583776971240",
                    null,
                    null,
                    "plugintest",
                    null,
                    null,
                    null
            )
            event.content = mapOf(
                    "execution" to mapOf("type" to "ORCHESTRATION")
            )
            val okHttpClient = mockk<OkHttpClient>() {
                every { newCall(any()) } returns mockk {
                    every { execute() } returns mockk {
                        every { isSuccessful } returns false
                    }
                }
            }
            val log = mockk<Logger>(relaxed = true)

            val eventListener = HttpMockableDataDogEventListener(okHttpClient, log)
            eventListener.processEvent(event)

            verify(exactly = 1) {
                okHttpClient.newCall(any())
            }

            verify(exactly = 1) {
                log.error(
                        withArg {
                            expectThat(it).startsWith("DataDog event listener failed with response: ")
                        }
                )
            }
        }

    }
}

