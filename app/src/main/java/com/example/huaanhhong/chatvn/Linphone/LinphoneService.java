package com.example.huaanhhong.chatvn.Linphone;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;


import com.example.huaanhhong.chatvn.R;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreListenerBase;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.mediastream.Log;

public class LinphoneService extends Service {

    private static LinphoneService instance;
    private LinphoneCoreListenerBase mListener;

    public static boolean isReady() {
        return instance != null && instance.mTestDelayElapsed;
    }

    public static LinphoneService instance()  {
        if (isReady()) return instance;

        throw new RuntimeException("LinphoneService not instantiated yet");
    }

    private boolean mTestDelayElapsed = true;
    public Handler mHandler = new Handler();
    private PendingIntent mkeepAlivePendingIntent;

    @Override
    public void onCreate() {
        super.onCreate();

        LinphoneManager.createAndStart(LinphoneService.this);
        instance = this; // instance is ready once linphone manager has been created
        LinphoneManager.getLc().addListener(mListener = new LinphoneCoreListenerBase(){
            @Override
            public void callState(LinphoneCore lc, LinphoneCall call, LinphoneCall.State state, String message) {
                super.callState(lc, call, state, message);
            }

            @Override
            public void registrationState(LinphoneCore lc, LinphoneProxyConfig cfg, LinphoneCore.RegistrationState state, String smessage) {
                super.registrationState(lc, cfg, state, smessage);
            }

            @Override
            public void globalState(LinphoneCore lc, LinphoneCore.GlobalState state, String message) {
                super.globalState(lc, state, message);
            }
        });
        if (!mTestDelayElapsed) {
            // Only used when testing. Simulates a 5 seconds delay for launching service
            mHandler.postDelayed(new Runnable() {
                @Override public void run() {
                    mTestDelayElapsed = true;
                }
            }, 5000);
            android.util.Log.i("CNN", "Service_testDelay");
        }

        //make sure the application will at least wakes up every 10 mn
        Intent intent = new Intent(this, KeepAliveHandler.class);
        mkeepAlivePendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
        ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE)).setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP
                , SystemClock.elapsedRealtime()+600000
                , 600000
                , mkeepAlivePendingIntent);
    }

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        if (getResources().getBoolean(R.bool.kill_service_with_task_manager)) {
            Log.d("Task removed, stop service");
            LinphoneManager.getLc().setNetworkReachable(false);
            stopSelf();
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public synchronized void onDestroy() {
        LinphoneCore lc = LinphoneManager.getLcIfManagerNotDestroyedOrNull();
        if (lc != null) {
            lc.removeListener(mListener);
        }
        android.util.Log.i("CNN", "service detroy");

        instance = null;
        LinphoneManager.destroy();

        // Make sure our notification is gone.

        ((AlarmManager) this.getSystemService(Context.ALARM_SERVICE)).cancel(mkeepAlivePendingIntent);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Return the communication channel to the service.
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
