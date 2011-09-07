/*
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *          the names of its contributors may be used to endorse or promote
 *          products derived from this software without specific prior written
 *          permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.map;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.bluetooth.R;

import java.io.IOException;

import javax.obex.ServerSession;

import static com.android.bluetooth.map.IBluetoothMasApp.MESSAGE_TYPE_EMAIL;
import static com.android.bluetooth.map.IBluetoothMasApp.MESSAGE_TYPE_MMS;
import static com.android.bluetooth.map.IBluetoothMasApp.MESSAGE_TYPE_SMS;
import static com.android.bluetooth.map.IBluetoothMasApp.MESSAGE_TYPE_SMS_MMS;

public class BluetoothMasService extends Service {
    private static final String TAG = "BluetoothMasService";

    /**
     * To enable MAP DEBUG/VERBOSE logging - run below cmd in adb shell, and
     * restart com.android.bluetooth process. only enable DEBUG log:
     * "setprop log.tag.BluetoothMapService DEBUG"; enable both VERBOSE and
     * DEBUG log: "setprop log.tag.BluetoothMapService VERBOSE"
     */
    public static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    public static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    /**
     * Intent indicating incoming connection request which is sent to
     * BluetoothMasActivity
     */
    public static final String ACCESS_REQUEST_ACTION = "com.android.bluetooth.map.accessrequest";

    /**
     * Intent indicating incoming connection request accepted by user which is
     * sent from BluetoothMasActivity
     */
    public static final String ACCESS_ALLOWED_ACTION = "com.android.bluetooth.map.accessallowed";

    /**
     * Intent indicating incoming connection request denied by user which is
     * sent from BluetoothMasActivity
     */
    public static final String ACCESS_DISALLOWED_ACTION = "com.android.bluetooth.map.accessdisallowed";

    /**
     * Intent indicating incoming obex authentication request which is from
     * PCE(Carkit)
     */
    public static final String AUTH_CHALL_ACTION = "com.android.bluetooth.map.authchall";

    /**
     * Intent indicating obex session key input complete by user which is sent
     * from BluetoothMasActivity
     */
    public static final String AUTH_RESPONSE_ACTION = "com.android.bluetooth.map.authresponse";

    /**
     * Intent indicating user canceled obex authentication session key input
     * which is sent from BluetoothMasActivity
     */
    public static final String AUTH_CANCELLED_ACTION = "com.android.bluetooth.map.authcancelled";

    /**
     * Intent indicating timeout for user confirmation, which is sent to
     * BluetoothMasActivity
     */
    public static final String USER_CONFIRM_TIMEOUT_ACTION = "com.android.bluetooth.map.userconfirmtimeout";

    public static final String THIS_PACKAGE_NAME = "com.android.bluetooth";

    /**
     * Intent Extra name indicating always allowed which is sent from
     * BluetoothMasActivity
     */
    public static final String EXTRA_ALWAYS_ALLOWED = "com.android.bluetooth.map.alwaysallowed";

    /**
     * Intent Extra name indicating session key which is sent from
     * BluetoothMasActivity
     */
    public static final String EXTRA_SESSION_KEY = "com.android.bluetooth.map.sessionkey";

    public static final int MSG_SERVERSESSION_CLOSE = 5004;

    public static final int MSG_SESSION_ESTABLISHED = 5005;

    public static final int MSG_SESSION_DISCONNECTED = 5006;

    public static final int MSG_OBEX_AUTH_CHALL = 5007;

    private static final int MSG_INTERNAL_START_LISTENER = 1;

    private static final int MSG_INTERNAL_USER_TIMEOUT = 2;

    private static final int MSG_INTERNAL_AUTH_TIMEOUT = 3;

    /* package private */
    static final int MSG_INTERNAL_CONNECTION_FAILED = 4;

    private static final int USER_CONFIRM_TIMEOUT_VALUE = 30000;

    private static final int TIME_TO_WAIT_VALUE = 6000;

    // Ensure not conflict with Opp notification ID
    private static final int NOTIFICATION_ID_ACCESS = -1000005;

    private static final int NOTIFICATION_ID_AUTH = -1000006;

    private BluetoothAdapter mAdapter;

    private Object mAuthSync = new Object();

    private BluetoothMapAuthenticator mAuth = null;

    public static BluetoothDevice mRemoteDevice = null;

    private static String sRemoteDeviceName = null;

    BluetoothObexConnectManager masId1, masId2;

    BluetoothMns mnsClient;
    public static boolean uiRequestCalled = false;

    public BluetoothMasService() {
        masId1 = new BluetoothObexConnectManager(MESSAGE_TYPE_SMS_MMS, 16);
        masId2 = new BluetoothObexConnectManager(MESSAGE_TYPE_EMAIL, 17);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (VERBOSE)
            Log.v(TAG, "Map Service onCreate");

        masId1.mInterrupted = false;
        masId2.mInterrupted = false;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!masId1.mHasStarted) {
            masId1.mHasStarted = true;
            if (VERBOSE)
                Log.v(TAG, "Starting MAP service");

            int state = mAdapter.getState();
            if (state == BluetoothAdapter.STATE_ON) {
                mSessionStatusHandler.sendEmptyMessageDelayed(
                        MSG_INTERNAL_START_LISTENER, TIME_TO_WAIT_VALUE);
            }
        }
        if (!masId2.mHasStarted) {
            masId2.mHasStarted = true;
            if (VERBOSE)
                Log.e(TAG, "Starting MAP service for second Mas Instance");

            int state = mAdapter.getState();
            if (state == BluetoothAdapter.STATE_ON) {
                mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
                        .obtainMessage(MSG_INTERNAL_START_LISTENER), TIME_TO_WAIT_VALUE);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (VERBOSE)
            Log.v(TAG, "Map Service onStartCommand");
        int retCode = super.onStartCommand(intent, flags, startId);
        if (retCode == START_STICKY) {
            masId1.mStartId = startId;
            masId2.mStartId = startId;
            if (mAdapter == null) {
                Log.w(TAG, "Stopping BluetoothMasService: "
                        + "device does not have BT or device is not ready");
                // Release all resources
                masId1.closeService();
                masId2.closeService();
            } else {
                // No need to handle the null intent case, because we have
                // all restart work done in onCreate()
                if (intent != null) {
                    parseIntent(intent);
                }
            }
        }
        return retCode;
    }

    // process the intent from receiver
    private void parseIntent(final Intent intent) {
        String action = intent.getStringExtra("action");
        if (VERBOSE)
            Log.v(TAG, "action: " + action);

        int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR);
        boolean removeTimeoutMsg = true;
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            removeTimeoutMsg = false;
            if (state == BluetoothAdapter.STATE_TURNING_OFF) {
                // Release all resources
                masId1.closeService();
                masId2.closeService();
            }
        } else if (Intent.ACTION_MEDIA_EJECT.equals(action)
                || Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            masId1.closeService();
            masId2.closeService();
        } else if (ACCESS_ALLOWED_ACTION.equals(action)) {
            if (intent.getBooleanExtra(EXTRA_ALWAYS_ALLOWED, false)) {
                boolean result = mRemoteDevice.setTrust(true);
                if (VERBOSE)
                    Log.v(TAG, "setTrust() result=" + result);
            }
            try {
                if (masId1.mConnSocket != null) {
                    masId1.startObexServerSession(mnsClient);
                }
                else if(masId2.mConnSocket != null){
                    masId2.startObexServerSession(mnsClient);
                }
                else {
                    masId1.stopObexServerSession();
                    masId2.stopObexServerSession();
                }
            } catch (IOException ex) {
                Log.e(TAG, "Caught the error: " + ex.toString());
            }
        } else if (ACCESS_DISALLOWED_ACTION.equals(action)) {
            masId1.stopObexServerSession();
            masId2.stopObexServerSession();
        } else if (AUTH_RESPONSE_ACTION.equals(action)) {
            String sessionkey = intent.getStringExtra(EXTRA_SESSION_KEY);
            notifyAuthKeyInput(sessionkey);
        } else if (AUTH_CANCELLED_ACTION.equals(action)) {
            notifyAuthCancelled();
        } else {
            removeTimeoutMsg = false;
        }

        if (removeTimeoutMsg) {
            mSessionStatusHandler.removeMessages(MSG_INTERNAL_USER_TIMEOUT);
        }
    }

    @Override
    public void onDestroy() {
        if (VERBOSE)
            Log.v(TAG, "Map Service onDestroy");

        super.onDestroy();
        if (masId1.mWakeLock != null) {
            masId1.mWakeLock.release();
            masId1.mWakeLock = null;
        }
        if (masId2.mWakeLock != null) {
            masId2.mWakeLock.release();
            masId2.mWakeLock = null;
        }
        masId1.closeService();
        masId2.closeService();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (VERBOSE)
            Log.v(TAG, "Map Service onBind");
        return null;
    }

    private final Handler mSessionStatusHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            if (VERBOSE) Log.v(TAG, "Handler(): got msg=" + msg.what);
            Context context = getApplicationContext();
            if (mnsClient == null) {
                mnsClient = new BluetoothMns(context);
            }

            switch (msg.what) {
                case MSG_INTERNAL_START_LISTENER:
                    if (mAdapter.isEnabled()) {
                        masId1.startRfcommSocketListener(mnsClient);
                        masId2.startRfcommSocketListener(mnsClient);
                    } else {
                        masId1.closeService();// release all resources
                        masId2.closeService();
                    }
                    break;
                case MSG_INTERNAL_USER_TIMEOUT:
                    Intent intent = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
                    sendBroadcast(intent);
                    removeMapNotification(NOTIFICATION_ID_ACCESS);
                    masId1.stopObexServerSession();
                    masId2.stopObexServerSession();
                    break;
                case MSG_INTERNAL_AUTH_TIMEOUT:
                    Intent i = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
                    sendBroadcast(i);
                    removeMapNotification(NOTIFICATION_ID_AUTH);
                    notifyAuthCancelled();
                
                    masId1.stopObexServerSession();
                    masId2.stopObexServerSession();
                    break;
                case MSG_SERVERSESSION_CLOSE:
                    if(msg.arg1 == 0){
                        masId1.stopObexServerSession();
                    }
                    else if(msg.arg1 == 1){
                        masId2.stopObexServerSession();
                    }
                    break;
                case MSG_SESSION_ESTABLISHED:
                    break;
                case MSG_SESSION_DISCONNECTED:
                    break;
                case MSG_INTERNAL_CONNECTION_FAILED:
                    alertConnectionFailure();
                    masId1.stopObexServerSession();
                    masId2.stopObexServerSession();
                    break;
                default:
                    break;
            }
        }
    };
    private void createMapNotification(String action) {
        NotificationManager nm = (NotificationManager)
        getSystemService(Context.NOTIFICATION_SERVICE);

        // Create an intent triggered by clicking on the status icon.
        Intent clickIntent = new Intent();
        clickIntent.setClass(this, BluetoothMasActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        clickIntent.setAction(action);

        // Create an intent triggered by clicking on the
        // "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(this, BluetoothMasReceiver.class);

        Notification notification = null;
        String name = getRemoteDeviceName();

        if (action.equals(ACCESS_REQUEST_ACTION)) {
            deleteIntent.setAction(ACCESS_DISALLOWED_ACTION);
            notification = new Notification(android.R.drawable.stat_sys_data_bluetooth,
                getString(R.string.map_notif_ticker), System.currentTimeMillis());
            notification.setLatestEventInfo(this, getString(R.string.map_notif_title),
                    getString(R.string.map_notif_message, name), PendingIntent
                            .getActivity(this, 0, clickIntent, 0));

            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
            notification.defaults = Notification.DEFAULT_SOUND;
            notification.deleteIntent = PendingIntent.getBroadcast(this, 0, deleteIntent, 0);
            nm.notify(NOTIFICATION_ID_ACCESS, notification);
        }
    }

    private void alertConnectionFailure() {
        Toast.makeText(this, getString(R.string.map_alert_conn_failed_message),
                Toast.LENGTH_LONG).show();
    }

    private void removeMapNotification(int id) {
        Context context = getApplicationContext();
        NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id);
    }

    public static String getRemoteDeviceName() {
        return sRemoteDeviceName;
    }

    private void notifyAuthKeyInput(final String key) {
        synchronized (mAuthSync) {
            if (key != null) {
                mAuth.setSessionKey(key);
            }
            mAuth.setChallenged(true);
            mAuth.notify();
        }
    }

    private void notifyAuthCancelled() {
        synchronized (mAuthSync) {
            mAuth.setCancelled(true);
            mAuth.notify();
        }
    }

    private class BluetoothObexConnectManager {
        private volatile boolean mInterrupted;
        private BluetoothServerSocket mServerSocket = null;
        private SocketAcceptThread mAcceptThread = null;
        private BluetoothSocket mConnSocket = null;
        private ServerSession mServerSession = null;
        private boolean mHasStarted = false;
        private int mStartId = -1;
        private PowerManager.WakeLock mWakeLock = null;
        private BluetoothMasObexServer mMapServer = null;

        private int mSupportedMessageTypes;
        private int portNum;

        public BluetoothObexConnectManager(int supportedMessageTypes, int portNumber) {
                mSupportedMessageTypes = supportedMessageTypes;
                portNum = portNumber;
        }

        private void startRfcommSocketListener(BluetoothMns mnsClient) {
            if (VERBOSE)
                Log.v(TAG, "Map Service startRfcommSocketListener");

            if (mServerSocket == null) {
                if (!initSocket()) {
                    closeService();
                    return;
                }
            }
            if (mAcceptThread == null) {
                mAcceptThread = new SocketAcceptThread(mnsClient);
                mAcceptThread.setName("BluetoothMapAcceptThread");
                mAcceptThread.start();
            }
        }

        private final boolean initSocket() {
            if (VERBOSE)
                Log.e(TAG, "Map Service initSocket");

            boolean initSocketOK = true;
            final int CREATE_RETRY_TIME = 10;

            // It's possible that create will fail in some cases. retry for 10 times
            for (int i = 0; i < CREATE_RETRY_TIME && !mInterrupted; i++) {
                try {
                    mServerSocket = mAdapter.listenUsingRfcommOn(portNum);
                } catch (IOException e) {
                    Log.e(TAG, "Error create RfcommServerSocket " + e.toString());
                    initSocketOK = false;
                }

                if (!initSocketOK) {
                    synchronized (this) {
                        try {
                            if (VERBOSE)
                                Log.e(TAG, "wait 3 seconds");
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            Log.e(TAG, "socketAcceptThread thread was interrupted (3)");
                            mInterrupted = true;
                        }
                    }
                } else {
                    break;
                }
            }

            if (initSocketOK) {
                if (VERBOSE)
                    Log.e(TAG, "Succeed to create listening socket on channel "
                            + portNum);
            } else {
                Log.e(TAG, "Error to create listening socket after "
                        + CREATE_RETRY_TIME + " try");
            }
            return initSocketOK;
        }

        private final void closeSocket(boolean server, boolean accept)
                        throws IOException {
            if (server == true) {
                // Stop the possible trying to init serverSocket
                mInterrupted = true;

                if (mServerSocket != null) {
                    mServerSocket.close();
                    mServerSocket = null;
                }
            }
            if (accept == true) {
                if (mConnSocket != null) {
                    mConnSocket.close();
                    mConnSocket = null;
                }
            }
        }

        private final void closeService() {
            if (VERBOSE)
                Log.e(TAG, "Map Service closeService ");

            try {
                closeSocket(true, true);
            } catch (IOException ex) {
                Log.e(TAG, "CloseSocket error: " + ex);
            }

            if (mAcceptThread != null) {
                try {
                    mAcceptThread.shutdown();
                    mAcceptThread.join();
                    mAcceptThread = null;
                } catch (InterruptedException ex) {
                    Log.w(TAG, "mAcceptThread  close error" + ex);
                }
            }

            if (mServerSession != null) {
                mServerSession.close();
                mServerSession = null;
            }

            mHasStarted = false;
            if (stopSelfResult(mStartId)) {
                if (VERBOSE)
                    Log.e(TAG, "successfully stopped map service");
            }
        }

        private final void startObexServerSession(BluetoothMns mnsClient)
                        throws IOException {
            if (VERBOSE)
                Log.v(TAG, "Map Service startObexServerSession ");

            // acquire the wakeLock before start Obex transaction thread
            if (mWakeLock == null) {
                PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "StartingObexMapTransaction");
                mWakeLock.setReferenceCounted(false);
            }
            if(!mWakeLock.isHeld()) {
                Log.e(TAG,"Acquire partial wake lock");
                mWakeLock.acquire();
            }
            Context context = getApplicationContext();

            IBluetoothMasApp appIf = null;
            if (((mSupportedMessageTypes & ~MESSAGE_TYPE_SMS_MMS) == 0x00) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_SMS) != 0x00) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_MMS) != 0x00)) {
                // BluetoothMasAppZero if and only if both SMS and MMS
                appIf = new BluetoothMasAppZero(context, mSessionStatusHandler, mnsClient);
            } else if (((mSupportedMessageTypes & ~MESSAGE_TYPE_EMAIL) == 0x0) &&
                    ((mSupportedMessageTypes & MESSAGE_TYPE_EMAIL) != 0x0)) {
                // BluetoothMasAppOne if and only if email
                appIf = new BluetoothMasAppOne(context, mSessionStatusHandler, mnsClient);
            }

            mMapServer = new BluetoothMasObexServer(mSessionStatusHandler,
                    mRemoteDevice, context, appIf);
            synchronized (mAuthSync) {
                mAuth = new BluetoothMapAuthenticator(mSessionStatusHandler);
                mAuth.setChallenged(false);
                mAuth.setCancelled(false);
            }
            BluetoothMapRfcommTransport transport = new BluetoothMapRfcommTransport(
                    mConnSocket);
            mServerSession = new ServerSession(transport, mMapServer, mAuth);

            if (VERBOSE) {
                Log.e(TAG, "startObexServerSession() success!");
            }
        }

        private void stopObexServerSession() {
            if (VERBOSE)
                Log.e(TAG, "Map Service stopObexServerSession ");

            // Release the wake lock if obex transaction is over
            if (mWakeLock != null) {
                if (mWakeLock.isHeld()) {
                    Log.e(TAG,"Release full wake lock");
                    mWakeLock.release();
                    mWakeLock = null;
                }
                else{
                    mWakeLock = null;
                }
            }

            if (mServerSession != null) {
                mServerSession.close();
                mServerSession = null;
            }

            mAcceptThread = null;

            try {
                closeSocket(false, true);
                mConnSocket = null;
            } catch (IOException e) {
                Log.e(TAG, "closeSocket  error: " + e.toString());
            }
            // Last obex transaction is finished, we start to listen for incoming
            // connection again
            if (mAdapter.isEnabled()) {
                startRfcommSocketListener(mnsClient);
            }
        }

        /**
         * A thread that runs in the background waiting for remote rfcomm
         * connect.Once a remote socket connected, this thread shall be
         * shutdown.When the remote disconnect,this thread shall run again waiting
         * for next request.
         */
        private class SocketAcceptThread extends Thread {
            private boolean stopped = false;
            private BluetoothMns mnsObj;

            public SocketAcceptThread(BluetoothMns mnsClient){
                mnsObj = mnsClient;
            }

            @Override
            public void run() {
                while (!stopped) {
                    try {
                        mConnSocket = mServerSocket.accept();

                        mRemoteDevice = mConnSocket.getRemoteDevice();
                        if (mRemoteDevice == null) {
                            Log.i(TAG, "getRemoteDevice() = null");
                            break;
                        }
                        sRemoteDeviceName = mRemoteDevice.getName();
                        // In case getRemoteName failed and return null
                        if (TextUtils.isEmpty(sRemoteDeviceName)) {
                            sRemoteDeviceName = getString(R.string.defaultname);
                        }
                        boolean trust = mRemoteDevice.getTrustState();
                        if (VERBOSE)
                            Log.e(TAG, "GetTrustState() = " + trust);
                        if(uiRequestCalled == true){
                            trust = true;
                        }

                        if (trust) {
                            try {
                                Log.d(TAG, "trust is true::");
                                if (VERBOSE) Log.e( TAG, "incomming connection accepted from: "
                                    + sRemoteDeviceName + " automatically as trusted device");
                                startObexServerSession(mnsObj);
                            } catch (IOException ex) {
                                Log.e(TAG, "catch exception starting obex server session"
                                    + ex.toString());
                            }
                        } else {
                            Log.d(TAG, "trust is false::");
                            if(uiRequestCalled == false){
                                createMapNotification(ACCESS_REQUEST_ACTION);
                                uiRequestCalled = true;
                                if (VERBOSE) Log.e(TAG, "incomming connection accepted from: "
                                    + sRemoteDeviceName);
                                mSessionStatusHandler.sendMessageDelayed(
                                    mSessionStatusHandler.obtainMessage(MSG_INTERNAL_USER_TIMEOUT),
                                        USER_CONFIRM_TIMEOUT_VALUE);
                            }
                        }
                        stopped = true; // job done ,close this thread;
                    } catch (IOException ex) {
                        if (stopped) {
                            break;
                        }
                        if (VERBOSE)
                            Log.e(TAG, "Accept exception: " + ex.toString());
                    }
                }
            }

            void shutdown() {
                stopped = true;
                interrupt();
            }
        }
    }
};
