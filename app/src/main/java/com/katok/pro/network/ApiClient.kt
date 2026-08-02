package com.katok.pro.network

import android.content.Context
import android.util.Log
import com.katok.pro.model.RefreshTokenRequest
import com.katok.pro.util.TokenManager
import com.katok.pro.workers.TokenRefreshScheduler
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

import okhttp3.Authenticator
import kotlinx.coroutines.runBlocking
import com.katok.pro.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout


class ApiClient private constructor() {

    interface AuthListener {
        fun onUnauthorized()
    }

    interface TokenRefreshListener {
        fun onTokenRefreshed(newAccessToken: String?)   // изменено на String?
    }

    interface UrlCheckCallback {
        fun onUrlReady(baseUrl: String)
        fun onError(error: String)
    }

    interface BaseUrlChangeListener {
        fun onBaseUrlChanged(newBaseUrl: String)
    }

    companion object {
        private val refreshScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        private const val LOCAL_BASE_URL = "http://192.168.0.119:8082/api/"
        private const val REMOTE_BASE_URL = "https://varamy.online/api-staging/"

        //Первый подключается глобальный адрес
        @Volatile
        private var currentBaseUrl: String = REMOTE_BASE_URL

        //Первый подключается локальный адрес
        /*@Volatile
        private var currentBaseUrl: String = LOCAL_BASE_URL*/

        @Volatile
        private var httpClient: OkHttpClient? = null

        @Volatile
        private var retrofit: Retrofit? = null
        private val lock = Object()
        private var tokenManager: TokenManager? = null
        private var appContext: Context? = null
        @Volatile
        private var isChecking = false
        private var baseUrlChangeListener: BaseUrlChangeListener? = null

        private val authListeners = mutableListOf<WeakReference<AuthListener>>()
        private val tokenRefreshListeners = mutableListOf<WeakReference<TokenRefreshListener>>()
        private val refreshMutex = Mutex()
        private var refreshTokenJob: Job? = null
        private var refreshTokenDeferred: CompletableDeferred<String?>? = null


        @JvmStatic
        fun addAuthListener(listener: AuthListener) {
            synchronized(authListeners) {
                authListeners.add(WeakReference(listener))
            }
        }

        @JvmStatic
        fun removeAuthListener(listener: AuthListener) {
            synchronized(authListeners) {
                authListeners.removeIf { ref -> ref.get() == listener }
            }
        }

        @JvmStatic
        fun addTokenRefreshListener(listener: TokenRefreshListener) {
            synchronized(tokenRefreshListeners) {
                tokenRefreshListeners.add(WeakReference(listener))
            }
        }

        @JvmStatic
        fun removeTokenRefreshListener(listener: TokenRefreshListener) {
            synchronized(tokenRefreshListeners) {
                tokenRefreshListeners.removeIf { ref -> ref.get() == listener }
            }
        }

        private fun notifyAuthUnauthorized() {
            synchronized(authListeners) {
                val iterator = authListeners.iterator()
                while (iterator.hasNext()) {
                    val ref = iterator.next()
                    val l = ref.get()
                    if (l == null) {
                        iterator.remove()
                    } else {
                        l.onUnauthorized()
                    }
                }
            }
        }

        private fun notifyTokenRefreshed(newToken: String?) {
            synchronized(tokenRefreshListeners) {
                val iterator = tokenRefreshListeners.iterator()
                while (iterator.hasNext()) {
                    val ref = iterator.next()
                    val l = ref.get()
                    if (l == null) {
                        iterator.remove()
                    } else {
                        l.onTokenRefreshed(newToken)
                    }
                }
            }
        }

        // Синхронное обновление токена (вызывается из перехватчика, работает в IO-потоке)
        internal suspend fun refreshAccessTokenAsync(): String? = withContext(Dispatchers.IO) {
            // Используем Mutex для безопасной синхронизации
            refreshMutex.withLock {
                // Если уже обновляется, ждём результат
                refreshTokenDeferred?.let { deferred ->
                    Log.d("ApiClient", "Token refresh already in progress, waiting...")
                    return@withLock deferred.await()
                }

                // Если обновление уже запланировано, но не началось?
                if (refreshTokenJob?.isActive == true) {
                    val deferred = CompletableDeferred<String?>()
                    refreshTokenDeferred = deferred
                    return@withLock deferred.await()
                }

                // Начинаем новое обновление
                val deferred = CompletableDeferred<String?>()
                refreshTokenDeferred = deferred
                refreshTokenJob = refreshScope.launch {
                    try {
                        val newToken = performRefreshTokenRequest()
                        deferred.complete(newToken)
                        newToken
                    } catch (e: Exception) {
                        Log.e("ApiClient", "Token refresh failed", e)
                        deferred.complete(null)
                        null
                    } finally {
                        refreshMutex.withLock {
                            refreshTokenJob = null
                            refreshTokenDeferred = null
                        }
                    }
                }
                deferred.await()
            }
        }

        // Собственно запрос на обновление (блокирующий, но уже внутри корутины)
        private suspend fun performRefreshTokenRequest(): String? {
            val refreshToken = tokenManager?.getRefreshToken() ?: return null

            val request = RefreshTokenRequest(refreshToken)

            val response = withTimeoutOrNull(10000L) {
                createTempApiService().refreshToken(request)
            } ?: return null

            if (response.isSuccessful && response.body()?.isSuccess == true) {
                val loginData = response.body()?.data
                if (loginData != null && loginData.accessToken != null) {
                    val newToken = loginData.accessToken
                    tokenManager?.saveTokens(newToken, loginData.refreshToken)
                    appContext?.let { TokenRefreshScheduler.scheduleNextRefresh(it) }
                    notifyTokenRefreshed(newToken)
                    return newToken
                }
            }
            Log.e("ApiClient", "Token refresh failed: ${response.code()}")
            return null
        }

        private fun createTempApiService(): ApiService {
            val refreshClient = createSimpleOkHttpClient()
            val tempRetrofit = Retrofit.Builder()
                .baseUrl(currentBaseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .client(refreshClient)
                .build()
            return tempRetrofit.create(ApiService::class.java)
        }

        @JvmStatic
        fun init(context: Context) {
            tokenManager = TokenManager.getInstance(context)
            appContext = context.applicationContext
            synchronized(lock) {
                httpClient = createOkHttpClient()
                retrofit = Retrofit.Builder()
                    .baseUrl(currentBaseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(httpClient!!)
                    .build()
            }
        }

        private fun createOkHttpClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor()
            if (isDebuggable()) {
                logging.setLevel(HttpLoggingInterceptor.Level.BODY)
            } else {
                logging.setLevel(HttpLoggingInterceptor.Level.NONE)
            }

            val authInterceptor = Interceptor { chain ->
                val original = chain.request()
                val token = tokenManager?.getAccessToken()
                if (token != null && token.isNotEmpty()) {
                    val request = original.newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                    chain.proceed(request)
                } else {
                    chain.proceed(original)
                }
            }

            // Authenticator для автоматического обновления токена (без дополнительной синхронизации,
            // так как refreshAccessToken уже @Synchronized)
            val authenticator = Authenticator { _, response ->
                if (response.code == 401) {
                    var newToken: String? = null
                    runBlocking {
                        try {
                            // Добавляем таймаут 5 секунд, чтобы не блокировать надолго
                            withTimeout(5000L) {
                                newToken = refreshAccessTokenAsync()
                            }
                        } catch (e: TimeoutCancellationException) {
                            Log.e("ApiClient", "Token refresh timeout", e)
                        } catch (e: Exception) {
                            Log.e("ApiClient", "Token refresh error", e)
                        }
                    }
                    if (newToken != null) {
                        response.request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    } else {
                        notifyAuthUnauthorized()
                        null
                    }
                } else {
                    null
                }
            }

            val builder = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor(authInterceptor)
                .authenticator(authenticator)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)

            // Для отладки с локальным сервером (только debug сборка)
            if (BuildConfig.DEBUG && currentBaseUrl.contains("192.168")) {
                try {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, getTrustAllCerts(), java.security.SecureRandom())
                    val sslSocketFactory = sslContext.socketFactory
                    builder.sslSocketFactory(sslSocketFactory, getTrustAllCerts()[0] as X509TrustManager)
                    builder.hostnameVerifier { _, _ -> true }
                    if (BuildConfig.LOG_ENABLED) {
                        Log.w("ApiClient", "⚠️ Using unsafe SSL for local server (DEBUG only)")
                    }
                } catch (e: Exception) {
                    if (BuildConfig.LOG_ENABLED) {
                        Log.e("ApiClient", "SSL setup error", e)
                    }
                }
            }

            return builder.build()
        }

