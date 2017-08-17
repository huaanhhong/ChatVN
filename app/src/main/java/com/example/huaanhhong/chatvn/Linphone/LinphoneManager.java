package com.example.huaanhhong.chatvn.Linphone;


import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.Vibrator;
import android.telephony.TelephonyManager;

import com.example.huaanhhong.chatvn.R;

import org.linphone.core.LinphoneAddress;
import org.linphone.core.LinphoneAuthInfo;
import org.linphone.core.LinphoneBuffer;
import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCallStats;
import org.linphone.core.LinphoneChatMessage;
import org.linphone.core.LinphoneChatRoom;
import org.linphone.core.LinphoneContent;
import org.linphone.core.LinphoneCore;
import org.linphone.core.LinphoneCoreException;
import org.linphone.core.LinphoneCoreFactory;
import org.linphone.core.LinphoneCoreListener;
import org.linphone.core.LinphoneEvent;
import org.linphone.core.LinphoneFriend;
import org.linphone.core.LinphoneFriendList;
import org.linphone.core.LinphoneInfoMessage;
import org.linphone.core.LinphoneProxyConfig;
import org.linphone.core.PublishState;
import org.linphone.core.SubscriptionState;
import org.linphone.core.TunnelConfig;
import org.linphone.mediastream.Log;
import org.linphone.mediastream.Version;
import org.linphone.mediastream.video.capture.hwconf.AndroidCameraConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import static android.media.AudioManager.STREAM_VOICE_CALL;

/**
 * Created by huaanhhong on 16/08/2017.
 *
 * Lop nay sinh ra cac phuong thuc, nham quan ly va dieu hanh cac cong viec chinh
 */

public class LinphoneManager implements LinphoneCoreListener, LinphoneChatMessage.LinphoneChatMessageListener {

    private static boolean sExited;
    private Context mServiceContext;
    private String basePath;
    /**
     * call when fisrt activity created
     */
    private final String mLPConfigXsd;
    private final String mLinphoneFactoryConfigFile;
    public final String mLinphoneConfigFile;
    private final String mLinphoneRootCaFile;
    private final String mRingSoundFile;
    private final String mRingbackSoundFile;
    private final String mPauseSoundFile;
    private final String mCallLogDatabaseFile;
    private final String mErrorToneFile;
    private final String mUserCertificatePath;
    private LinphonePreferences mPrefs;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private PowerManager mPowerManager;
    private ConnectivityManager mConnectivityManager;
    private Resources mR;
    //////////////////////////////////
    private LinphoneCore mLc;
    private static LinphoneManager instance;
    private Timer mTimer;
    private BroadcastReceiver mKeepAliveReceiver = new KeepAliveReceiver();
    /////////////////////////////////
    private int mLastNetworkType=-1;

    /**
     * LinphoneManager constructor
     */
    protected LinphoneManager(final Context c) {
        sExited = false;
        mServiceContext = c;
        basePath = c.getFilesDir().getAbsolutePath();
        android.util.Log.i("CNN", "Manager_linphonemanager basepath " + basePath);
        mLPConfigXsd = basePath + "/lpconfig.xsd";
        mLinphoneFactoryConfigFile = basePath + "/linphonerc";
        mLinphoneConfigFile = basePath + "/.linphonerc";
        mLinphoneRootCaFile = basePath + "/rootca.pem";
        mRingSoundFile = basePath + "/oldphone_mono.wav";
        mRingbackSoundFile = basePath + "/ringback.wav";
        mPauseSoundFile = basePath + "/hold.mkv";
        //     mChatDatabaseFile = basePath + "/linphone-history.db";
        mCallLogDatabaseFile = basePath + "/linphone-log-history.db";
        //     mFriendsDatabaseFile = basePath + "/linphone-friends.db";
        mErrorToneFile = basePath + "/error.wav";
        mUserCertificatePath = basePath;

        mPrefs = LinphonePreferences.instance();
        mAudioManager = ((AudioManager) c.getSystemService(Context.AUDIO_SERVICE));
        mVibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
        mPowerManager = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        mConnectivityManager = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
        mR = c.getResources();

        android.util.Log.i("CNN", "Manager_linphonemanager");
    }

    private void routeAudioToSpeakerHelper(boolean speakerOn) {
        Log.w("Routing audio to " + (speakerOn ? "speaker" : "earpiece") + ", disabling bluetooth audio route");
//        BluetoothManager.getInstance().disableBluetoothSCO();

        mLc.enableSpeaker(speakerOn);
    }

