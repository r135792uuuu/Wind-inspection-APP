package com.dji.mediaManagerDemo;

import android.app.Application;
import android.content.Context;

import com.secneo.sdk.Helper;

public class MApplication extends Application {

    private DemoApplication demoApplication;
    @Override
    protected void attachBaseContext(Context paramContext) {
        super.attachBaseContext(paramContext);
        Helper.install(MApplication.this); // 防止崩溃 每次构建app都要来一下
        if (demoApplication == null) {
            demoApplication = new DemoApplication(); // 从类定义（.h文件）DemoApplication.java里面实现定义  创建对象
            demoApplication.setContext(this);  // 把this赋值给demo的实例instance
        }
    }

    // 和fpv类无关，只是放在这个里面，第一次权限检查和SDKManager注册。注册成功后开启连接飞机页面  -> connecttionactivity.java
    @Override
    public void onCreate() {
        super.onCreate();
        demoApplication.onCreate();
    }
}