        @JvmStatic
        fun setBaseUrl(baseUrl: String) {
            synchronized(lock) {
                if (baseUrl != currentBaseUrl) {
                    currentBaseUrl = baseUrl
                    httpClient = createOkHttpClient()
                    retrofit = Retrofit.Builder()
                        .baseUrl(currentBaseUrl)
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(httpClient!!)
                        .build()
                    if (BuildConfig.LOG_ENABLED) {
                        Log.d("ApiClient", "Base URL changed to: $currentBaseUrl")
                    }

                    baseUrlChangeListener?.onBaseUrlChanged(currentBaseUrl)
                }
            }
        }

        @JvmStatic
        fun getCurrentBaseUrl(): String = currentBaseUrl

        @JvmStatic
        fun getApiService(): ApiService {
            if (retrofit == null) {
                synchronized(lock) {
                    if (retrofit == null) {
                        httpClient = createOkHttpClient()
                        retrofit = Retrofit.Builder()
                            .baseUrl(currentBaseUrl)
                            .addConverterFactory(GsonConverterFactory.create())
                            .client(httpClient!!)
                            .build()
                    }
                }
            }
            return retrofit!!.create(ApiService::class.java)
        }

        @JvmStatic
        fun getCurrentWebSocketUrl(): String {
            var base = currentBaseUrl

            // Заменяем http(s) на ws(s)
            val webSocketProtocol = when {
                base.startsWith("https://") -> "wss://"
                base.startsWith("http://") -> "ws://"
                else -> "ws://" // fallback
            }
            // Удаляем протокол, оставляя только хост и порт
            var hostPortPath = base.replace(Regex("^https?://"), "")

            // Если есть путь, содержащий "/api", убираем его вместе со всем, что после него (до порта)
            val apiIndex = hostPortPath.indexOf("/api")
            if (apiIndex != -1) {
                hostPortPath = hostPortPath.substring(0, apiIndex)
            } else {
                // Если нет /api, но есть другие пути (например, /), убираем всё после хоста/порта
                val slashIndex = hostPortPath.indexOf("/")
                if (slashIndex != -1) {
                    hostPortPath = hostPortPath.substring(0, slashIndex)
                }
            }

            // Убираем завершающий слеш, если есть
            if (hostPortPath.endsWith("/")) {
                hostPortPath = hostPortPath.substring(0, hostPortPath.length - 1)
            }

            // Собираем итоговый URL
            return "$webSocketProtocol$hostPortPath/ws"
        }