    public void routeAudioToSpeaker() {
        routeAudioToSpeakerHelper(true);
        android.util.Log.i("CNN", "Manager_routerAudioSpeaker");
    }
    public void routeAudioToReceiver() {
        routeAudioToSpeakerHelper(false);
        android.util.Log.i("CNN", "Manager_routeraudiospeaker");
    }

    /**
     * LinphoneManager creaeAndStart
     */
    public synchronized static final LinphoneManager createAndStart(Context c) {
        android.util.Log.i("CNN", "Manager_createandstart");
        if (instance != null)
            throw new RuntimeException("Linphone Manager is already initialized");
        android.util.Log.i("CNN", "Manager_insatnce!null");

        instance = new LinphoneManager(c);
        instance.startLibLinphone(c);
        TelephonyManager tm = (TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
        boolean gsmIdle = tm.getCallState() == TelephonyManager.CALL_STATE_IDLE;
        setGsmIdle(gsmIdle);

        return instance;
    }

    /**
     * LinphoneManager getinstance
     */
    public static synchronized final LinphoneManager getInstance() {
        android.util.Log.i("CNN", "Manager_getinsance");
        if (instance != null) return instance;

        if (sExited) {
            throw new RuntimeException("Linphone Manager was already destroyed. "
                    + "Better use getLcIfManagerNotDestroyed and check returned value");
        }

        throw new RuntimeException("Linphone Manager should be created before accessed");
    }

    public static synchronized final LinphoneCore getLc() {
        android.util.Log.i("CNN", "Manager_getLC");
        return getInstance().mLc;

    }

    public String getLPConfigXsdPath() {
        return mLPConfigXsd;
    }

    /**
     * Dettroy Linphonecore
     */
    public synchronized final void destroyLinphoneCore() {
        android.util.Log.i("CNN", "Manager_destroylinphonecore");
        sExited = true;
        //chua dung den
//        BluetoothManager.getInstance().destroy();
        try {
            mTimer.cancel();
            mLc.destroy();
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            mServiceContext.unregisterReceiver(instance.mKeepAliveReceiver);
            mLc = null;
        }
    }

    /**
     * restart Linphonecore
     */
    public void restartLinphoneCore() {
        android.util.Log.i("CNN", "Manager_restartlinphonecore");
        destroyLinphoneCore();
        startLibLinphone(mServiceContext);
        sExited = false;
    }

    /**
     * start liblinphone
     */
    private synchronized void startLibLinphone(Context c) {
        try {
            android.util.Log.i("CNN", "Manager_startliblinphone");
            copyAssetsFromPackage();
            //traces alway start with traces enable to not missed first initialization
            boolean isDebugLogEnabled = !(mR.getBoolean(R.bool.disable_every_log));
            LinphoneCoreFactory.instance().setDebugMode(isDebugLogEnabled, mR.getString(R.string.app_name));
            LinphoneCoreFactory.instance().enableLogCollection(isDebugLogEnabled);

            mLc = LinphoneCoreFactory.instance().createLinphoneCore(this, mLinphoneConfigFile, mLinphoneFactoryConfigFile, null, c);

            TimerTask lTask = new TimerTask() {
                @Override
                public void run() {
                    UIThreadDispatcher.dispatch(new Runnable() {
                        @Override
                        public void run() {
                            if (mLc != null) {
                                mLc.iterate();
                                //iterate :
                                //Main loop function. It is crucial that your application call it periodically. #iterate() performs various backgrounds tasks:
                            }
                        }
                    });
                }
            };
			/*use schedule instead of scheduleAtFixedRate to avoid iterate from being call in burst after cpu wake up*/
            mTimer = new Timer("Linphone scheduler");
            mTimer.schedule(lTask, 0, 20);
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(e, "Cannot start linphone");
        }
    }

    /**
     * Thiet lap linphone core, rat quan trong
     */
    private synchronized void initLiblinphone(LinphoneCore lc) throws LinphoneCoreException {

        android.util.Log.i("CNN", "Manager_initliblinphone");
        mLc = lc;
        //cv nay de goi log luu log len server
        boolean isDebugLogEnabled = !(mR.getBoolean(R.bool.disable_every_log)) && mPrefs.isDebugEnabled();
        LinphoneCoreFactory.instance().setDebugMode(isDebugLogEnabled, mR.getString(R.string.app_name));
        LinphoneCoreFactory.instance().enableLogCollection(isDebugLogEnabled);

        PreferencesMigrator prefMigrator = new PreferencesMigrator(mServiceContext);
        prefMigrator.migrateRemoteProvisioningUriIfNeeded();
        prefMigrator.migrateSharingServerUrlIfNeeded();

        if (prefMigrator.isMigrationNeeded()) {
            prefMigrator.doMigration();
        }

        // Some devices could be using software AEC before
        // This will disable it in favor of hardware AEC if available
        if (prefMigrator.isEchoMigratioNeeded()) {
            Log.d("Echo canceller configuration need to be updated");
            prefMigrator.doEchoMigration();
            mPrefs.echoConfigurationUpdated();
        }

        mLc.setContext(mServiceContext);
        mLc.setZrtpSecretsCache(basePath + "/zrtp_secrets");

        try {
            String versionName = mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionName;
            if (versionName == null) {
                versionName = String.valueOf(mServiceContext.getPackageManager().getPackageInfo(mServiceContext.getPackageName(), 0).versionCode);
            }
            mLc.setUserAgent("LinphoneAndroid", versionName);
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(e, "cannot get version name");
        }
        mLc.setRing(mRingSoundFile);
        if (mR.getBoolean(R.bool.use_linphonecore_ringing)) {
            disableRinging();
        } else {
            mLc.setRing(null); //We'll use the android media player api to play the ringtone
        }
        mLc.setRingback(mRingbackSoundFile);
        mLc.setRootCA(mLinphoneRootCaFile);
        mLc.setPlayFile(mPauseSoundFile);
        mLc.setCallLogsDatabasePath(mCallLogDatabaseFile);
        mLc.setUserCertificatesPath(mUserCertificatePath);
        //mLc.setCallErrorTone(Reason.NotFound, mErrorToneFile);

        int availableCores = Runtime.getRuntime().availableProcessors();
        Log.w("MediaStreamer : " + availableCores + " cores detected and configured");
        mLc.setCpuCount(availableCores);

        int migrationResult = getLc().migrateToMultiTransport();
        Log.d("Migration to multi transport result = " + migrationResult);

        mLc.migrateCallLogs();

        if (mServiceContext.getResources().getBoolean(R.bool.enable_push_id)) {
//            Compatibility.initPushNotificationService(mServiceContext);
        }

        IntentFilter lFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        lFilter.addAction(Intent.ACTION_SCREEN_OFF);
        mServiceContext.registerReceiver(mKeepAliveReceiver, lFilter);

        updateNetworkReachability();

        if (Version.sdkAboveOrEqual(Version.API11_HONEYCOMB_30)) {
//            BluetoothManager.getInstance().initBluetooth();
        }

        resetCameraFromPreferences();
        mLc.setFileTransferServer(LinphonePreferences.instance().getSharingPictureServerUrl());
    }

    /** UpdateNetworkReachability - kiem tra,thiet lap tinh trang internet*/
    public void updateNetworkReachability() {
        android.util.Log.i("CNN", "Manager_updateneworkREachability");
        ConnectivityManager cm = (ConnectivityManager) mServiceContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo eventInfo = cm.getActiveNetworkInfo();

        if (eventInfo == null || eventInfo.getState() == NetworkInfo.State.DISCONNECTED) {
            Log.i("No connectivity: setting network unreachable");
            mLc.setNetworkReachable(false);
        } else if (eventInfo.getState() == NetworkInfo.State.CONNECTED){
            manageTunnelServer(eventInfo);

            boolean wifiOnly = LinphonePreferences.instance().isWifiOnlyEnabled();
            if (wifiOnly){
                if (eventInfo.getType()==ConnectivityManager.TYPE_WIFI)
                    mLc.setNetworkReachable(true);
                else {
                    Log.i("Wifi-only mode, setting network not reachable");
                    mLc.setNetworkReachable(false);
                }
            }else{
                int curtype=eventInfo.getType();

                if (curtype!=mLastNetworkType){
                    //if kind of network has changed, we need to notify network_reachable(false) to make sure all current connections are destroyed.
                    //they will be re-created during setNetworkReachable(true).
                    Log.i("Connectivity has changed.");
                    mLc.setNetworkReachable(false);
                }
                mLc.setNetworkReachable(true);
                mLastNetworkType=curtype;
            }
        }
    }
    public void initTunnelFromConf() {
        if (!mLc.isTunnelAvailable())
            return;
        android.util.Log.i("CNN", "Manager_initTunneFromConf");
        NetworkInfo info = mConnectivityManager.getActiveNetworkInfo();
        mLc.tunnelCleanServers();
        TunnelConfig config = mPrefs.getTunnelConfig();
        if (config.getHost() != null) {
            mLc.tunnelAddServer(config);
            manageTunnelServer(info);
        }
    }
    private boolean isTunnelNeeded(NetworkInfo info) {
        android.util.Log.i("CNN", "Manager_istunnelneed");
        if (info == null) {
            Log.i("No connectivity: tunnel should be disabled");
            return false;
        }

        String pref = mPrefs.getTunnelMode();

        if (getString(R.string.tunnel_mode_entry_value_always).equals(pref)) {
            return true;
        }

        if (info.getType() != ConnectivityManager.TYPE_WIFI
                && getString(R.string.tunnel_mode_entry_value_3G_only).equals(pref)) {
            Log.i("need tunnel: 'no wifi' connection");
            return true;
        }

        return false;
    }

    private void manageTunnelServer(NetworkInfo info) {
        android.util.Log.i("CNN", "Manager_manageTUnneServer");
        if (mLc == null) return;
        if (!mLc.isTunnelAvailable()) return;

        Log.i("Managing tunnel");
        if (isTunnelNeeded(info)) {
            Log.i("Tunnel need to be activated");
            mLc.tunnelSetMode(LinphoneCore.TunnelMode.enable);
        } else {
            Log.i("Tunnel should not be used");
            String pref = mPrefs.getTunnelMode();
            mLc.tunnelSetMode(LinphoneCore.TunnelMode.disable);
            if (getString(R.string.tunnel_mode_entry_value_auto).equals(pref)) {
                mLc.tunnelSetMode(LinphoneCore.TunnelMode.auto);
            }
        }
    }
    /** resetCameraFromPreference*/
    private void resetCameraFromPreferences() {
        android.util.Log.i("CNN", "Manager_reseCamerFrom");
        boolean useFrontCam = mPrefs.useFrontCam();

        int camId = 0;
        AndroidCameraConfiguration.AndroidCamera[] cameras = AndroidCameraConfiguration.retrieveCameras();
        for (AndroidCameraConfiguration.AndroidCamera androidCamera : cameras) {
            if (androidCamera.frontFacing == useFrontCam)
                camId = androidCamera.id;
        }
        LinphoneManager.getLc().setVideoDevice(camId);
    }

    private void copyAssetsFromPackage() throws IOException {
        android.util.Log.i("CNN", "Manager_copyassets");
        copyIfNotExist(R.raw.oldphone_mono, mRingSoundFile);
        copyIfNotExist(R.raw.ringback, mRingbackSoundFile);
        copyIfNotExist(R.raw.hold, mPauseSoundFile);
        copyIfNotExist(R.raw.incoming_chat, mErrorToneFile);
        copyIfNotExist(R.raw.linphonerc_default, mLinphoneConfigFile);
        copyFromPackage(R.raw.linphonerc_factory, new File(mLinphoneFactoryConfigFile).getName());
        copyIfNotExist(R.raw.lpconfig, mLPConfigXsd);
        copyIfNotExist(R.raw.rootca, mLinphoneRootCaFile);
    }

    public void copyIfNotExist(int ressourceId, String target) throws IOException {
        android.util.Log.i("CNN", "Manager_copyIfNoExits");
        File lFileToCopy = new File(target);
        if (!lFileToCopy.exists()) {
            copyFromPackage(ressourceId,lFileToCopy.getName());
        }
    }
    public void copyFromPackage(int ressourceId, String target) throws IOException{
        android.util.Log.i("CNN", "Manager_copyFRomPackage");
        FileOutputStream lOutputStream = mServiceContext.openFileOutput (target, 0);
        InputStream lInputStream = mR.openRawResource(ressourceId);
        int readByte;
        byte[] buff = new byte[8048];
        while (( readByte = lInputStream.read(buff)) != -1) {
            lOutputStream.write(buff,0, readByte);
        }
        lOutputStream.flush();
        lOutputStream.close();
        lInputStream.close();
    }

    /** Detroy */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void doDestroy() {
        android.util.Log.i("CNN", "Manager_doDestroy");
        if (LinphoneService.isReady()) // indeed, no need to crash

        //chua dung den
//        BluetoothManager.getInstance().destroy();
        try {
            mTimer.cancel();
            mLc.destroy();
        }
        catch (RuntimeException e) {
            e.printStackTrace();
        }
        finally {
            mServiceContext.unregisterReceiver(instance.mKeepAliveReceiver);
            mLc = null;
            instance = null;
        }
    }

    public static synchronized void destroy() {
        android.util.Log.i("CNN", "Manager_detroy");
        if (instance == null) return;

        sExited = true;
        instance.doDestroy();
    }

    private String getString(int key) {
        return mR.getString(key);
    }


    public static synchronized LinphoneCore getLcIfManagerNotDestroyedOrNull() {
        android.util.Log.i("CNN", "Manager_NgetLCIfManagerNotDestroyOrNull");
        if (sExited || instance == null) {
            // Can occur if the UI thread play a posted event but in the meantime the LinphoneManager was destroyed
            // Ex: stop call and quickly terminate application.
            Log.w("Trying to get linphone core while LinphoneManager already destroyed or not created");
            return null;
        }
        return getLc();
    }

    public static final boolean isInstanciated() {
        android.util.Log.i("CNN", "Manager_isInstanced");
        return instance != null;

    }
    public Context getContext() {
        android.util.Log.i("CNN", "Manager_getContext");
        try {
//            if (LinphoneActivity.isInstanciated())
//                return LinphoneActivity.instance();
//            else if (CallActivity.isInstanciated())
//                return CallActivity.instance();
//            else if (CallIncomingActivity.isInstanciated())
//                return CallIncomingActivity.instance();
             if (mServiceContext != null)
                return mServiceContext;
            else if (LinphoneService.isReady())
                return LinphoneService.instance().getApplicationContext();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    ///////////////////////////////////////KET THUC NHUNG GI CHUNG CHUNG VE LINPHONE CORE//////////////////////////////
    /* Simple implementation as Android way seems very complicate:
	For example: with wifi and mobile actives; when pulling mobile down:
	I/Linphone( 8397): WIFI connected: setting network reachable
	I/Linphone( 8397): new state [RegistrationProgress]
	I/Linphone( 8397): mobile disconnected: setting network unreachable
	I/Linphone( 8397): Managing tunnel
	I/Linphone( 8397): WIFI connected: setting network reachable
	*/
    /**Sau, la nhung van de ve mang, cuoc goi, sensor */



    public void globalState(final LinphoneCore lc, final LinphoneCore.GlobalState state, final String message) {
        android.util.Log.i("CNN", "Manager_globalstate");
        Log.i("New global state [",state,"]");
        if (state == LinphoneCore.GlobalState.GlobalOn){
            try {
                initLiblinphone(lc);
            } catch (LinphoneCoreException e) {
                Log.e(e);
            }
        }
    }

    public void registrationState(final LinphoneCore lc, final LinphoneProxyConfig proxy, final LinphoneCore.RegistrationState state, final String message) {
        Log.i("New registration state ["+state+"]");
        android.util.Log.i("CNN", "Manager_registrationstate");
    }

    private int savedMaxCallWhileGsmIncall;
    private synchronized void preventSIPCalls() {
        android.util.Log.i("CNN", "Manager_prevenSIPCalls");
        if (savedMaxCallWhileGsmIncall != 0) {
            Log.w("SIP calls are already blocked due to GSM call running");
            return;
        }
        savedMaxCallWhileGsmIncall = mLc.getMaxCalls();
        mLc.setMaxCalls(0);
    }
    private synchronized void allowSIPCalls() {
        android.util.Log.i("CNN", "Manager_allowSIPCalls");
        if (savedMaxCallWhileGsmIncall == 0) {
            Log.w("SIP calls are already allowed as no GSM call known to be running");
            return;
        }
        mLc.setMaxCalls(savedMaxCallWhileGsmIncall);
        savedMaxCallWhileGsmIncall = 0;
    }

    public static void setGsmIdle(boolean gsmIdle) {
        android.util.Log.i("CNN", "Manager_setGsmIdle");
        LinphoneManager mThis = instance;
        if (mThis == null) return;
        if (gsmIdle) {
            mThis.allowSIPCalls();
        } else {
            mThis.preventSIPCalls();
        }
    }
    public void startEcCalibration(LinphoneCoreListener l) throws LinphoneCoreException {
        android.util.Log.i("CNN", "Manager_startEcCalibraion");
        routeAudioToSpeaker();
        int oldVolume = mAudioManager.getStreamVolume(STREAM_VOICE_CALL);
        int maxVolume = mAudioManager.getStreamMaxVolume(STREAM_VOICE_CALL);
        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, maxVolume, 0);
        mLc.startEchoCalibration(l);

        mAudioManager.setStreamVolume(STREAM_VOICE_CALL, oldVolume, 0);
    }

    private boolean isRinging;
    private boolean disableRinging = false;

    public void disableRinging() {
        disableRinging = true;
    }




    @Override
    public void onLinphoneChatMessageStateChanged(LinphoneChatMessage linphoneChatMessage, LinphoneChatMessage.State state) {
            //////KHONG CO////////////////////////////
    }

    @Override
    public void onLinphoneChatMessageFileTransferReceived(LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, LinphoneBuffer linphoneBuffer) {
           ////////KHONG CO/////////////////////////////
    }

    @Override
    public void onLinphoneChatMessageFileTransferSent(LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, int i, int i1, LinphoneBuffer linphoneBuffer) {
         //////////KHONG CO///////////////////////////////
    }

    @Override
    public void onLinphoneChatMessageFileTransferProgressChanged(LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, int i, int i1) {
       ///////////KHONG CO//////////////////////////////
    }

    @Override
    public void authInfoRequested(LinphoneCore linphoneCore, String s, String s1, String s2) {

    }

    @Override
    public void authenticationRequested(LinphoneCore linphoneCore, LinphoneAuthInfo linphoneAuthInfo, LinphoneCore.AuthMethod authMethod) {

    }

    @Override
    public void callStatsUpdated(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCallStats linphoneCallStats) {

    }

    @Override
    public void newSubscriptionRequest(LinphoneCore linphoneCore, LinphoneFriend linphoneFriend, String s) {

    }

    @Override
    public void notifyPresenceReceived(LinphoneCore linphoneCore, LinphoneFriend linphoneFriend) {

    }

    @Override
    public void dtmfReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, int i) {

    }

    @Override
    public void notifyReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneAddress linphoneAddress, byte[] bytes) {

    }

