package com.guoxiaoxing.fresco.demo;

import android.app.Application;

import com.facebook.drawee.backends.pipeline.Fresco;

/**
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingse@163.com.
 *
 * @author guoxiaoxing
 * @since 2018/1/31 下午2:09
 */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Fresco.initialize(this);
    }
}
