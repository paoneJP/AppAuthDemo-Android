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
private val CODE_NONE = -1


class HttpGetJsonTask(
        private val uri: String,
        private val accessToken: String,
        private val callback: (Int, JSONObject?, Exception?) -> Unit)
    : AsyncTask<Void, Void, HttpGetJsonTask.Response>() {

    class Response(val code: Int, val json: JSONObject?, val ex: Exception?)

    override fun doInBackground(vararg p0: Void?): Response {

        try {
            val conn = URL(uri).openConnection() as HttpURLConnection
            conn.addRequestProperty("Authorization", "Bearer ${accessToken}")
            conn.readTimeout = NETWORK_TIMEOUT_MSEC

            var body: String
            try {
                conn.connect()
                body = conn.inputStream.bufferedReader().readText()
            } catch (ex: FileNotFoundException) {
                body = conn.errorStream.bufferedReader().readText()
            }

            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                val m = Throwable().stackTrace[0]
                Log.e("HttpGetJsonTask", "${m}: ${conn.responseCode}, ${body}")
            }
            return Response(conn.responseCode, JSONObject(body), null)

        } catch (ex: Exception) {
            val m = Throwable().stackTrace[0]
            Log.e("HttpGetJsonTask", "${m}: ${ex}")
            return Response(CODE_NONE, null, ex)
        }
    }

    override fun onPostExecute(resp: Response) {
        callback(resp.code, resp.json, resp.ex)
    }

}