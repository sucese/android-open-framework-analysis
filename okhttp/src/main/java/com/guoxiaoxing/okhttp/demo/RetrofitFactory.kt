package com.guoxiaoxing.okhttp.demo

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingse@163.com.

 * @author guoxiaoxing
 * *
 * @since 2017/7/15 下午3:44
 */
object RetrofitFactory {

    private val TIMEOUT: Long = 60

    fun getInstance() {
        val okhtt: OkHttpClient = OkHttpClient.Builder()
                .readTimeout(TIMEOUT, TimeUnit.SECONDS)
                .writeTimeout(TIMEOUT, TimeUnit.SECONDS)
                .build()
    }
}
