/*
 *  AppAuth for Android demonstration application.
 *    Author: Takashi Yahata (@paoneJP)
 *    Copyright: (c) 2017 Takashi Yahata
 *    License: MIT License
 */

package net.paonejp.kndzyb.appauthdemo

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import net.openid.appauth.*
import net.paonejp.kndzyb.appauthdemo.util.HttpRequestJsonTask
import net.paonejp.kndzyb.appauthdemo.util.decryptString
import net.paonejp.kndzyb.appauthdemo.util.encryptString
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection.*
import java.text.DateFormat
import java.util.*


private val ISSUER_URI = "https://accounts.google.com"
private val CLIENT_ID = "999999999999-xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx.apps.googleusercontent.com"
private val REDIRECT_URI = "net.paonejp.kndzyb.appauthdemo:/cb"

private val SCOPE = "profile"
private val API_URI = "https://www.googleapis.com/oauth2/v3/userinfo"

private val REQCODE_AUTH = 100
private val X_HTTP_NEED_REAUTHZ = -1
private val X_HTTP_ERROR = -9

private val LOG_TAG = "MainActivity"


class MainActivity : AppCompatActivity() {

    private lateinit var appAuthState: AuthState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState != null) {
            try {
                appAuthState = savedInstanceState
                        .getString("appAuthState", "{}")
                        .let { AuthState.jsonDeserialize(it) }
            } catch (ex: JSONException) {
                val m = Throwable().stackTrace[0]
                Log.e(LOG_TAG, "${m}: ${ex}")
                appAuthState = AuthState()
            }
        } else {
            val prefs = getSharedPreferences("appAuthPreference", MODE_PRIVATE)
            val data = decryptString(this, prefs.getString("appAuthState", null)) ?: "{}"
            appAuthState = AuthState.jsonDeserialize(data)
        }

        if (savedInstanceState != null) {
            uAppAuthStateView.text = savedInstanceState.getCharSequence("appAuthStateView")
            uResponseView.text = savedInstanceState.getCharSequence("responseView")
            uAppAuthStateFullView.text = savedInstanceState.getCharSequence("appAuthStateFullView")
        } else {
            doShowAppAuthState()
            uResponseView.text = getText(R.string.msg_app_start)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQCODE_AUTH) {
            handleAuthorizationResponse(data)
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)

        outState?.putString("appAuthState", appAuthState.jsonSerializeString())

        outState?.putCharSequence("appAuthStateView", uAppAuthStateView.text)
        outState?.putCharSequence("responseView", uResponseView.text)
        outState?.putCharSequence("appAuthStateFullView", uAppAuthStateFullView.text)
    }

    override fun onPause() {
        super.onPause()

        getSharedPreferences("appAuthPreference", MODE_PRIVATE)
                .edit()
                .putString("appAuthState", encryptString(this, appAuthState.jsonSerializeString()))
                .apply()
    }


    fun onClickSigninButton(view: View) {
        startAuthorization()
    }

    fun onClickSignoutButton(view: View) {
        revokeAuthorization()
    }

    fun onClickCallApiButton(view: View) {
        showApiResult()
    }

    fun onClickShowStatusButton(view: View) {
        showAppAuthStatus()
    }

    fun onClickTokenRefreshButton(view: View) {
        tokenRefreshAndShowApiResult()
    }


    private fun startAuthorization() {
        AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(ISSUER_URI), { config, ex ->
            if (config != null) {
                val req = AuthorizationRequest
                        .Builder(config, CLIENT_ID, ResponseTypeValues.CODE, Uri.parse(REDIRECT_URI))
                        .setScope(SCOPE)
                        .build()
                val intent = AuthorizationService(this).getAuthorizationRequestIntent(req)
                startActivityForResult(intent, REQCODE_AUTH)
            } else {
                if (ex != null) {
                    val m = Throwable().stackTrace[0]
                    Log.e(LOG_TAG, "${m}: ${ex}")
                }
                whenAuthorizationFails(ex)
            }
        })
    }

    private fun handleAuthorizationResponse(data: Intent?) {
        if (data == null) {
            val m = Throwable().stackTrace[0]
            Log.e(LOG_TAG, "${m}: unexpected intent call")
            return
        }

        val resp = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        appAuthState.update(resp, ex)

        if (ex != null || resp == null) {
            val m = Throwable().stackTrace[0]
            Log.e(LOG_TAG, "${m}: ${ex}")
            whenAuthorizationFails(ex)
            return
        }

        AuthorizationService(this)
                .performTokenRequest(resp.createTokenExchangeRequest(), { resp2, ex2 ->
                    appAuthState.update(resp2, ex2)
                    if (resp2 != null) {
                        whenAuthorizationSucceeds()
                    } else {
                        whenAuthorizationFails(ex2)
                    }
                })
    }

    // 認証成功時の処理を書く。
    // Write program to be executed when authorization succeeds.
    private fun whenAuthorizationSucceeds() {
        uResponseView.text = getText(R.string.msg_auth_ok)
        doShowAppAuthState()
    }

    // 認証エラー時の処理を書く。
    // Write program to be executed when authorization fails.
    private fun whenAuthorizationFails(ex: AuthorizationException?) {
        uResponseView.text = "%s\n\n%s".format(getText(R.string.msg_auth_ng), ex?.message)
        doShowAppAuthState()
    }


    private fun revokeAuthorization() {
        val uri = appAuthState.authorizationServiceConfiguration?.discoveryDoc?.docJson
                ?.opt("revocation_endpoint") as String?

        if (uri == null) {
            appAuthState = AuthState()
            whenRevokeAuthorizationSucceeds()
            return
        }

        val param = "token=${appAuthState.refreshToken}&token_type_hint=refresh_token"
        HttpRequestJsonTask(uri, param, null, { code, data, ex ->
            when (code) {
                HTTP_OK -> {
                    appAuthState = AuthState()
                    whenRevokeAuthorizationSucceeds()
                }

                HTTP_BAD_REQUEST -> {

                    // RFC 7009 に示されているように、すでに無効なトークンの無効化リクエストに
                    // 対しサーバーは HTTP 200 を応答するが、一部のサーバーはエラーを応答する
                    // ことがある。Google Accounts の場合、 HTTP 400 で "invalid_token" エラー
                    // を返すため、それを成功応答として処理する。
                    // As described in RFC 7009, the server responds with HTTP 200 for revocation
                    // request to already invalidated token, but some servers may respond with an
                    // error. Google Accounts returns "invalid_token" error with HTTP 400, it must
                    // be treated as a successful response.
                    if (data?.optString("error") == "invalid_token") {
                        appAuthState = AuthState()
                        whenRevokeAuthorizationSucceeds()
                        return@HttpRequestJsonTask
                    }

                    val msg = "Server returned HTTP response code: %d for URL: %s with message: %s"
                            .format(code, uri, data.toString())
                    whenRevokeAuthorizationFails(IOException(msg))
                }

                else -> whenRevokeAuthorizationFails(ex)
            }
        }).execute()
    }

    // 認証状態取り消し時の処理を書く。
    // Write program to be executed when revoking authorization succeeds.
    private fun whenRevokeAuthorizationSucceeds() {
        uResponseView.text = getText(R.string.msg_auth_revoke_ok)
        doShowAppAuthState()
    }

    // 認証状態取り消し時の処理を書く。
    // Write program to be executed when revoking authorization fails.
    private fun whenRevokeAuthorizationFails(ex: Exception?) {
        uResponseView.text = "%s\n\n%s"
                .format(getText(R.string.msg_auth_revoke_ng), ex ?: "")
        doShowAppAuthState()
    }


    private fun showAppAuthStatus() {
        uResponseView.text = getText(R.string.msg_show_auth_state)
        doShowAppAuthState()
    }


    private fun doShowAppAuthState() {
        val t = appAuthState
                .accessTokenExpirationTime
                ?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
        uAppAuthStateView.text = JSONObject()
                .putOpt("isAuthorized", appAuthState.isAuthorized)
                .putOpt("accessToken", appAuthState.accessToken)
                .putOpt("accessTokenExpirationTime", appAuthState.accessTokenExpirationTime)
                .putOpt("accessTokenExpirationTime_readable", t)
                .putOpt("refreshToken", appAuthState.refreshToken)
                .putOpt("needsTokenRefresh", appAuthState.needsTokenRefresh)
                .toString(2)
                .replace("\\/", "/")
        uAppAuthStateFullView.text = appAuthState
                .jsonSerialize()
                .toString(2)
                .replace("\\/", "/")
        uScrollView.scrollY = 0
    }


    // APIを呼び出す処理を書く。
    // Write program calling the API.
    private fun showApiResult() {
        httpGetJson(API_URI, { code, data, ex -> showApiResultCallback(code, data, ex) })
    }

    private fun tokenRefreshAndShowApiResult() {
        appAuthState.needsTokenRefresh = true
        showApiResult()
    }

    // APIレスポンスに対する処理を書く。
    // Write program to be executed when API responded.
    private fun showApiResultCallback(code: Int, data: JSONObject?, ex: Exception?) {
        when (code) {
            X_HTTP_NEED_REAUTHZ, HTTP_UNAUTHORIZED -> whenReauthorizationRequired(ex)

            HTTP_OK -> {
                uResponseView.text = "%s\n\n%s"
                        .format(getText(R.string.msg_api_ok),
                                data?.toString(2)?.replace("\\/", "/"))
            }

            else -> {
                uResponseView.text = "%s\n\n%d\n%s\n%s"
                        .format(getText(R.string.msg_api_error),
                                code, data ?: "", ex ?: "")
            }
        }
        doShowAppAuthState()
    }

    // 再認証が必要な状態の時の処理を書く。
    // Write program to be executed when reauthorization required.
    private fun whenReauthorizationRequired(ex: Exception?) {
        uResponseView.text = "%s\n\n%s"
                .format(getText(R.string.msg_reauthz_required), ex ?: "")
        doShowAppAuthState()
    }


    private fun httpGetJson(uri: String,
                            callback: (code: Int, json: JSONObject?, ex: Exception?) -> Unit) {
        val service = AuthorizationService(this)
        appAuthState.performActionWithFreshTokens(service, { accessToken, _, ex ->
            if (ex != null) {
                val m = Throwable().stackTrace[0]
                Log.e(LOG_TAG, "${m}: ${ex}")
                if (appAuthState.isAuthorized) {
                    callback(X_HTTP_ERROR, null, ex)
                } else {
                    callback(X_HTTP_NEED_REAUTHZ, null, ex)
                }
            } else {
                if (accessToken == null) {
                    callback(X_HTTP_ERROR, null, null)
                } else {
                    HttpRequestJsonTask(uri, null, accessToken, { code, data, ex2 ->
                        callback(code, data, ex2)
                    }).execute()
                }
            }
        })
    }

}
