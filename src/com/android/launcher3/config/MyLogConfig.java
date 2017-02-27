package com.android.launcher3.config;

import android.util.Log;

/**
 * Created by penglin.zhao on 2016/12/21.
 */
public class MyLogConfig {

    /**
     * 这个是调试5.0 平台的allapp界面的log
     */
    public static String ALLAPP = "zhaoall";

    /**
     * 这个是CTS的那个
     * 调试CTS先关功能的tag
     */
    public static String CTS = "zhaocts";

    /**
     * 这个状态位的
     * 调试界面切换时用的，查看切换到不同界面时的状态用的
     */
    public static String state = "zhao11state";

    /**
     * 这个状态位的
     * 调试切换语言桌面消失
     */
    public static String debug = "zhao11statedebug";
    /**
     * 这个是抛异常的变量
     */
    public static boolean thorwErr = true;

    /**
     * 这个是不抛异常的变量
     */
    public static boolean noThorwErr = false;


    public static void i(String tag,String msg){
        Log.i(tag,msg);
    }

    public static void w(String tag,String msg){
        Log.w(tag, msg);
    }

    public static void e(String tag,String msg){
        Log.e(tag,msg);
    }

}
