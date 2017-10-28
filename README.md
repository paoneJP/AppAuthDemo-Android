Kotlin + AppAuth for Android ネイティブアプリ実装サンプル
=========================================================

これは OAuth 2.0 で保護されたバックエンドAPIを使用する Android ネイティブアプリの実装サンプルです。

ネイティブアプリでの OAuth 2.0 の実装については [RFC 8252][BCP 212] OAuth 2.0 for Native Apps として現時点のベストプラクティスがまとめられています。
また、そのプラクティスに沿った実装を支援するオープンソースライブラリ AppAuth が公開されており、今回はその Android 用のライブラリ AppAuth for Android を用いて実装してみます。


## 開発環境

 * Android Studio 3.0 Beta 7
 * Kotlin 1.1
 * AppAuth for Android 0.7.0
 * API Level 21以上 (Android 5.0以上)


## 実装されている機能

 * Google Accounts 使ったサインイン (OAuth 2.0 Authorization)
 * Google Accounts の UserInfo エンドポイントをバックエンドAPIに見立てたAPIアクセス
 * リフレッシュトークンを使ったアクセストークンの更新
 * アクセストークン、リフレッシュトークンの SharedPreferences への保存とその際の暗号化
 * その他補助的な機能として以下の2つを実装
    - サインアウト（アプリ内のトークンを破棄するだけ）
    - 強制的にアクセストークンを更新


## 遊び方

 * Google Cloud Platform の「APIとサービス」でプロジェクトを作成します。
 * 「認証情報を作成」で OAuthクライアントID を作成します。この時アプリケーションの種類は Android を選択します。
 * 発行されたクライアントIDを確認します。
  
 * Android Studio でこのディレクトリをプロジェクトとして開きます。
 * `MainActivity.kt` の `CLIENT_ID` の値に、先に確認したクライアントIDの値を設定し、ビルド、実行します。

取り急ぎ動作の確認をしたい方のために、ビルド済みの apk ファイルを `built` ディレクトリに収録しています。


## 操作

 * 「サインイン」
    - Google Accounts で認証を行ないアクセストークン、リフレッシュトークンを取得します。
    - AppAuth の内部状態を appAuthState (Summary) および appAuthState (Full) エリアに表示します。
 * 「API呼出し」
    - Google Accounts の UserInfo エンドポイントへAPIアクセスし、その結果を Response エリアに表示します。
 * 「認証状態表示」
    - AppAuth の内部状態を appAuthStatre (Summary) および appAuthState (Full) エリアに表示します。
 * 「サインアウト」
    - AppAuth の内部状態を初期化します。
 * 「トークン強制更新」
    - AppAuth の内部状態の `needsTokenRefresh` を `true` にしてAPI呼出しを実行します。
      アクセストークンが強制的に更新されてからAPIが呼び出されます。

ネットワークアクセスができない状況や、 Google Accouns の「アカウントにアクセスできるアプリ」でアクセス権を削除した状態などで動作を試してみると良いでしょう。


## 実装上のポイント

### 認証 (OAuth 2.0 Authorization) の要求

 * `startAuthorization()` を呼び出すことで、 Chrome Custom Tabs あるいは外部ブラウザが起動され、 Google Accounts の認証画面が表示されます。

### アクセストークンの取得

 * 認証が完了すると `redirect_uri` へ結果が返却されます。
 * `onActivityResult()` で `redirect_uri` に返された結果を受け取ります。
 * 受け取った結果を `handleAuthorizationResponse()` で処理し、アクセストークンの取得を完了します。
 * 認証の処理が成功すれば `whenAuthorizationSucceeds()` が、失敗すれば `whenAuthorizationFails()` が呼び出されます。
   
### バックエンドAPIへのアクセス

 * APIの呼出しは `httpGetJson()` で行ないます。
 * AppAuth の `performActionWithFreshTokens()` を使うことで、アクセストークンを自動更新しながらAPIアクセスを行ないます。
 * 実行結果は `httpGetJson()` の callback 引数に渡されたコールバック関数で処理します。
 * コールバック関数では、(1) API呼出しが成功、(2) API呼出し時にエラーが発生、(3) 再認証が必要な状態である の3つに場合分けをして処理を行なっています。

### アクセストークンの保存 (AppAuthの状態の保存)

 * 画面の切り替え等が生じたときのための状態の保存は `onSaveInstanceState()` で行ない、それを `onCreate()` で復元しています。
 * SharedPreferences への保存は `onPause()` で行ない、 `onCreate()` で `savedInstanceState` が無いときに SharedPreferences の内容を復元しています。
 * アクセストークン、リフレッシュトークンの暗号化の処理は `encryptString()` と `decryptString()` で行なっています。
 * 暗号の鍵管理には Android KeyStore System を使っています。 API Level により扱えるアルゴリズムに違いがあるため、 API Level 23 以上と API Level 21, 22 で動作を変えて対応しています。


## 参考サイト

 * [RFC 8252][BCP 212] OAuth 2.0 for Native Apps
    - https://datatracker.ietf.org/doc/rfc8252/
    - https://datatracker.ietf.org/doc/bcp212/
 * AppAuth for Android
    - https://openid.net/code/AppAuth
    - https://openid.net/code/AppAuth-Android


## ライセンス等

 * この実装サンプルのオリジナルは、以下の場所で MIT License で公開しています。
    - https://github.com/paoneJP/AppAuthDemo-Android
