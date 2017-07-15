package com.guoxiaoxing.okhttp.demo

import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import java.io.IOException

class MainActivity : AppCompatActivity(), View.OnClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btn_send_get_request.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btn_send_get_request -> getAsynchronization("http://www.baidu.com")
        }
    }


    /**
     * 发送Get请求-异步
     */
    private fun getAsynchronization(url: String) {
        val okhttpClient: OkHttpClient = OkHttpClient.Builder().build()
        val request: Request = Request.Builder()
                .url(url)
                .build()
        okhttpClient.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call?, e: IOException?) {
                Log.d(App.TAG, e.toString())
            }

            override fun onResponse(call: Call?, response: Response?) {
                Log.d(App.TAG, response?.body()?.string())
            }
        })
    }

    /**
     * 发送Get请求-同步
     */
    private fun getSynchronization(url: String) {
        val okhttpClient: OkHttpClient = OkHttpClient.Builder().build()
        val request: Request = Request.Builder()
                .url(url)
                .build()
        val response: Response = okhttpClient.newCall(request).execute()
    }
}
