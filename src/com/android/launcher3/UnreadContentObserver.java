package com.android.launcher3;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Telephony;
import android.util.Log;
import android.widget.Toast;

import com.android.launcher3.config.ProviderConfig;

/**
 * Created by penglin.zhao on 2016/7/12.
 */
public class UnreadContentObserver {
    Context mcontext;
    private static UnreadContentObserver unreadContentObserver;
    private static final String TAGzhao = "zhao11unread.UnreadContentObserver";
    private  UnreadContentObserver(Context context){
        mcontext = context;
        registerObserver();
    }

    public static UnreadContentObserver getUnreadContentObserver(Context context){
        if (unreadContentObserver == null){
            unreadContentObserver = new UnreadContentObserver(context);
        }
        return unreadContentObserver;
    }
    /**
     * 一、未读短信
     *首先注册Observer，当有新短信或彩信来的时候会调用 onChange方法，
     *我们可以在onChange方法中去获取未读短信和彩信，然后做一些UI上的处理！
     */
    int oldeNum = -1;
    int mNewSmsCount = 0;
    private ContentObserver newMmsContentObserver = new ContentObserver(new Handler()) {
        public void onChange(boolean selfChange) {
            mNewSmsCount = getNewSmsCount() + getNewMmsCount();
            Log.i(TAGzhao,"mNewSmsCount:"+mNewSmsCount+",oldeNum:"+oldeNum);
            if(mNewSmsCount != oldeNum){
                oldeNum = mNewSmsCount;
                Log.i(TAGzhao,"getUnreadMms发送广播了："+ mNewSmsCount);
                broadcastUnreadMessageNumber(mcontext, mNewSmsCount);
                Toast.makeText(mcontext,"未读短信数："+mNewSmsCount,Toast.LENGTH_SHORT).show();
            }
        }
    };
    public void getUnreadMms(){
        mNewSmsCount = getNewSmsCount() + getNewMmsCount();
        Log.i("zhao11unread", "mNewSmsCount:" + mNewSmsCount);
        if(mNewSmsCount != 0)
            Log.i(TAGzhao,"getUnreadMms222222发送广播了："+mNewSmsCount);
            broadcastUnreadMessageNumber(mcontext,mNewSmsCount);
            Toast.makeText(mcontext,"未读短信数："+mNewSmsCount,Toast.LENGTH_SHORT).show();
    }
    private void registerObserver() {
        unregisterObserver();
        mcontext.getContentResolver().registerContentObserver(Uri.parse("content://sms"), true,
                newMmsContentObserver);
        /**
         * 下边的注册是删除数据库的时候的一个监听者（因为注释下边这一个代码删除数据库就监听不到了）
         */
        mcontext.getContentResolver().registerContentObserver(Telephony.MmsSms.CONTENT_URI, true,
                newMmsContentObserver);
    }

    /**
     * 发送未读短信数
     * @param context
     * @param unreadMsgNumber
     */
    public void broadcastUnreadMessageNumber(Context context, int unreadMsgNumber) {
        Intent intent = new Intent();
        intent.setAction(ProviderConfig.ACTION_UNREAD_CHANGED);
        intent.putExtra(ProviderConfig.EXTRA_UNREAD_NUMBER, unreadMsgNumber);
        intent.putExtra(ProviderConfig.EXTRA_UNREAD_COMPONENT,
                new ComponentName("com.android.mms",
                        "com.android.mms.ui.BootActivity"));
        context.sendBroadcast(intent);
    }
    /**
     * 发送未读短信数
     * @param context
     * @param unreadMsgNumber
     */
    public void broadcastUnreadMissCall(Context context, int unreadMsgNumber) {
        Intent intent = new Intent();
        intent.setAction(ProviderConfig.ACTION_UNREAD_CHANGED);
        intent.putExtra(ProviderConfig.EXTRA_UNREAD_NUMBER, unreadMsgNumber);
        intent.putExtra(ProviderConfig.EXTRA_UNREAD_COMPONENT,
                new ComponentName("com.android.dialer",
                        "com.android.dialer.DialtactsActivity"));
        context.sendBroadcast(intent);
    }
    private synchronized void unregisterObserver() {
        try {
            if (newMmsContentObserver != null) {
                mcontext.getContentResolver().unregisterContentObserver(newMmsContentObserver);
            }
        } catch (Exception e) {
            Log.e("zhao", "unregisterObserver fail");
        }
    }

    //得到未读短信数量：
    private int getNewSmsCount() {
        int result = 0;
        Cursor csr = mcontext.getContentResolver().query(Uri.parse("content://sms"), null,
                "type = 1 and read = 0", null, null);
        if (csr != null) {
            result = csr.getCount();
            csr.close();
        }
        return result;
    }

    //获取未读彩信数量：
    private int getNewMmsCount() {
        int result = 0;
        Cursor csr = mcontext.getContentResolver().query(Uri.parse("content://mms/inbox"),
                null, "read = 0", null, null);
        if (csr != null) {
            result = csr.getCount();
            csr.close();
        }
        return result;
    }
    /**
     *  二、未接来电
     *  未接来电不能用 Observer监听，不过当有新的未接来电时，
     *  系统会发送一个广播com.android.phone.NotificationMgr.MissedCall_intent
     * （锁屏上显示的未接来电数量就是通知监听这个广播实现的）
     */
//    final IntentFilter filter = new IntentFilter();
//    filter.addAction("com.android.phone.NotificationMgr.MissedCall_intent");
//    final Application application = getApplication();
//    application.registerReceiver(new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (action != null && "com.android.phone.NotificationMgr.MissedCall_intent".equals(action)) {
//                int mMissCallCount = intent.getExtras().getInt("MissedCallNumber");
//            }
//        }
//    }, filter);

    /**
     * 广播只是当有新的未接来电时才会发送，但是如果有旧的未接来电没有读取时，
     * 上面的广播就得不到数据了，那就必须得从数据库中查找了。功能代码如下
     */
//    private int readMissCall() {
//        int result = 0;
//        Cursor cursor = mcontext.getContentResolver().query(Calls.CONTENT_URI, new String[] {
//                CallLog.Calls.TYPE
//        }, " type=? and new=?", new String[] {
//                CallLog.Calls.MISSED_TYPE + "", "1"
//        }, "date desc");
//
//        if (cursor != null) {
//            result = cursor.getCount();
//            cursor.close();
//        }
//        return result;
//    }
}