        private fun getTrustAllCerts(): Array<TrustManager> {
            return arrayOf(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                }
            )
        }

        @JvmStatic
        fun checkServerAvailability(callback: UrlCheckCallback?) {
            if (isChecking) return
            isChecking = true

            CoroutineScope(Dispatchers.IO).launch {
                var globalReachable = false
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build()
                    val request = Request.Builder()
                        .url(REMOTE_BASE_URL + "ads/all")
                        .build()
                    val response = client.newCall(request).execute()
                    globalReachable = response.isSuccessful
                    response.close()
                } catch (e: Exception) {
                    if (BuildConfig.LOG_ENABLED) {
                        Log.d("ApiClient", "Global server not reachable: ${e.message}")
                    }
                }

                if (globalReachable) {
                    if (BuildConfig.LOG_ENABLED) {
                        Log.d("ApiClient", "✅ Global server reachable: $REMOTE_BASE_URL")
                    }
                    setBaseUrl(REMOTE_BASE_URL)
                    callback?.onUrlReady(REMOTE_BASE_URL)
                } else {
                    try {
                        val client = OkHttpClient.Builder()
                            .connectTimeout(10, TimeUnit.SECONDS)
                            .readTimeout(10, TimeUnit.SECONDS)
                            .build()
                        val request = Request.Builder()
                            .url(LOCAL_BASE_URL + "ads/all")
                            .build()
                        val response = client.newCall(request).execute()
                        val localReachable = response.isSuccessful
                        response.close()
                        if (localReachable) {
                            Log.d("ApiClient", "✅ Local server reachable: $LOCAL_BASE_URL")
                            setBaseUrl(LOCAL_BASE_URL)
                            callback?.onUrlReady(LOCAL_BASE_URL)
                        } else {
                            Log.d("ApiClient", "❌ Both servers unreachable, using remote as fallback")
                            setBaseUrl(REMOTE_BASE_URL)
                            callback?.onUrlReady(REMOTE_BASE_URL)
                        }
                    } catch (e: Exception) {
                        Log.d("ApiClient", "Local server not reachable: ${e.message}")
                        setBaseUrl(REMOTE_BASE_URL)
                        callback?.onUrlReady(REMOTE_BASE_URL)
                    }
                }
                isChecking = false
            }
        }

        @JvmStatic
        fun normalizeResourceUrl(url: String?): String? {
            if (url == null || url.isEmpty()) return url

            val baseWithoutTrailingSlash = currentBaseUrl.trimEnd('/')

            // Если URL уже начинается с baseUrl, возвращаем как есть
            if (url.startsWith(baseWithoutTrailingSlash)) return url

            try {
                val parsed = java.net.URL(url)
                var path = parsed.path
                // Убираем ведущий слеш
                if (path.startsWith("/")) {
                    path = path.substring(1)
                }
                // Если путь начинается с "api/", убираем этот префикс (чтобы не дублировать)
                if (path.startsWith("api/")) {
                    path = path.substring(4) // убираем "api/"
                }
                // Также если путь начинается с "api-staging/", убираем его (если такой случай)
                if (path.startsWith("api-staging/")) {
                    path = path.substring(12) // убираем "api-staging/"
                }

                val base = currentBaseUrl.trimEnd('/')
                return "$base/$path"
            } catch (e: Exception) {
                // Если парсинг не удался, пробуем просто заменить
                return url
            }
        }

        @JvmStatic
        fun getOkHttpClient(): OkHttpClient {
            synchronized(lock) {
                if (httpClient == null) {
                    httpClient = createOkHttpClient()
                }
                return httpClient!!
            }
        }

        @JvmStatic
        fun setBaseUrlChangeListener(listener: BaseUrlChangeListener?) {
            baseUrlChangeListener = listener

        }

        @JvmStatic
        fun getAppContext(): Context? = appContext

        private fun isDebuggable(): Boolean {
            return appContext != null && (appContext!!.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }

        private fun createSimpleOkHttpClient(): OkHttpClient {
            val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)

            if (BuildConfig.DEBUG && currentBaseUrl.contains("192.168")) {
                try {
                    val sslContext = SSLContext.getInstance("TLS")
                    sslContext.init(null, getTrustAllCerts(), java.security.SecureRandom())
                    val sslSocketFactory = sslContext.socketFactory
                    builder.sslSocketFactory(sslSocketFactory, getTrustAllCerts()[0] as X509TrustManager)
                    builder.hostnameVerifier { _, _ -> true }
                } catch (e: Exception) {
                    if (BuildConfig.LOG_ENABLED) {
                        Log.e("ApiClient", "SSL setup error for refresh client", e)
                    }
                }
            }
            return builder.build()
        }

        @JvmStatic
        fun refreshTokenSynchronously(): Boolean {
            return runBlocking {
                refreshAccessTokenAsync() != null
            }
        }
    }
}
