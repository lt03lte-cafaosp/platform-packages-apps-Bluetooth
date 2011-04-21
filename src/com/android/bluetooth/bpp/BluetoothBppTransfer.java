/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *        * Redistributions of source code must retain the above copyright
 *           notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above
 *          copyright notice, this list of conditions and the following
 *           disclaimer in the documentation and/or other materials provided
 *           with the distribution.
 *        * Neither the name of Code Aurora Forum, Inc. nor the names of its
 *           contributors may be used to endorse or promote products derived
 *           from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.bluetooth.bpp;

import javax.obex.ObexTransport;
import javax.obex.Authenticator;

import com.android.bluetooth.opp.BluetoothShare;
import com.android.bluetooth.opp.Constants;
import com.android.bluetooth.opp.BluetoothOppShareInfo;
import com.android.bluetooth.opp.BluetoothOppBatch;
import com.android.bluetooth.opp.BluetoothOppSendFileInfo;
import com.android.bluetooth.opp.BluetoothOppService;
import com.android.bluetooth.opp.BluetoothOppManager;
import com.android.bluetooth.bpp.BluetoothBppObexClientSession.SoapReqSeq;

import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.BluetoothUuid;
import android.os.ParcelUuid;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.Process;
import android.util.Log;
import android.app.AlertDialog;
import android.app.Activity;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * This class run an actual Opp transfer session (from connect target device to
 * disconnect)
 */
public class BluetoothBppTransfer implements BluetoothOppBatch.BluetoothOppBatchListener {
    private static final String TAG = "BluetoothBppTransfer";

    private static final boolean D = BluetoothBppConstant.DEBUG;

    private static final boolean V = BluetoothBppConstant.VERBOSE;

    public static final int RFCOMM_ERROR = 10;

    public static final int RFCOMM_CONNECTED = 11;

    public static final int RFCOMM_CONNECTED_EVT = 12;

    public static final int SDP_RESULT = 13;

    public static final int RFCOMM_CONNECT = 14;

    public static final int GET_PRINTER_ATTR = 15;

    public static final int CREATE_JOB = 16;

    public static final int GET_EVENT = 17;

    public static final int SEND_DOCUMENT = 18;

    public static final int START_EVENT_THREAD = 19;

    public static final int CANCEL = 20;

    public static final int MSG_OBEX_AUTH_CHALL = 21;

    public static final int USER_TIMEOUT = 22;

    /* GetEvent Messages */
    public static final int EVENT_GETEVENT_RESPONSE = 30;

    private static final int USER_CONFIRM_TIMEOUT_VALUE = 30000;

    private static final int CONNECT_WAIT_TIMEOUT = 45000;

    private static final int CONNECT_RETRY_TIME = 100;

    public static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.bpp.userconfirmtimeout";

    private Context mContext;

    private BluetoothAdapter mAdapter;

    static private BluetoothOppBatch mBatch;

    private BluetoothBppObexClientSession mSession;

    private BluetoothBppEvent mSessionEvent;

    private BluetoothOppShareInfo mCurrentShare;

    private ObexTransport mTransport;

    private ObexTransport mTransportEvent;

    private HandlerThread mHandlerThread;

    static EventHandler mSessionHandler;

    public static int JobChannel = -1;

    public static int StatusChannel = -1;

    static boolean bonding_process;
    /*
     * TODO check if we need PowerManager here
     */
    private PowerManager mPowerManager;

    private long mTimestamp;

    static private BluetoothBppAuthenticator mAuth = null;

    static public String mJobStatus;

    static public String mPrinterStateReason;

    static public String mOperationStatus;

    static public boolean mFileSending = false;

    private BluetoothOppSendFileInfo mFileInfo = null;

