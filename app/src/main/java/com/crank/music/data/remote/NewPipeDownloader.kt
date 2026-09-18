package com.crank.music.data.remote

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloader : Downloader() {
    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = httpMethod
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true

            headers.forEach { (headerName, headerValues) ->
                headerValues.forEach { headerValue ->
                    addRequestProperty(headerName, headerValue)
                }
            }

            if (dataToSend != null && dataToSend.isNotEmpty()) {
                doOutput = true
                outputStream.use { os ->
                    os.write(dataToSend)
                }
            }
        }

        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage ?: ""
        val responseHeaders = connection.headerFields
            ?.filterKeys { it != null }
            ?.mapValues { it.value ?: emptyList() } ?: emptyMap()

        val responseBody = try {
            val inputStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }

        val latestUrl = connection.url.toString()

        return Response(responseCode, responseMessage, responseHeaders, responseBody, latestUrl)
    }
}
