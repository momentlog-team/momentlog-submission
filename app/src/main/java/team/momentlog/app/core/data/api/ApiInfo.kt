package team.momentlog.app.core.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import team.momentlog.app.BuildConfig
import team.momentlog.app.Constant
import java.util.concurrent.TimeUnit

/** 서버 주소와 Retrofit 인스턴스. 주소는 `app/build.gradle.kts`의 `SEAL_SERVER`. */
object ApiInfo {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(Constant.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constant.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("${BuildConfig.SEAL_SERVER}/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    fun <T> create(service: Class<T>): T = retrofit.create(service)
}