    public BluetoothBppTransfer(Context context, PowerManager powerManager,
            BluetoothOppBatch batch, BluetoothBppObexClientSession session) {

        mContext = context;
        mPowerManager = powerManager;
        mBatch = batch;
        mSession = session;

        mJobStatus = "waiting";
        mPrinterStateReason = "none";
        mOperationStatus = "0x0000";
        mFileSending = false;

        mBatch.registerListern(this);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public BluetoothBppTransfer(Context context, PowerManager powerManager,
                                     BluetoothOppBatch batch) {
        this(context, powerManager, batch, null);
    }

    public int getBatchId() {
        return mBatch.mId;
    }

    /*
     * Receives events from mConnectThread & mSession back in the main thread.
     */
    public class EventHandler extends Handler {
        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SDP_RESULT:
                    if (V) Log.v(TAG, "SDP request returned " + msg.arg1 + " (" +
                            (System.currentTimeMillis() - mTimestamp + " ms)"));
                    if (!((BluetoothDevice)msg.obj).equals(mBatch.mDestination)) {
                        return;
                    }
                    try {
                        mContext.unregisterReceiver(mReceiver);
                    } catch (IllegalArgumentException e) {
                        // ignore
                    }
                    if (msg.arg1 > 0) {
                        Intent in = new Intent(mContext, BluetoothBppActivity.class);
                        in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        in.putExtra("jobCh", msg.arg1);
                        in.putExtra("statCh", msg.arg2);
                        mContext.startActivity(in);
                    } else {
                        /* SDP query fail case */
                        Log.e(TAG, "SDP query failed!");
                        markBatchFailed(BluetoothShare.STATUS_CONNECTION_ERROR);
                        mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    }
                    break;
                case RFCOMM_CONNECT:
                    if (V) Log.v(TAG, "Get RFCOMM_CONNECT Message");
                    if (msg.arg1 > 0) {
                        mConnectThread = new SocketConnectThread(mBatch.mDestination, JobChannel);
                        mConnectThread.start();

                    } else {
                        /* SDP query fail case */
                        Log.e(TAG, "SDP query failed!");
                        markBatchFailed(BluetoothShare.STATUS_CONNECTION_ERROR);
                        mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    }
                    break;
                /*
                 * RFCOMM connect fail is for outbound share only! Mark batch
                 * failed, and all shares in batch failed
                 */
                case RFCOMM_ERROR:
                    if (V) Log.v(TAG, "receive RFCOMM_ERROR msg");
                    mConnectThread = null;
                    markBatchFailed(BluetoothShare.STATUS_CONNECTION_ERROR);
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    BluetoothBppActivity.mOPPstop = false;
                    if(BluetoothBppActivity.mContext != null){
                        ((Activity) BluetoothBppActivity.mContext).finish();
                    }
                    break;
                /*
                 * RFCOMM connected is for outbound share only! Create
                 * BluetoothOppObexClientSession and start it
                 */
                case RFCOMM_CONNECTED:
                    if (V) Log.v(TAG, "Transfer receive RFCOMM_CONNECTED msg");
                    mConnectThread = null;
                    mTransport = (ObexTransport)msg.obj;
                    startObexSession();
                    break;

                case RFCOMM_CONNECTED_EVT:
                    if (V) Log.v(TAG, "Transfer receive RFCOMM_CONNECTED_EVT msg");
                    mTransportEvent = (ObexTransport)msg.obj;
                    startEventThread();
                    break;

                case GET_PRINTER_ATTR:
                    if (V) Log.v(TAG, "Transfer receive GET_PRINTER_ATTR msg");
                    BluetoothBppObexClientSession.mSoapProcess =
                                            BluetoothBppObexClientSession.SoapReqSeq.GETATTRIBUTE;
                    break;

                case CREATE_JOB:
                    if (V) Log.v(TAG, "Transfer receive CREATE_JOB msg");
                    BluetoothBppObexClientSession.mSoapProcess =
                                                BluetoothBppObexClientSession.SoapReqSeq.CREATJOB;
                    break;

                case GET_EVENT:
                    if (V) Log.v(TAG, "Transfer receive GET_EVENT msg");
                    BluetoothBppObexClientSession.mSoapProcess =
                                                BluetoothBppObexClientSession.SoapReqSeq.GETEVENT;
                    break;

                case SEND_DOCUMENT:
                    if (V) Log.v(TAG, "Transfer receive SEND_DOCUMENT msg");
                    BluetoothBppObexClientSession.mSoapProcess =
                                            BluetoothBppObexClientSession.SoapReqSeq.SENDDOCUMENT;
                    break;

                case CANCEL:
                    if (V) Log.v(TAG, "Transfer receive CANCEL msg");
                    BluetoothBppObexClientSession.mSoapProcess =
                                                BluetoothBppObexClientSession.SoapReqSeq.CANCEL;
                    break;

                case START_EVENT_THREAD:
                    if (V) Log.v(TAG, "Transfer receive START_EVENT_THREAD msg");
                    if(StatusChannel == -1){
                        if (V) Log.v(TAG, "Status RFCOMM channel has not been set correctly");
                        return;
                    }
                    mConnectThreadEvent = new SocketConnectThread(mBatch.mDestination,
                                                                    StatusChannel);
                    mConnectThreadEvent.start();
                    break;

                case MSG_OBEX_AUTH_CHALL:
                    if (V) Log.v(TAG, "Transfer receive MSG_OBEX_AUTH_CHALL msg");
                    Intent in = new Intent(mContext, BluetoothBppAuthActivity.class);
                    in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(in);
                    mSessionHandler.sendMessageDelayed(mSessionHandler
                        .obtainMessage(USER_TIMEOUT), USER_CONFIRM_TIMEOUT_VALUE);
                    break;

                case USER_TIMEOUT:
                    if (V) Log.v(TAG, "Transfer receive USER_TIMEOUT msg");
                    Intent intent = new Intent(USER_CONFIRM_TIMEOUT_ACTION);
                    mContext.sendBroadcast(intent);
                    break;
                /*
                 * Put next share if available,or finish the transfer.
                 * For outbound session, call session.addShare() to send next file,
                 * or call session.stop().
                 * For inbounds session, do nothing. If there is next file to receive,it
                 * will be notified through onShareAdded()
                 */
                case BluetoothBppObexClientSession.MSG_SHARE_COMPLETE:
                    BluetoothOppShareInfo info = (BluetoothOppShareInfo)msg.obj;
                    if (V) Log.v(TAG, "receive MSG_SHARE_COMPLETE for info " + info.mId);
                    if (mBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                        mCurrentShare = mBatch.getPendingShare();

                        if (mCurrentShare != null) {
                            /* we have additional share to process */
                            if (V) Log.v(TAG, "continue session for info " + mCurrentShare.mId +
                                    " from batch " + mBatch.mId);
                            processCurrentShare();
                        } else {
                            /* for outbound transfer, all shares are processed */
                            if (V) Log.v(TAG, "Batch " + mBatch.mId + " is done");

                            mFileSending = false;
                            /* In case of JobStatus got completed before actual printing is done,
                                                    it need to stop event Thread here !!
                                                */
                            if ((mJobStatus.compareTo("completed")== 0) &&
                                                                    BluetoothBppEvent.mConnected){
                                mSessionEvent.stop();
                            }else if (BluetoothBppEvent.mConnected == false){
                                mSession.stop();
                            }
                        }
                    }
                    break;

                case BluetoothBppObexClientSession.MSG_SESSION_EVENT_COMPLETE:
                    if (V) Log.v(TAG, "receive MSG_SESSION_EVENT_COMPLETE");
                    /* Disconnect Status Channel */
                    mSessionEvent.stop();
                    break;

                case BluetoothBppObexClientSession.MSG_SESSION_STOP:
                    if (V) Log.v(TAG, "receive MSG_SESSION_STOP");
                    /* Disconnect Job Channel */
                    mSession.stop();
                    break;
              /*
                         * Handle session completed status Set batch status to
                         * finished
                         */
                case BluetoothBppObexClientSession.MSG_SESSION_COMPLETE:
                    BluetoothOppShareInfo info1 = (BluetoothOppShareInfo)msg.obj;
                    if (V) Log.v(TAG, "receive MSG_SESSION_COMPLETE for batch " + mBatch.mId);
                    mBatch.mStatus = Constants.BATCH_STATUS_FINISHED;

                    BluetoothBppActivity.mOPPstop = false;
                    BluetoothBppPrintPrefActivity.mOPPstop = false;

              /*
                         * trigger content provider again to know batch status change
                         */
                    tickShareStatus(info1);
                    if (V) Log.v(TAG, "mJobStatus: " + mJobStatus
                        + "\r\nmPrinterStateReason: " + mPrinterStateReason
                        + "\r\nmOperationStatus: " + mOperationStatus);

                    /* Show AlertDiaglog Message */
                    if(BluetoothBppStatusActivity.mContext != null){
                        String PrintResultMsg = null;
                        if (mJobStatus.compareTo("completed")== 0){
                            PrintResultMsg = "Printing Completed Successfully";
                        }
                        else{
                            if(mPrinterStateReason.compareTo("none") != 0)
                                PrintResultMsg = "Printing Fail due to \""
                                                        + mPrinterStateReason + "\"";
                            if(mOperationStatus.compareTo("0x0000") != 0)
                                PrintResultMsg = ((PrintResultMsg == null) ? "" :
                                (PrintResultMsg + "\r\n") ) + "Operation Fail ("
                                                        + mOperationStatus + ")";
                            if((mJobStatus.compareTo("stopped")== 0)
                                || (mJobStatus.compareTo("aborted")== 0)
                                || (mJobStatus.compareTo("cancelled")== 0)
                                || (mJobStatus.compareTo("unknown")== 0)){
                                PrintResultMsg = ((PrintResultMsg == null) ?
                                 "" :(PrintResultMsg + "\r\n") )+ "Printing has been " + mJobStatus;

                            }
                            if(BluetoothBppObexClientSession.mSoapProcess ==
                                                BluetoothBppObexClientSession.SoapReqSeq.CANCELLED){
                                PrintResultMsg = "Printing has been cancelled by user";
                            }

                            if (BluetoothBppEvent.mEventStatus != null){
                                String msgErr = BluetoothBppEvent.mEventStatus.toString();
                                msgErr = msgErr.substring("java.io.IOException: ".length());
                                PrintResultMsg = ((PrintResultMsg == null) ? "" :
                                                (PrintResultMsg + "\r\n") ) + msgErr;
                            } else if (BluetoothBppObexClientSession.mJobStatus != null){
                                String msgErr = BluetoothBppObexClientSession.mJobStatus.toString();
                                msgErr = msgErr.substring("java.io.IOException: ".length());
                                PrintResultMsg = ((PrintResultMsg == null) ? "" :
                                                (PrintResultMsg + "\r\n") ) + msgErr;
                            }
                        }
                        new AlertDialog.Builder(BluetoothBppStatusActivity.mContext)
                            .setTitle("Bluetooth Printing")
                            .setMessage(PrintResultMsg)
                            .setNeutralButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // TODO Auto-generated method stub
                                    ((Activity) BluetoothBppStatusActivity.mContext).finish();
                                }
                            }).show();
                        }
                    break;

                /* Handle the error state of an Obex session */
                case BluetoothBppObexClientSession.MSG_SESSION_ERROR:
                    if (V) Log.v(TAG, "receive MSG_SESSION_ERROR for batch " + mBatch.mId);
                    BluetoothOppShareInfo info2 = (BluetoothOppShareInfo)msg.obj;
                    if((BluetoothBppEvent.mConnected == true) && (mSessionEvent != null )){
                        mSessionEvent.stop();
                    }else{
                        mSession.stop();
                    }
                    mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                    markBatchFailed(info2.mStatus);
                    tickShareStatus(mCurrentShare);

                    if(BluetoothBppActivity.mContext != null){
                        ((Activity) BluetoothBppActivity.mContext).finish();
                    }
                    if(BluetoothBppPrintPrefActivity.mContext != null){
                        ((Activity) BluetoothBppPrintPrefActivity.mContext).finish();
                    }
                    if(BluetoothBppStatusActivity.mContext != null){
                        ((Activity) BluetoothBppStatusActivity.mContext).finish();
                    }
                    break;

                case BluetoothBppObexClientSession.MSG_SHARE_INTERRUPTED:
                    if (V) Log.v(TAG, "receive MSG_SHARE_INTERRUPTED for batch " + mBatch.mId);
                    BluetoothOppShareInfo info3 = (BluetoothOppShareInfo)msg.obj;
                    if (mBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                        try {
                            if (mTransport == null) {
                                Log.v(TAG, "receive MSG_SHARE_INTERRUPTED but mTransport = null");
                            } else {
                                mTransport.close();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "failed to close mTransport");
                        }
                        if (V) Log.v(TAG, "mTransport closed ");
                        mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
                        if (info3 != null) {
                            markBatchFailed(info3.mStatus);
                        } else {
                            markBatchFailed();
                        }
                        tickShareStatus(mCurrentShare);
                    }
                    break;

                case BluetoothBppObexClientSession.MSG_CONNECT_TIMEOUT:
                    if (V) Log.v(TAG, "receive MSG_CONNECT_TIMEOUT for batch " + mBatch.mId);
                    /* for outbound transfer, the block point is BluetoothSocket.write()
                     * The only way to unblock is to tear down lower transport
                     * */
                    if (mBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                        try {
                            if (mTransport == null) {
                                Log.v(TAG, "receive MSG_SHARE_INTERRUPTED but mTransport = null");
                            } else {
                                mTransport.close();
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "failed to close mTransport");
                        }
                        if (V) Log.v(TAG, "mTransport closed ");
                    } else {
                        /*
                         * For inbound transfer, the block point is waiting for
                         * user confirmation we can interrupt it nicely
                         */

                        // Remove incoming file confirm notification
                        NotificationManager nm = (NotificationManager)mContext
                                .getSystemService(Context.NOTIFICATION_SERVICE);
                        nm.cancel(mCurrentShare.mId);
                        // Send intent to UI for timeout handling
                        Intent i = new Intent(BluetoothShare.USER_CONFIRMATION_TIMEOUT_ACTION);
                        mContext.sendBroadcast(i);

                        markShareTimeout(mCurrentShare);
                    }
                    break;
            }
        }
    }

    private void markShareTimeout(BluetoothOppShareInfo share) {
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + share.mId);
        ContentValues updateValues = new ContentValues();
        updateValues
                .put(BluetoothShare.USER_CONFIRMATION, BluetoothShare.USER_CONFIRMATION_TIMEOUT);
        mContext.getContentResolver().update(contentUri, updateValues, null, null);
    }

    private void markBatchFailed(int failReason) {
        synchronized (this) {
            try {
                wait(1000);
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for markBatchFailed");
            }
        }

        if (D) Log.d(TAG, "Mark all ShareInfo in the batch as failed");
        if (mCurrentShare != null) {
            if (V) Log.v(TAG, "Current share has status " + mCurrentShare.mStatus);
            if (BluetoothShare.isStatusError(mCurrentShare.mStatus)) {
                failReason = mCurrentShare.mStatus;
            }
            if (mCurrentShare.mDirection == BluetoothShare.DIRECTION_INBOUND
                    && mCurrentShare.mFilename != null) {
                new File(mCurrentShare.mFilename).delete();
            }
        }

        BluetoothOppShareInfo info = mBatch.getPendingShare();
        while (info != null) {
            if (info.mStatus < 200) {
                info.mStatus = failReason;
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + info.mId);
                ContentValues updateValues = new ContentValues();
                updateValues.put(BluetoothShare.STATUS, info.mStatus);
                /* Update un-processed outbound transfer to show some info */
                if (info.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                    BluetoothOppSendFileInfo fileInfo = null;
                    fileInfo = BluetoothOppSendFileInfo.generateFileInfo(mContext, info.mUri,
                            info.mMimetype, info.mDestination);
                    if (fileInfo.mFileName != null) {
                        updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                        updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                        updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                    }
                } else {
                    if (info.mStatus < 200 && info.mFilename != null) {
                        new File(info.mFilename).delete();
                    }
                }
                mContext.getContentResolver().update(contentUri, updateValues, null, null);
                Constants.sendIntentIfCompleted(mContext, contentUri, info.mStatus);
            }
            info = mBatch.getPendingShare();
        }

    }

    private void markBatchFailed() {
        markBatchFailed(BluetoothShare.STATUS_UNKNOWN_ERROR);
    }

    /*
     * NOTE
     * For outbound transfer
     * 1) Check Bluetooth status
     * 2) Start handler thread
     * 3) new a thread to connect to target device
     * 3.1) Try a few times to do SDP query for target device OPUSH channel
     * 3.2) Try a few seconds to connect to target socket
     * 4) After BluetoothSocket is connected,create an instance of RfcommTransport
     * 5) Create an instance of BluetoothOppClientSession
     * 6) Start the session and process the first share in batch
     * For inbound transfer
     * The transfer already has session and transport setup, just start it
     * 1) Check Bluetooth status
     * 2) Start handler thread
     * 3) Start the session and process the first share in batch
     */
    /**
     * Start the transfer
     */
    public void start() {
        /* check Bluetooth enable status */
        /*
         * normally it's impossible to reach here if BT is disabled. Just check
         * for safety
         */
        if (!mAdapter.isEnabled()) {
            Log.e(TAG, "Can't start transfer when Bluetooth is disabled for " + mBatch.mId);
            markBatchFailed();
            mBatch.mStatus = Constants.BATCH_STATUS_FAILED;
            return;
        }

        if (mHandlerThread == null) {
            if (V) Log.v(TAG, "Create handler thread for batch " + mBatch.mId);
            mHandlerThread = new HandlerThread("BtBpp Transfer Handler");
            mHandlerThread.start();
            mSessionHandler = new EventHandler(mHandlerThread.getLooper());

            if (mBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
                /* for outbound transfer, we do connect first */
                startSDPSession();
            } else if (mBatch.mDirection == BluetoothShare.DIRECTION_INBOUND) {
                if (V) Log.v(TAG, "BPP doesn't support Server !!");
            }
        }
    }

    /**
     * Stop the transfer
     */
    public void stop() {
        if (V) Log.v(TAG, "stop");
        if (mConnectThread != null) {
            try {
                mConnectThread.interrupt();
                if (V) Log.v(TAG, "waiting for connect thread to terminate");
                mConnectThread.join();
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for connect thread to join");
            }
            mConnectThread = null;
        }
        if (mSession != null) {
            if (V) Log.v(TAG, "Stop mSession");
            mSession.stop();
        }
        if (mHandlerThread != null) {
            mHandlerThread.getLooper().quit();
            mHandlerThread.interrupt();
            mHandlerThread = null;
        }
    }

    private void startObexSession() {

        mBatch.mStatus = Constants.BATCH_STATUS_RUNNING;

        mCurrentShare = mBatch.getPendingShare();
        if (mCurrentShare == null) {
            /*
             * TODO catch this error
             */
            Log.e(TAG, "Unexpected error happened !");
            return;
        }
        if (V) Log.v(TAG, "Start session for info " + mCurrentShare.mId + " for batch " +
                mBatch.mId);

        if (mBatch.mDirection == BluetoothShare.DIRECTION_OUTBOUND) {
            if (V) Log.v(TAG, "Create Client session with transport " + mTransport.toString());
            mSession = new BluetoothBppObexClientSession(mContext, mTransport);
        } else if (mBatch.mDirection == BluetoothShare.DIRECTION_INBOUND) {
            if (V) Log.v(TAG, "BPP doesn't support server");
            return;
        }

        synchronized (this) {
            mAuth = new BluetoothBppAuthenticator(mSessionHandler);
            mAuth.setChallenged(false);
            mAuth.setCancelled(false);
            mConnectThreadEvent = null;
        }

        mSession.start(mSessionHandler, mAuth);
        processCurrentShare();
    }

    static public void notifyAuthKeyInput(final String key) {
        synchronized (mAuth) {
            if (key != null) {
                mAuth.setSessionKey(key);
            }
            mAuth.setChallenged(true);
            mAuth.notify();
        }

        mSessionHandler.removeMessages(USER_TIMEOUT);
    }

    static public void notifyAuthCancelled() {
        synchronized (mAuth) {
            mAuth.setCancelled(true);
            mAuth.notify();
        }

        mSessionHandler.removeMessages(USER_TIMEOUT);
    }


    static public String getRemoteDeviceName(){
        if(mBatch.mDestination != null){
            return mBatch.mDestination .getName();
        }
        return null;
    }

    private void startEventThread() {

        if (V) Log.v(TAG, "Create Client session with transport " + mTransportEvent.toString());
        mSessionEvent = new BluetoothBppEvent(mContext, mTransportEvent);
        mSessionEvent.start(mSessionHandler);
    }


    private void processCurrentShare() {
        /* This transfer need user confirm */
        if (V) Log.v(TAG, "processCurrentShare" + mCurrentShare.mId);
        mSession.addShare(mCurrentShare);
    }

    /**
     * Set transfer confirmed status. It should only be called for inbound
     * transfer
     */
    public void setConfirmed() {
        /* unblock server session */
        final Thread notifyThread = new Thread("Server Unblock thread") {
            public void run() {
                synchronized (mSession) {
                    mSession.unblock();
                    mSession.notify();
                }
            }
        };
        if (V) Log.v(TAG, "setConfirmed to unblock mSession" + mSession.toString());
        notifyThread.start();
    }

    private void startSDPSession() {
        if (V) Log.v(TAG, "startSDPSession");

        JobChannel = BluetoothBppPreference.getInstance(mContext).getChannel(
                    mBatch.mDestination, BluetoothBppConstant.DPS_UUID16);

        StatusChannel = BluetoothBppPreference.getInstance(mContext).getChannel(
                    mBatch.mDestination, BluetoothBppConstant.STS_UUID16);

        String docFormats = BluetoothBppPreference.getInstance(mContext).getFeatures(
                    mBatch.mDestination);

        if(IsNotSupportedDocFormats(docFormats)) return;

        if ((JobChannel != -1) && (StatusChannel !=-1) && (docFormats != null)) {
            if (D) Log.d(TAG, "Get BPP Job channel: " + JobChannel +
                ", Status Channel: " + StatusChannel + " from cache for " +
                mBatch.mDestination);
            mTimestamp = System.currentTimeMillis();
            mSessionHandler.obtainMessage(SDP_RESULT, JobChannel, StatusChannel,
                                    mBatch.mDestination).sendToTarget();
        } else {
            doBppSdp();
        }
    }

    private void doBppSdp() {
        if (V) Log.v(TAG, "Do BPP SDP request for address " + mBatch.mDestination);

        mTimestamp = System.currentTimeMillis();
        String docFormats = mBatch.mDestination.getFeature("SupportedFormats");
        if(IsNotSupportedDocFormats(docFormats)) return;

        JobChannel = mBatch.mDestination.getServiceChannel(BluetoothUuid.DirectPrinting);
        StatusChannel = mBatch.mDestination.getServiceChannel(BluetoothUuid.PrintingStatus);

        if ((JobChannel != -1) && (StatusChannel !=-1) && (docFormats != null)) {
            if (D) Log.d(TAG, "Get BPP Job channel: " + JobChannel +
                ", Status Channel: " + StatusChannel + " from SDP for " +
                mBatch.mDestination);

            mSessionHandler.obtainMessage(SDP_RESULT, JobChannel, StatusChannel,
                                    mBatch.mDestination).sendToTarget();
            return;

        } else {
            if (V) Log.v(TAG, "Remote Service channel not in cache");

            if (!mBatch.mDestination.fetchUuidsWithSdp()) {
                Log.e(TAG, "Start SDP query failed");
            } else {
                // we expect framework send us Intent ACTION_UUID. otherwise we will fail
                if (V) Log.v(TAG, "Start new SDP, wait for result");
                bonding_process = false;
                IntentFilter filter = new IntentFilter();
                filter.addAction(BluetoothDevice.ACTION_UUID);
                filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                mContext.registerReceiver(mReceiver, filter);
                return;
            }
        }
        Message msg = mSessionHandler.obtainMessage(SDP_RESULT, JobChannel,
                                                    StatusChannel, mBatch.mDestination);
        mSessionHandler.sendMessageDelayed(msg, 2000);
    }

    private boolean IsNotSupportedDocFormats(String docFormats){
        // Overwriting previous value with cache from BluetoothDevice
        BluetoothBppConstant.mSupportedDocs = docFormats;

        if(docFormats != null){
            if (V) Log.v(TAG, "Printer supports doc format: " + docFormats);

            String currFileType = BluetoothOppManager.getInstance(mContext).getSendingFileTypeInfo();
            String currFileName = BluetoothOppManager.getInstance(mContext).getSendingFileNameInfo();
            if (V) Log.v(TAG, "File Type: " + currFileType + "\r\nFile Name: "+ currFileName);

            if(docFormats.indexOf(currFileType, 0) == -1 ){
                if(checkUnknownMimetype(currFileType, currFileName) != null){
                    return false;
                }
                if (V) Log.v(TAG, "No match doc format, let OPP handle it !!");
                BluetoothOppService.mTransfer.start();
                return true;
            }
        }
        return false;

    }

    static public String checkUnknownMimetype(String fileType, String fileName){
        String mimeType = null;
        if(fileType == null || fileName == null)
            return null;
        if(fileType.compareTo("application/*") != 0)
            return null;

        if(fileName.indexOf(".vcf", 0) != -1){
            mimeType = "text/x-vcard";
        }else if(fileName.indexOf(".vcs", 0) != -1){
            mimeType = "text/x-vcalendar";
        }else if(fileName.indexOf(".vmg", 0) != -1){
            mimeType = "text/x-vmessage";
        }else if(fileName.indexOf(".ical", 0) != -1){
            mimeType = "text/calendar";
        }else if(fileName.indexOf(".msg", 0) != -1){
            mimeType = "text/x-vmessage";
        }
        return mimeType;
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(BluetoothDevice.ACTION_UUID)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (V) Log.v(TAG, "ACTION_UUID for device " + device + "requested device: "
                                                                        + mBatch.mDestination);
                if (device.equals(mBatch.mDestination)) {
                    Parcelable[] uuid = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    if (uuid != null) {
                        ParcelUuid[] uuids = new ParcelUuid[uuid.length];
                        for (int i = 0; i < uuid.length; i++) {
                            uuids[i] = (ParcelUuid)uuid[i];
                        }
                        // Get Printer Supported Documents list
                        String docFormats = mBatch.mDestination.getFeature("SupportedFormats");
                        if(IsNotSupportedDocFormats(docFormats)){
                            try {
                                mContext.unregisterReceiver(mReceiver);
                            } catch (IllegalArgumentException e) {
                                // ignore
                            }
                            return;
                        }
                        // Get BPP DirectPrinting RFCOMM Channel number
                        if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.DirectPrinting)) {
                            JobChannel = mBatch.mDestination
                                    .getServiceChannel(BluetoothUuid.DirectPrinting);

                            if (V) Log.v(TAG, "SDP get BPP DTS Channel: " + JobChannel);
                        }
                        // Get BPP Status RFCOMM Channel number
                        if (BluetoothUuid.isUuidPresent(uuids, BluetoothUuid.PrintingStatus)) {
                            StatusChannel = mBatch.mDestination
                                .getServiceChannel(BluetoothUuid.PrintingStatus);
                            if (V) Log.v(TAG, "SDP get BPP STS Channel: " + StatusChannel);
                        }
                    }
                    if(bonding_process == false){
                        mSessionHandler.obtainMessage(SDP_RESULT, JobChannel, StatusChannel,
                                                    mBatch.mDestination).sendToTarget();
                    }
                }
            }
            else if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                bonding_process = true;
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE,
                                                   BluetoothDevice.ERROR);

                if (V) Log.v(TAG, "ACTION_BOND_STATE_CHANGED - " + bondState );
                if (bondState == BluetoothDevice.BOND_BONDED ){
                    bonding_process = false;
                    if (V) Log.v(TAG, "Start SDP now ");
                    if (!mBatch.mDestination.fetchUuidsWithSdp()) {
                        Log.e(TAG, "Start SDP query failed");
                        Message msg = mSessionHandler.obtainMessage(SDP_RESULT, -1, -1,
                                                                    mBatch.mDestination);
                        mSessionHandler.sendMessageDelayed(msg, 2000);
                    }
                }
            }
        }
    };

    private SocketConnectThread mConnectThread;
    private SocketConnectThread mConnectThreadEvent;

    private class SocketConnectThread extends Thread {
        private final String host;

        private final BluetoothDevice device;

        private final int channel;

        private boolean isConnected;

        private long timestamp;

        private BluetoothSocket btSocket = null;

        /* create a TCP socket */
        public SocketConnectThread(String host, int port, int dummy) {
            super("Socket Connect Thread");
            this.host = host;
            this.channel = port;
            this.device = null;
            isConnected = false;
        }

        /* create a Rfcomm Socket */
        public SocketConnectThread(BluetoothDevice device, int channel) {
            super("Socket Connect Thread");
            this.device = device;
            this.host = null;
            this.channel = channel;
            isConnected = false;
        }

        public void interrupt() {
            if (!Constants.USE_TCP_DEBUG) {
                if (btSocket != null) {
                    try {
                        btSocket.close();
                    } catch (IOException e) {
                        Log.v(TAG, "Error when close socket");
                    }
                }
            }
        }

        @Override
        public void run() {

            timestamp = System.currentTimeMillis();

            /* Use BluetoothSocket to connect */
            try {
                if (V) Log.v(TAG, "Rfcomm socket Connection init: " + device.getAddress());

                btSocket = device.createInsecureRfcommSocket(channel);
            } catch (IOException e1) {
                Log.e(TAG, "Rfcomm socket create error");
                markConnectionFailed(btSocket);
                return;
            }
            try {
                btSocket.connect();

                if (V) Log.v(TAG, "Rfcomm socket connection attempt took " +
                        (System.currentTimeMillis() - timestamp) + " ms");
                BluetoothBppRfcommTransport transport;
                transport = new BluetoothBppRfcommTransport(btSocket);
                BluetoothBppPreference.getInstance(mContext).setChannel(device,
                                        BluetoothBppConstant.DPS_UUID16, JobChannel);
                BluetoothBppPreference.getInstance(mContext).setChannel(device,
                                        BluetoothBppConstant.STS_UUID16, StatusChannel);
                BluetoothBppPreference.getInstance(mContext).setName(device, device.getName());
                BluetoothBppPreference.getInstance(mContext).setFeatures(device,
                                        BluetoothBppConstant.mSupportedDocs);
                if (V) Log.v(TAG, "Send transport message " + transport.toString());
                if(channel == JobChannel){
                    mSessionHandler.obtainMessage(RFCOMM_CONNECTED, transport).sendToTarget();
                } else {
                    mSessionHandler.obtainMessage(RFCOMM_CONNECTED_EVT, transport).sendToTarget();
                }
            } catch (IOException e) {
                Log.e(TAG, "Rfcomm socket connect exception " + e);
                BluetoothBppPreference.getInstance(mContext)
                        .removeChannel(device, BluetoothBppConstant.DPS_UUID16);
                BluetoothBppPreference.getInstance(mContext)
                        .removeChannel(device, BluetoothBppConstant.STS_UUID16);
                markConnectionFailed(btSocket);
                return;
            }
        }

        private void markConnectionFailed(Socket s) {
            try {
                s.close();
            } catch (IOException e) {
                Log.e(TAG, "TCP socket close error");
            }
            mSessionHandler.obtainMessage(RFCOMM_ERROR).sendToTarget();
        }

        private void markConnectionFailed(BluetoothSocket s) {
            try {
                s.close();
            } catch (IOException e) {
                if (V) Log.e(TAG, "Error when close socket");
            }
            mSessionHandler.obtainMessage(RFCOMM_ERROR).sendToTarget();
            return;
        }
    };

    /* update a trivial field of a share to notify Provider the batch status change */
    private void tickShareStatus(BluetoothOppShareInfo share) {
        if(share == null){
            if (V) Log.v(TAG, "tickShareStatus() - share is null");
            return;
        }
        Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + share.mId);
        ContentValues updateValues = new ContentValues();
        updateValues.put(BluetoothShare.DIRECTION, share.mDirection);
        mContext.getContentResolver().update(contentUri, updateValues, null, null);
    }

    /*
     * Note: For outbound transfer We don't implement this method now. If later
     * we want to support merging a later added share into an existing session,
     * we could implement here For inbounds transfer add share means it's
     * multiple receive in the same session, we should handle it to fill it into
     * mSession
     */
    /**
     * Process when a share is added to current transfer
     */
    public void onShareAdded(int id) {
        BluetoothOppShareInfo info = mBatch.getPendingShare();
        if (info.mDirection == BluetoothShare.DIRECTION_INBOUND) {
            mCurrentShare = mBatch.getPendingShare();
            /*
             * TODO what if it's not auto confirmed?
             */
            if (mCurrentShare != null
                    && mCurrentShare.mConfirm == BluetoothShare.USER_CONFIRMATION_AUTO_CONFIRMED) {
                /* have additional auto confirmed share to process */
                if (V) Log.v(TAG, "Transfer continue session for info " + mCurrentShare.mId +
                        " from batch " + mBatch.mId);
                processCurrentShare();
                setConfirmed();
            }
        }
    }

    /*
     * NOTE We don't implement this method now. Now delete a single share from
     * the batch means the whole batch should be canceled. If later we want to
     * support single cancel, we could implement here For outbound transfer, if
     * the share is currently in transfer, cancel it For inbounds transfer,
     * delete share means the current receiving file should be canceled.
     */
    /**
     * Process when a share is deleted from current transfer
     */
    public void onShareDeleted(int id) {

    }

    /**
     * Process when current transfer is canceled
     */
    public void onBatchCanceled() {
        if (V) Log.v(TAG, "Transfer on Batch canceled");
        this.stop();
        mBatch.mStatus = Constants.BATCH_STATUS_FINISHED;
    }
}
