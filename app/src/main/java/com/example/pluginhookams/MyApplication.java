package com.example.pluginhookams;

import android.app.Application;

/**
 * @author: 王硕风
 * @date: 2021.6.15 1:40
 * @Description:
 */
public class MyApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        HookUtils hookUtils = new HookUtils();
        hookUtils.hookStartActivity(this);
        hookUtils.hookHookmH();
    }
}
