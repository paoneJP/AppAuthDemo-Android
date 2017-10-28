/*
 *  AppAuth for Android demonstration application.
 *    Author: Takashi Yahata (@paoneJP)
 *    Copyright: (c) 2017 Takashi Yahata
 *    License: MIT License
 */

package net.paonejp.kndzyb.appauthdemo.util

import android.os.AsyncTask
import android.util.Log
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL


private val NETWORK_TIMEOUT_MSEC = 5000
private val X_HTTP_ERROR = -9


class HttpRequestJsonTask(
        private val uri: String,
        private val data: String?,
        private val accessToken: String?,
        private val callback: (Int, JSONObject?, Exception?) -> Unit)
    : AsyncTask<Void, Void, HttpRequestJsonTask.Response>() {

    class Response(val code: Int, val json: JSONObject?, val ex: Exception?)

    override fun doInBackground(vararg p0: Void?): Response {

        try {
            val conn = URL(uri).openConnection() as HttpURLConnection
            conn.connectTimeout = NETWORK_TIMEOUT_MSEC
            conn.readTimeout = NETWORK_TIMEOUT_MSEC
            if (data != null) {
                conn.requestMethod = "POST"
                conn.outputStream.write(data.toByteArray())
            }
            if (accessToken != null) {
                conn.addRequestProperty("Authorization", "Bearer ${accessToken}")
            }

            var body: String
            try {
                conn.connect()
                body = conn.inputStream.bufferedReader().readText()
            } catch (ex: FileNotFoundException) {
                body = conn.errorStream.bufferedReader().readText()
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                val m = Throwable().stackTrace[0]
                Log.e("HttpRequestJsonTask", "${m}: ${conn.responseCode}, ${body}")
            }
            return Response(conn.responseCode, JSONObject(body), null)

        } catch (ex: Exception) {
            val m = Throwable().stackTrace[0]
            Log.e("HttpRequestJsonTask", "${m}: ${ex}")
            return Response(X_HTTP_ERROR, null, ex)
        }
    }

    override fun onPostExecute(resp: Response) {
        callback(resp.code, resp.json, resp.ex)
    }

}