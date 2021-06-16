package com.example.pluginhookams;

import android.app.Application;
import android.content.Context;

import me.weishu.reflection.Reflection;

/**
 * @author: 王硕风
 * @date: 2021.6.15 1:40
 * @Description: 无
 *
 * <p>重点：版本适配：https://www.jianshu.com/p/eb772e50c690
 * <p>
 * 另一种绕过Android P非公开API限制(反射限制)：https://blog.csdn.net/heng615975867/article/details/104939454
 * 开源库FreeReflection：https://github.com/tiann/FreeReflection/
 */
public class MyApplication extends Application {

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        //Android P反射限制
        Reflection.unseal(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        HookUtils hookUtils = new HookUtils();
        hookUtils.hookStartActivity(this);
        hookUtils.hookHookmH();
    }
}