    @Override
    public void transferState(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCall.State state) {

    }

    @Override
    public void infoReceived(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneInfoMessage linphoneInfoMessage) {

    }

    @Override
    public void subscriptionStateChanged(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, SubscriptionState subscriptionState) {

    }

    @Override
    public void publishStateChanged(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, PublishState publishState) {

    }

    @Override
    public void show(LinphoneCore linphoneCore) {

    }

    @Override
    public void displayStatus(LinphoneCore linphoneCore, String s) {

    }


    @Override
    public void displayMessage(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void displayWarning(LinphoneCore linphoneCore, String s) {

    }

    @Override
    public void fileTransferProgressIndication(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, int i) {

    }

    @Override
    public void fileTransferRecv(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, byte[] bytes, int i) {

    }

    @Override
    public int fileTransferSend(LinphoneCore linphoneCore, LinphoneChatMessage linphoneChatMessage, LinphoneContent linphoneContent, ByteBuffer byteBuffer, int i) {
        return 0;
    }

    @Override
    public void configuringStatus(LinphoneCore linphoneCore, LinphoneCore.RemoteProvisioningState remoteProvisioningState, String s) {

    }

    @Override
    public void messageReceived(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom, LinphoneChatMessage linphoneChatMessage) {
       ///////////KHONG CO////////////
    }

    @Override
    public void messageReceivedUnableToDecrypted(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom, LinphoneChatMessage linphoneChatMessage) {

    }

    @Override
    public void callState(LinphoneCore linphoneCore, LinphoneCall linphoneCall, LinphoneCall.State state, String s) {

    }

    @Override
    public void callEncryptionChanged(LinphoneCore linphoneCore, LinphoneCall linphoneCall, boolean b, String s) {

    }

    @Override
    public void notifyReceived(LinphoneCore linphoneCore, LinphoneEvent linphoneEvent, String s, LinphoneContent linphoneContent) {

    }

    @Override
    public void isComposingReceived(LinphoneCore linphoneCore, LinphoneChatRoom linphoneChatRoom) {

    }

    @Override
    public void ecCalibrationStatus(LinphoneCore linphoneCore, LinphoneCore.EcCalibratorStatus ecCalibratorStatus, int i, Object o) {

    }

    @Override
    public void uploadProgressIndication(LinphoneCore linphoneCore, int i, int i1) {

    }

    @Override
    public void uploadStateChanged(LinphoneCore linphoneCore, LinphoneCore.LogCollectionUploadState logCollectionUploadState, String s) {

    }

    @Override
    public void friendListCreated(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void friendListRemoved(LinphoneCore linphoneCore, LinphoneFriendList linphoneFriendList) {

    }

    @Override
    public void networkReachableChanged(LinphoneCore linphoneCore, boolean b) {

    }
}
