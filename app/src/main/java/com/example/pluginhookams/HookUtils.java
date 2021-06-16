package com.example.pluginhookams;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * @author: 王硕风
 * @date: 2021.6.14 22:29
 * @Description: 无
 * <p> 重点全适配，并学习其博客
 * 各版本适配：https://www.jianshu.com/p/eb772e50c690
 * 代码地址：https://github.com/18598925736/ActivityHookDemo/tree/startActivityWithoutRegiste
 * <p>
 * Activity启动流程：https://blog.csdn.net/kai_zone/article/details/81530126
 * <p>
 * Android9.0 Activity启动流程分析（一）:https://blog.csdn.net/caiyu_09/article/details/83505340
 * Android9.0 Activity启动流程分析（二）:https://blog.csdn.net/caiyu_09/article/details/84634599
 * Android9.0 Activity启动流程分析（三）:https://blog.csdn.net/caiyu_09/article/details/84837544
 * <p>
 * Android P Activity启动代码流程：https://blog.csdn.net/llleahdizon/article/details/89947580
 */
public class HookUtils {
    private static final String TAG = HookUtils.class.getCanonicalName();
    private Context context;

    /**
     * hook到ActivityManagerNative的gDefault对象(Singleton)，获取Singleton的mInstance变量，就是拿到IActivityManager对象
     * 最终调用IActivityManager的startActivity方法
     */
    public void hookStartActivity(Context context) {
        this.context = context;
        //还原gDefault对象
        try {
            Field gDefaule = null;
            Class<?> iActivityManager = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //Android Q 10.0 29
                Class<?> ActivityTaskManagerCls = Class.forName("android.app.ActivityTaskManager");
                gDefaule = ActivityTaskManagerCls.getDeclaredField("IActivityTaskManagerSingleton");
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //Android O 8.0 26 和  Android P 9.0
                Class<?> activityManagerNativeCls = Class.forName("android.app.ActivityManager");
                gDefaule = activityManagerNativeCls.getDeclaredField("IActivityManagerSingleton");
            } else {
                Class<?> ActivityManagerCls = Class.forName("android.app.ActivityManagerNative");
                gDefaule = ActivityManagerCls.getDeclaredField("gDefault");
            }

            if (gDefaule != null) {
                gDefaule.setAccessible(true);
            }

            //因为是静态变量，所以获取到的是系统值
            Object defaultValue = gDefaule.get(null);

            //获取mInstance对象
            Class<?> singletonCls = Class.forName("android.util.Singleton");
            Field mInstance = singletonCls.getDeclaredField("mInstance");
            mInstance.setAccessible(true);
            //还原IActivityManager对象，是个系统对象
            Object iActivityManagerObject = mInstance.get(defaultValue);

            //动态代理(IActivityManagerProxy代理系统的IActivityManager)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //Android Q 10.0 29
                iActivityManager = Class.forName("android.app.IActivityTaskManager");
            } else {
                iActivityManager = Class.forName("android.app.IActivityManager");
            }
            IActivityManagerProxy proxy = new IActivityManagerProxy(iActivityManagerObject);
            Object proxyIActivityManager = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(), new Class[]{iActivityManager}, proxy);

            //将系统的IActivityManager 替换成 自己通过动态代理实现的oldIActivityManager对象,该对象实现了IActivityManager的所有方法
            mInstance.set(defaultValue, proxyIActivityManager);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class IActivityManagerProxy implements InvocationHandler {
        //系统IActivityManager对象
        private Object iActivityManager;

        public IActivityManagerProxy(Object iActivityManager) {
            this.iActivityManager = iActivityManager;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Intent oldIntent = null;
            //   代理对象    方法    参数
            Log.d(TAG, "invoke：" + method.getName());
            if ("startActivity".equals(method.getName())) {
                Log.d(TAG, "-------hook---------startActivity-----------------");
                //获取要启动的Intent
                int index = 0;// 记录一下是第几个参数
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Intent) {
                        index = i;
                        oldIntent = (Intent) args[i];
                    }
                }
                Intent newIntent = new Intent();
                // 清单文件只有ProxyActivity是注册的，其他activity是没有注册的。
                // 为什么要用ProxyActivity 因为这一步是要绕过AMS的检查，清单文件注册了的
                newIntent.setComponent(new ComponentName(context, ProxyActivity.class));
                newIntent.putExtra("oldIntent", oldIntent);

                //将真实的Intent隐藏起来创建新的 然后给系统
                args[index] = newIntent; // 然后给原样放进去
            }
            return method.invoke(iActivityManager, args);
        }
    }

    public void hookHookmH() {
        try {
            Class<?> forName = Class.forName("android.app.ActivityThread");
            Field currentActivityThreadField = forName.getDeclaredField("sCurrentActivityThread");
            currentActivityThreadField.setAccessible(true);
            //还原系统的ActivityThread对象
            Object activityThreadObj = currentActivityThreadField.get(null);
            Field handlerField = forName.getDeclaredField("mH");
            handlerField.setAccessible(true);
            Handler mH = (Handler) handlerField.get(activityThreadObj);
            //因为Handler中有接口，这里直接使用接口   在dispatchMessage方法中调用handleMessage
            //我们需要给callback赋值，这样才会执行我们自己的handleMessage方法，但是callback只在Handler构造中赋值
            //因此我们不能直接new Handler  而要通过反射设置Callback
            Field callbackField = Handler.class.getDeclaredField("mCallback");

            callbackField.setAccessible(true);

            Handler.Callback callback;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                callback = new Activity159MH(mH);
            } else {
                callback = new Activity100MH(mH);
            }

            //设置callback
            callbackField.set(mH, callback);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class Activity100MH implements Handler.Callback {
        private Handler mH;

        public Activity100MH(Handler mH) {
            this.mH = mH;
        }

        @Override
        public boolean handleMessage(Message msg) {
            //Android P 9.0以上是159,9.0以下是100
            //Android P Activity启动流程分析:https://blog.csdn.net/caiyu_09/article/details/84837544

            if (msg.what == 100) {
                //即将要加载activity
                //我们自己加工
                handle100LaunchActivity(msg);
            }
            //加工完丢给系统处理,做真正的跳转
            mH.handleMessage(msg);
            return true;
        }

        private void handle100LaunchActivity(Message msg) {
            //还原Intent    msg的obj中有intent成员变量
            Object obj = msg.obj;
            try {
                Field intentField = obj.getClass().getDeclaredField("intent");
                intentField.setAccessible(true);
                //代表ProxyActivity的Intent
                Intent realIntent = (Intent) intentField.get(obj);

                Intent oldIntent = realIntent.getParcelableExtra("oldIntent");
                if (oldIntent != null) {
                    //集中式登陆
                    SharedPreferences share = context.getSharedPreferences("david",
                            Context.MODE_PRIVATE);
                    if (share.getBoolean("login", false)) {
                        //登陆成功,原来的目标
                        realIntent.setComponent(oldIntent.getComponent());
                    } else {
                        ComponentName componentName = new ComponentName(context, LoginActivity.class);
                        realIntent.putExtra("extraIntent", oldIntent.getComponent().getClassName());
                        realIntent.setComponent(componentName);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class Activity159MH implements Handler.Callback {
        private Handler mH;

        public Activity159MH(Handler mH) {
            this.mH = mH;
        }

        @Override
        public boolean handleMessage(Message msg) {
            //Android P 9.0以上是159,9.0以下是100   https://www.jianshu.com/p/eb772e50c690
            if (msg.what == 159) {
                //即将要加载activity
                //我们自己加工
                handle159LaunchActivity(msg);
            }
            //加工完丢给系统处理,做真正的跳转
            mH.handleMessage(msg);
            return true;
        }

        private void handle159LaunchActivity(Message msg) {
            //还原Intent    msg的obj中有intent成员变量
            Object obj = msg.obj;
            try {
                //先把相关@hide的类都建好
                Class<?> ClientTransactionClz = Class.forName("android.app.servertransaction.ClientTransaction");
                Class<?> LaunchActivityItemClz = Class.forName("android.app.servertransaction.LaunchActivityItem");

                Field mActivityCallbacksField = ClientTransactionClz.getDeclaredField("mActivityCallbacks");//ClientTransaction的成员
                mActivityCallbacksField.setAccessible(true);
                //类型判定，好习惯
                if (!ClientTransactionClz.isInstance(msg.obj)) return;
                Object mActivityCallbacksObj = mActivityCallbacksField.get(msg.obj);//根据源码，在这个分支里面,msg.obj就是 ClientTransaction类型,所以，直接用
                //拿到了ClientTransaction的List<ClientTransactionItem> mActivityCallbacks;
                List list = (List) mActivityCallbacksObj;

                if (list.size() == 0) return;
                Object LaunchActivityItemObj = list.get(0);//所以这里直接就拿到第一个就好了

                if (!LaunchActivityItemClz.isInstance(LaunchActivityItemObj)) return;
                //这里必须判定 LaunchActivityItemClz，
                // 因为 最初的ActivityResultItem传进去之后都被转化成了这LaunchActivityItemClz的实例

                Field mIntentField = LaunchActivityItemClz.getDeclaredField("mIntent");
                mIntentField.setAccessible(true);
                //代表ProxyActivity的Intent
                Intent realIntent = (Intent) mIntentField.get(obj);

                Intent oldIntent = realIntent.getParcelableExtra("oldIntent");
                if (oldIntent != null) {
                    //集中式登陆
                    SharedPreferences share = context.getSharedPreferences("david",
                            Context.MODE_PRIVATE);
                    if (share.getBoolean("login", false)) {
                        //登陆成功,原来的目标
                        realIntent.setComponent(oldIntent.getComponent());
                    } else {
                        ComponentName componentName = new ComponentName(context, LoginActivity.class);
                        realIntent.putExtra("extraIntent", oldIntent.getComponent().getClassName());
                        realIntent.setComponent(componentName);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
