package com.guoxiaoxing.virtualapk.host;

import android.app.Application;
import android.content.Context;

import com.didi.virtualapk.PluginManager;

/**
 * For more information, you can visit https://github.com/guoxiaoxing or contact me by
 * guoxiaoxingse@163.com.
 *
 * @author guoxiaoxing
 * @since 2018/2/10 上午10:16
 */
public class App extends Application{

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        PluginManager.getInstance(base).init();
    }
}
