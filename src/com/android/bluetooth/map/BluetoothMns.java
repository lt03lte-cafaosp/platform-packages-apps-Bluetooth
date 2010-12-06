/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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



import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.CursorJoiner;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.Parcelable;
import android.os.Process;
import android.util.Log;

import com.android.bluetooth.map.MapUtils.MapUtils;

import javax.obex.ObexTransport;

/**
 * This class run an MNS session.
 */
public class BluetoothMns {
    private static final String TAG = "BtMns";

    private static final boolean D = BluetoothMasService.DEBUG;

    private static final boolean V = BluetoothMasService.VERBOSE;

    public static final int RFCOMM_ERROR = 10;

    public static final int RFCOMM_CONNECTED = 11;

    public static final int SDP_RESULT = 12;

    public static final int MNS_CONNECT = 13;

    public static final int MNS_DISCONNECT = 14;

    public static final int MNS_SEND_EVENT = 15;

    private static final int CONNECT_WAIT_TIMEOUT = 45000;

    private static final int CONNECT_RETRY_TIME = 100;

    private static final short MNS_UUID16 = 0x1133;

    public static final String NEW_MESSAGE = "NewMessage";

    public static final String DELIVERY_SUCCESS = "DeliverySuccess";

    public static final String SENDING_SUCCESS = "SendingSuccess";

    public static final String DELIVERY_FAILURE = "DeliveryFailure";

    public static final String SENDING_FAILURE = "SendingFailure";

    public static final String MEMORY_FULL = "MemoryFull";

    public static final String MEMORY_AVAILABLE = "MemoryAvailable";

    public static final String MESSAGE_DELETED = "MessageDeleted";

    public static final String MESSAGE_SHIFT = "MessageShift";

    private Context mContext;

    private BluetoothAdapter mAdapter;

    private BluetoothMnsObexSession mSession;

    private int mStartId = -1;

    private ObexTransport mTransport;

    private HandlerThread mHandlerThread;

    private EventHandler mSessionHandler;

    private BluetoothDevice mDestination;

    private MapUtils mu = null;

    public static final ParcelUuid BluetoothUuid_ObexMns = ParcelUuid
            .fromString("00001133-0000-1000-8000-00805F9B34FB");

    private long mTimestamp;

    public BluetoothMns(Context context) {
        /* check Bluetooth enable status */
        /*
         * normally it's impossible to reach here if BT is disabled. Just check
         * for safety
         */

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mContext = context;

        mDestination = BluetoothMasService.mRemoteDevice;

        mu = new MapUtils();

        if (!mAdapter.isEnabled()) {
            Log.e(TAG, "Can't send event when Bluetooth is disabled ");
            return;
        }

        if (mHandlerThread == null) {
            if (V) Log.v(TAG, "Create handler thread for batch ");
            mHandlerThread = new HandlerThread("Bt MNS Transfer Handler",
                    Process.THREAD_PRIORITY_BACKGROUND);
            mHandlerThread.start();
            mSessionHandler = new EventHandler(mHandlerThread.getLooper());
        }
    }

    public Handler getHandler() {
        return mSessionHandler;
    }

    /*
     * Receives events from mConnectThread & mSession back in the main thread.
     */
    private class EventHandler extends Handler {
        public EventHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, " Handle Message " + msg.what);
            switch (msg.what) {
            case MNS_CONNECT:
                if (mSession != null) {
                    Log.d(TAG, "Disconnect previous obex connection");
                    mSession.disconnect();
                    mSession = null;
                }
                    start((BluetoothDevice) msg.obj);
                break;
            case MNS_DISCONNECT:
                deregisterUpdates();
                stop();
                break;
            case SDP_RESULT:
                if (V) Log.v(TAG, "SDP request returned " + msg.arg1
                    + " (" + (System.currentTimeMillis() - mTimestamp + " ms)"));
                if (!((BluetoothDevice) msg.obj).equals(mDestination)) {
                    return;
                }
                try {
                    mContext.unregisterReceiver(mReceiver);
                } catch (IllegalArgumentException e) {
                    // ignore
                }
                if (msg.arg1 > 0) {
                    mConnectThread = new SocketConnectThread(mDestination,
                        msg.arg1);
                    mConnectThread.start();
                } else {
                    /* SDP query fail case */
                    Log.e(TAG, "SDP query failed!");
                }

                break;

            /*
             * RFCOMM connect fail is for outbound share only! Mark batch
             * failed, and all shares in batch failed
             */
            case RFCOMM_ERROR:
                if (V) Log.v(TAG, "receive RFCOMM_ERROR msg");
                mConnectThread = null;

                break;
            /*
             * RFCOMM connected. Do an OBEX connect by starting the session
             */
            case RFCOMM_CONNECTED:
                if (V) Log.v(TAG, "Transfer receive RFCOMM_CONNECTED msg");
                mConnectThread = null;
                mTransport = (ObexTransport) msg.obj;
                startObexSession();
                registerUpdates();

                break;

            /* Handle the error state of an Obex session */
            case BluetoothMnsObexSession.MSG_SESSION_ERROR:
                if (V) Log.v(TAG, "receive MSG_SESSION_ERROR");
                deregisterUpdates();
                mSession.disconnect();
                mSession = null;
                break;

            case BluetoothMnsObexSession.MSG_CONNECT_TIMEOUT:
                if (V) Log.v(TAG, "receive MSG_CONNECT_TIMEOUT");
                /*
                 * for outbound transfer, the block point is
                 * BluetoothSocket.write() The only way to unblock is to tear
                 * down lower transport
                 */
                try {
                    if (mTransport == null) {
                        Log.v(TAG,"receive MSG_SHARE_INTERRUPTED but " +
                                "mTransport = null");
                    } else {
                        mTransport.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "failed to close mTransport");
                }
                if (V) Log.v(TAG, "mTransport closed ");

                break;

            case MNS_SEND_EVENT:
                sendEvent((String) msg.obj);
                break;
            }
        }
    }

    /**
     * Post a MNS Event to the MNS thread
     */
    public void sendMnsEvent(String msg, String handle, String folder,
            String old_folder, String msgType) {
        String str = mu.mapEventReportXML(msg, handle, folder, old_folder,
                msgType);
        mSessionHandler.obtainMessage(MNS_SEND_EVENT, -1, -1, str)
                .sendToTarget();
    }

    /**
     * Push the message over Obex client session
     */
    private void sendEvent(String str) {
        if (str != null && (str.length() > 0)) {

            Log.d(TAG, "--------------");
            Log.d(TAG, " CONTENT OF EVENT REPORT FILE: " + str);

            final String FILENAME = "EventReport";
            FileOutputStream fos = null;
            File file = new File(mContext.getFilesDir() + "/" + FILENAME);
            file.delete();
            try {
                fos = mContext.openFileOutput(FILENAME, Context.MODE_PRIVATE);
                fos.write(str.getBytes());
                fos.flush();
                fos.close();
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            File fileR = new File(mContext.getFilesDir() + "/" + FILENAME);
            if (fileR.exists() == true) {
                Log.d(TAG, " Sending event report file ");
                mSession.sendEvent(fileR, (byte) 0);
            } else {
                Log.d(TAG, " ERROR IN CREATING SEND EVENT OBJ FILE");
            }
        }
    }

    private boolean updatesRegistered = false;

    /**
     * Register with content provider to receive updates
     * of change on cursor.
     */
    private void registerUpdates() {

        Log.d(TAG, "REGISTER MNS UPDATES");

        Uri smsUri = Uri.parse("content://sms/");
        crSmsA = mContext.getContentResolver().query(smsUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsB = mContext.getContentResolver().query(smsUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsInboxUri = Uri.parse("content://sms/inbox/");
        crSmsInboxA = mContext.getContentResolver().query(smsInboxUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsInboxB = mContext.getContentResolver().query(smsInboxUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsSentUri = Uri.parse("content://sms/sent/");
        crSmsSentA = mContext.getContentResolver().query(smsSentUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsSentB = mContext.getContentResolver().query(smsSentUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsDraftUri = Uri.parse("content://sms/draft/");
        crSmsDraftA = mContext.getContentResolver().query(smsDraftUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsDraftB = mContext.getContentResolver().query(smsDraftUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsOutboxUri = Uri.parse("content://sms/outbox/");
        crSmsOutboxA = mContext.getContentResolver().query(smsOutboxUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsOutboxB = mContext.getContentResolver().query(smsOutboxUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsFailedUri = Uri.parse("content://sms/failed/");
        crSmsFailedA = mContext.getContentResolver().query(smsFailedUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsFailedB = mContext.getContentResolver().query(smsFailedUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsQueuedUri = Uri.parse("content://sms/queued/");
        crSmsQueuedA = mContext.getContentResolver().query(smsQueuedUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");
        crSmsQueuedB = mContext.getContentResolver().query(smsQueuedUri,
                new String[] { "_id", "body", "type" }, null, null, "_id asc");

        Uri smsObserverUri = Uri.parse("content://mms-sms/");
        mContext.getContentResolver().registerContentObserver(smsObserverUri,
                true, smsContentObserver);

        Uri smsInboxObserverUri = Uri.parse("content://mms-sms/inbox");
        mContext.getContentResolver().registerContentObserver(
                smsInboxObserverUri, true, inboxContentObserver);

        Uri smsSentObserverUri = Uri.parse("content://mms-sms/sent");
        mContext.getContentResolver().registerContentObserver(
                smsSentObserverUri, true, sentContentObserver);

        Uri smsDraftObserverUri = Uri.parse("content://mms-sms/draft");
        mContext.getContentResolver().registerContentObserver(
                smsDraftObserverUri, true, draftContentObserver);

        Uri smsOutboxObserverUri = Uri.parse("content://mms-sms/outbox");
        mContext.getContentResolver().registerContentObserver(
                smsOutboxObserverUri, true, outboxContentObserver);

        Uri smsFailedObserverUri = Uri.parse("content://mms-sms/failed");
        mContext.getContentResolver().registerContentObserver(
                smsFailedObserverUri, true, failedContentObserver);

        Uri smsQueuedObserverUri = Uri.parse("content://mms-sms/queued");
        mContext.getContentResolver().registerContentObserver(
                smsQueuedObserverUri, true, queuedContentObserver);

        updatesRegistered = true;
        Log.d(TAG, " ---------------- ");
        Log.d(TAG, " REGISTERED UPDATES ");
        Log.d(TAG, " ---------------- ");
    }

    /**
     * Stop listening to changes in cursor
     */
    private void deregisterUpdates() {

        if ( updatesRegistered == true ){
            Log.d(TAG, "DEREGISTER MNS SMS UPDATES");
            mContext.getContentResolver().unregisterContentObserver(
                    smsContentObserver);
            mContext.getContentResolver().unregisterContentObserver(
                    inboxContentObserver);
            mContext.getContentResolver().unregisterContentObserver(
                    sentContentObserver);
            mContext.getContentResolver().unregisterContentObserver(
                    draftContentObserver);
            mContext.getContentResolver().unregisterContentObserver(
                    outboxContentObserver);
            mContext.getContentResolver().unregisterContentObserver(
                    failedContentObserver);
            mContext.getContentResolver().unregisterContentObserver(
                    queuedContentObserver);

            crSmsA.close();
            crSmsB.close();
            currentCRSms = CR_SMS_A;
            crSmsInboxA.close();
            crSmsInboxB.close();
            currentCRSmsInbox = CR_SMS_INBOX_A;
            crSmsSentA.close();
            crSmsSentB.close();
            currentCRSmsSent = CR_SMS_SENT_A;
            crSmsDraftA.close();
            crSmsDraftB.close();
            currentCRSmsDraft = CR_SMS_DRAFT_A;
            crSmsOutboxA.close();
            crSmsOutboxB.close();
            currentCRSmsOutbox = CR_SMS_OUTBOX_A;
            crSmsFailedA.close();
            crSmsFailedB.close();
            currentCRSmsFailed = CR_SMS_FAILED_A;
            crSmsQueuedA.close();
            crSmsQueuedB.close();
            currentCRSmsQueued = CR_SMS_QUEUED_A;
        }

    }

    private SmsContentObserverClass smsContentObserver = new SmsContentObserverClass();
    private InboxContentObserverClass inboxContentObserver = new InboxContentObserverClass();
    private SentContentObserverClass sentContentObserver = new SentContentObserverClass();
    private DraftContentObserverClass draftContentObserver = new DraftContentObserverClass();
    private OutboxContentObserverClass outboxContentObserver = new OutboxContentObserverClass();
    private FailedContentObserverClass failedContentObserver = new FailedContentObserverClass();
    private QueuedContentObserverClass queuedContentObserver = new QueuedContentObserverClass();

    private Cursor crSmsA = null;
    private Cursor crSmsB = null;
    private Cursor crSmsInboxA = null;
    private Cursor crSmsInboxB = null;
    private Cursor crSmsSentA = null;
    private Cursor crSmsSentB = null;
    private Cursor crSmsDraftA = null;
    private Cursor crSmsDraftB = null;
    private Cursor crSmsOutboxA = null;
    private Cursor crSmsOutboxB = null;
    private Cursor crSmsFailedA = null;
    private Cursor crSmsFailedB = null;
    private Cursor crSmsQueuedA = null;
    private Cursor crSmsQueuedB = null;

    private final int CR_SMS_A = 1;
    private final int CR_SMS_B = 2;
    private int currentCRSms = CR_SMS_A;
    private final int CR_SMS_INBOX_A = 1;
    private final int CR_SMS_INBOX_B = 2;
    private int currentCRSmsInbox = CR_SMS_INBOX_A;
    private final int CR_SMS_SENT_A = 1;
    private final int CR_SMS_SENT_B = 2;
    private int currentCRSmsSent = CR_SMS_SENT_A;
    private final int CR_SMS_DRAFT_A = 1;
    private final int CR_SMS_DRAFT_B = 2;
    private int currentCRSmsDraft = CR_SMS_DRAFT_A;
    private final int CR_SMS_OUTBOX_A = 1;
    private final int CR_SMS_OUTBOX_B = 2;
    private int currentCRSmsOutbox = CR_SMS_OUTBOX_A;
    private final int CR_SMS_FAILED_A = 1;
    private final int CR_SMS_FAILED_B = 2;
    private int currentCRSmsFailed = CR_SMS_FAILED_A;
    private final int CR_SMS_QUEUED_A = 1;
    private final int CR_SMS_QUEUED_B = 2;
    private int currentCRSmsQueued = CR_SMS_QUEUED_A;

    /**
     * Get the folder name (MAP representation) based on the
     * folder type value in SMS database
     */
    private String getMAPFolder(int type) {
        String folder = null;
        switch (type) {
        case 1:
            folder = "inbox";
            break;
        case 2:
            folder = "sent";
            break;
        case 3:
            folder = "draft";
            break;
        case 4:
        case 5:
        case 6:
            folder = "outbox";
            break;
        default:
            break;
        }
        return folder;
    }

    /**
     * Get the folder name based on the type in SMS ContentProvider
     */
    private String getFolder(int type) {
        String folder = null;
        switch (type) {
        case 1:
            folder = "inbox";
            break;
        case 2:
            folder = "sent";
            break;
        case 3:
            folder = "draft";
            break;
        case 4:
            folder = "outbox";
            break;
        case 5:
            folder = "failed";
            break;
        case 6:
            folder = "queued";
            break;
        default:
            break;
        }
        return folder;
    }

    /**
     * Gets the table type (as in Sms Content Provider) for the
     * given id
     */
    private int getMessageType(String id) {
        Cursor cr = mContext.getContentResolver().query(
                Uri.parse("content://sms/" + id),
                new String[] { "_id", "type" }, null, null, null);
        if (cr.moveToFirst()) {
            return cr.getInt(cr.getColumnIndex("type"));
        }
        return -1;
    }

    /**
     * Get the folder name (table name of Sms Content Provider)
     */
    private String getContainingFolder(String oldFolder, String id,
            String dateTime) {
        String newFolder = null;
        Cursor cr = mContext.getContentResolver().query(
                Uri.parse("content://sms/"),
                new String[] { "_id", "date", "type" }, " _id = " + id, null,
                null);
        if (cr.moveToFirst()) {
            return getFolder(cr.getInt(cr.getColumnIndex("type")));
        }
        return newFolder;
    }

    /**
     * This class listens for changes in Sms Content Provider
     * It acts, only when a new entry gets added to database
     */
    private class SmsContentObserverClass extends ContentObserver {

        public SmsContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            // Synchronize this?
            if (currentCRSms == CR_SMS_A) {
                currentItemCount = crSmsA.getCount();
                crSmsB.requery();
                newItemCount = crSmsB.getCount();
            } else {
                currentItemCount = crSmsB.getCount();
                crSmsA.requery();
                newItemCount = crSmsA.getCount();
            }

            Log.d(TAG, "SMS current " + currentItemCount + " new "
                    + newItemCount);

            if (newItemCount > currentItemCount) {
                crSmsA.moveToFirst();
                crSmsB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsA,
                        new String[] { "_id" }, crSmsB, new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSms == CR_SMS_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                            Log.d(TAG, " SMS ADDED TO INBOX ");
                            String body1 = crSmsA.getString(crSmsA
                                    .getColumnIndex("body"));
                            String id1 = crSmsA.getString(crSmsA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " ADDED SMS ID " + id1 + " BODY "
                                    + body1);
                            String folder = getMAPFolder(crSmsA.getInt(crSmsA
                                    .getColumnIndex("type")));
                            sendMnsEvent(NEW_MESSAGE, id1, "TELECOM/MSG/"
                                    + folder, null, "SMS_GSM");
                            if (folder.equalsIgnoreCase("sent")) {
                                sendMnsEvent(SENDING_SUCCESS, id1,
                                        "TELECOM/MSG/" + folder, null,
                                        "SMS_GSM");
                            }
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSms == CR_SMS_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                            Log.d(TAG, " SMS ADDED ");
                            String body1 = crSmsB.getString(crSmsB
                                    .getColumnIndex("body"));
                            String id1 = crSmsB.getString(crSmsB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " ADDED SMS ID " + id1 + " BODY "
                                    + body1);
                            String folder = getMAPFolder(crSmsB.getInt(crSmsB
                                    .getColumnIndex("type")));
                            sendMnsEvent(NEW_MESSAGE, id1, "TELECOM/MSG/"
                                    + folder, null, "SMS_GSM");
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSms == CR_SMS_A) {
                currentCRSms = CR_SMS_B;
            } else {
                currentCRSms = CR_SMS_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider's inbox table
     * It acts, only when a entry gets removed from the table
     */
    private class InboxContentObserverClass extends ContentObserver {

        public InboxContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (currentCRSmsInbox == CR_SMS_INBOX_A) {
                currentItemCount = crSmsInboxA.getCount();
                crSmsInboxB.requery();
                newItemCount = crSmsInboxB.getCount();
            } else {
                currentItemCount = crSmsInboxB.getCount();
                crSmsInboxA.requery();
                newItemCount = crSmsInboxA.getCount();
            }

            Log.d(TAG, "SMS INBOX current " + currentItemCount + " new "
                    + newItemCount);

            if (currentItemCount > newItemCount) {
                crSmsInboxA.moveToFirst();
                crSmsInboxB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsInboxA,
                        new String[] { "_id" }, crSmsInboxB,
                        new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                // while((CursorJointer.Result joinerResult = joiner.next()) !=
                // null)
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsInbox == CR_SMS_INBOX_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM INBOX ");
                            String body = crSmsInboxA.getString(crSmsInboxA
                                    .getColumnIndex("body"));
                            String id = crSmsInboxA.getString(crSmsInboxA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/INBOX", null, "SMS_GSM");
                            } else {
                                Log.d(TAG, "Shouldn't reach here as you cannot " +
                                                "move msg from Inbox to any other folder");
                            }
                        } else {
                            // TODO - The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsInbox == CR_SMS_INBOX_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM INBOX ");
                            String body = crSmsInboxB.getString(crSmsInboxB
                                    .getColumnIndex("body"));
                            String id = crSmsInboxB.getString(crSmsInboxB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/INBOX", null, "SMS_GSM");
                            } else {
                                Log.d(TAG,"Shouldn't reach here as you cannot " +
                                                "move msg from Inbox to any other folder");
                            }
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsInbox == CR_SMS_INBOX_A) {
                currentCRSmsInbox = CR_SMS_INBOX_B;
            } else {
                currentCRSmsInbox = CR_SMS_INBOX_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider's Sent table
     * It acts, only when a entry gets removed from the table
     */
    private class SentContentObserverClass extends ContentObserver {

        public SentContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (currentCRSmsSent == CR_SMS_SENT_A) {
                currentItemCount = crSmsSentA.getCount();
                crSmsSentB.requery();
                newItemCount = crSmsSentB.getCount();
            } else {
                currentItemCount = crSmsSentB.getCount();
                crSmsSentA.requery();
                newItemCount = crSmsSentA.getCount();
            }

            Log.d(TAG, "SMS SENT current " + currentItemCount + " new "
                    + newItemCount);

            if (currentItemCount > newItemCount) {
                crSmsSentA.moveToFirst();
                crSmsSentB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsSentA,
                        new String[] { "_id" }, crSmsSentB,
                        new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                // while((CursorJointer.Result joinerResult = joiner.next()) !=
                // null)
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsSent == CR_SMS_SENT_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM SENT ");
                            String body = crSmsSentA.getString(crSmsSentA
                                    .getColumnIndex("body"));
                            String id = crSmsSentA.getString(crSmsSentA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);

                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/SENT", null, "SMS_GSM");
                            } else {
                                Log.d(TAG,"Shouldn't reach here as you cannot " +
                                          "move msg from Sent to any other folder");
                            }
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsSent == CR_SMS_SENT_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM SENT ");
                            String body = crSmsSentB.getString(crSmsSentB
                                    .getColumnIndex("body"));
                            String id = crSmsSentB.getString(crSmsSentB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/SENT", null, "SMS_GSM");
                            } else {
                                Log.d(TAG, "Shouldn't reach here as " +
                                                "you cannot move msg from Sent to " +
                                                "any other folder");
                            }
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsSent == CR_SMS_SENT_A) {
                currentCRSmsSent = CR_SMS_SENT_B;
            } else {
                currentCRSmsSent = CR_SMS_SENT_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider's Draft table
     * It acts, only when a entry gets removed from the table
     */
    private class DraftContentObserverClass extends ContentObserver {

        public DraftContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (currentCRSmsDraft == CR_SMS_DRAFT_A) {
                currentItemCount = crSmsDraftA.getCount();
                crSmsDraftB.requery();
                newItemCount = crSmsDraftB.getCount();
            } else {
                currentItemCount = crSmsDraftB.getCount();
                crSmsDraftA.requery();
                newItemCount = crSmsDraftA.getCount();
            }

            Log.d(TAG, "SMS DRAFT current " + currentItemCount + " new "
                    + newItemCount);

            if (currentItemCount > newItemCount) {
                crSmsDraftA.moveToFirst();
                crSmsDraftB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsDraftA,
                        new String[] { "_id" }, crSmsDraftB,
                        new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsDraft == CR_SMS_DRAFT_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM DRAFT ");
                            String body = crSmsDraftA.getString(crSmsDraftA
                                    .getColumnIndex("body"));
                            String id = crSmsDraftA.getString(crSmsDraftA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);

                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/DRAFT", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                sendMnsEvent(MESSAGE_SHIFT, id, "TELECOM/MSG/"
                                        + newFolder, "TELECOM/MSG/DRAFT",
                                        "SMS_GSM");
                            }

                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsDraft == CR_SMS_DRAFT_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM DRAFT ");
                            String body = crSmsDraftB.getString(crSmsDraftB
                                    .getColumnIndex("body"));
                            String id = crSmsDraftB.getString(crSmsDraftB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/DRAFT", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                sendMnsEvent(MESSAGE_SHIFT, id, "TELECOM/MSG/"
                                        + newFolder, "TELECOM/MSG/DRAFT",
                                        "SMS_GSM");
                            }

                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsDraft == CR_SMS_DRAFT_A) {
                currentCRSmsDraft = CR_SMS_DRAFT_B;
            } else {
                currentCRSmsDraft = CR_SMS_DRAFT_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider's Outbox table
     * It acts only when a entry gets removed from the table
     */
    private class OutboxContentObserverClass extends ContentObserver {

        public OutboxContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (currentCRSmsOutbox == CR_SMS_OUTBOX_A) {
                currentItemCount = crSmsOutboxA.getCount();
                crSmsOutboxB.requery();
                newItemCount = crSmsOutboxB.getCount();
            } else {
                currentItemCount = crSmsOutboxB.getCount();
                crSmsOutboxA.requery();
                newItemCount = crSmsOutboxA.getCount();
            }

            Log.d(TAG, "SMS OUTBOX current " + currentItemCount + " new "
                    + newItemCount);

            if (currentItemCount > newItemCount) {
                crSmsOutboxA.moveToFirst();
                crSmsOutboxB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsOutboxA,
                        new String[] { "_id" }, crSmsOutboxB,
                        new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsOutbox == CR_SMS_OUTBOX_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM OUTBOX ");
                            String body = crSmsOutboxA.getString(crSmsOutboxA
                                    .getColumnIndex("body"));
                            String id = crSmsOutboxA.getString(crSmsOutboxA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/OUTBOX", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder
                                                .equalsIgnoreCase("outbox"))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    sendMnsEvent(MESSAGE_SHIFT, id,
                                            "TELECOM/MSG/" + newFolder,
                                            "TELECOM/MSG/OUTBOX", "SMS_GSM");
                                }
                            }

                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsOutbox == CR_SMS_OUTBOX_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM OUTBOX ");
                            String body = crSmsOutboxB.getString(crSmsOutboxB
                                    .getColumnIndex("body"));
                            String id = crSmsOutboxB.getString(crSmsOutboxB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/OUTBOX", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder
                                                .equalsIgnoreCase("outbox"))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    sendMnsEvent(MESSAGE_SHIFT, id,
                                            "TELECOM/MSG/" + newFolder,
                                            "TELECOM/MSG/OUTBOX", "SMS_GSM");
                                }
                            }

                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsOutbox == CR_SMS_OUTBOX_A) {
                currentCRSmsOutbox = CR_SMS_OUTBOX_B;
            } else {
                currentCRSmsOutbox = CR_SMS_OUTBOX_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider's Failed table
     * It acts only when a entry gets removed from the table
     */
    private class FailedContentObserverClass extends ContentObserver {

        public FailedContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (currentCRSmsFailed == CR_SMS_FAILED_A) {
                currentItemCount = crSmsFailedA.getCount();
                crSmsFailedB.requery();
                newItemCount = crSmsFailedB.getCount();
            } else {
                currentItemCount = crSmsFailedB.getCount();
                crSmsFailedA.requery();
                newItemCount = crSmsFailedA.getCount();
            }

            Log.d(TAG, "SMS FAILED current " + currentItemCount + " new "
                    + newItemCount);

            if (currentItemCount > newItemCount) {
                crSmsFailedA.moveToFirst();
                crSmsFailedB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsFailedA,
                        new String[] { "_id" }, crSmsFailedB,
                        new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsFailed == CR_SMS_FAILED_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM FAILED ");
                            String body = crSmsFailedA.getString(crSmsFailedA
                                    .getColumnIndex("body"));
                            String id = crSmsFailedA.getString(crSmsFailedA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/OUTBOX", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder
                                                .equalsIgnoreCase("outbox"))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    sendMnsEvent(MESSAGE_SHIFT, id,
                                            "TELECOM/MSG/" + newFolder,
                                            "TELECOM/MSG/OUTBOX", "SMS_GSM");
                                }
                            }

                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsFailed == CR_SMS_FAILED_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM FAILED ");
                            String body = crSmsFailedB.getString(crSmsFailedB
                                    .getColumnIndex("body"));
                            String id = crSmsFailedB.getString(crSmsFailedB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/OUTBOX", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder
                                                .equalsIgnoreCase("outbox"))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    sendMnsEvent(MESSAGE_SHIFT, id,
                                            "TELECOM/MSG/" + newFolder,
                                            "TELECOM/MSG/OUTBOX", "SMS_GSM");
                                }
                            }
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsFailed == CR_SMS_FAILED_A) {
                currentCRSmsFailed = CR_SMS_FAILED_B;
            } else {
                currentCRSmsFailed = CR_SMS_FAILED_A;
            }
        }
    }

    /**
     * This class listens for changes in Sms Content Provider's Queued table
     * It acts only when a entry gets removed from the table
     */
    private class QueuedContentObserverClass extends ContentObserver {

        public QueuedContentObserverClass() {
            super(null);
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            int currentItemCount = 0;
            int newItemCount = 0;
            if (currentCRSmsQueued == CR_SMS_QUEUED_A) {
                currentItemCount = crSmsQueuedA.getCount();
                crSmsQueuedB.requery();
                newItemCount = crSmsQueuedB.getCount();
            } else {
                currentItemCount = crSmsQueuedB.getCount();
                crSmsQueuedA.requery();
                newItemCount = crSmsQueuedA.getCount();
            }

            Log.d(TAG, "SMS QUEUED current " + currentItemCount + " new "
                    + newItemCount);

            if (currentItemCount > newItemCount) {
                crSmsQueuedA.moveToFirst();
                crSmsQueuedB.moveToFirst();

                CursorJoiner joiner = new CursorJoiner(crSmsQueuedA,
                        new String[] { "_id" }, crSmsQueuedB,
                        new String[] { "_id" });

                CursorJoiner.Result joinerResult;
                // while((CursorJointer.Result joinerResult = joiner.next()) !=
                // null)
                while (joiner.hasNext()) {
                    joinerResult = joiner.next();
                    switch (joinerResult) {
                    case LEFT:
                        // handle case where a row in cursor1 is unique
                        if (currentCRSmsQueued == CR_SMS_QUEUED_A) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM QUEUED ");
                            String body = crSmsQueuedA.getString(crSmsQueuedA
                                    .getColumnIndex("body"));
                            String id = crSmsQueuedA.getString(crSmsQueuedA
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/OUTBOX", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder
                                                .equalsIgnoreCase("outbox"))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    sendMnsEvent(MESSAGE_SHIFT, id,
                                            "TELECOM/MSG/" + newFolder,
                                            "TELECOM/MSG/OUTBOX", "SMS_GSM");
                                }
                            }
                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case RIGHT:
                        // handle case where a row in cursor2 is unique
                        if (currentCRSmsQueued == CR_SMS_QUEUED_B) {
                            // The new query doesn't have this row; implies it
                            // was deleted
                            Log.d(TAG, " SMS DELETED FROM QUEUED ");
                            String body = crSmsQueuedB.getString(crSmsQueuedB
                                    .getColumnIndex("body"));
                            String id = crSmsQueuedB.getString(crSmsQueuedB
                                    .getColumnIndex("_id"));
                            Log.d(TAG, " DELETED SMS ID " + id + " BODY "
                                    + body);
                            int msgType = getMessageType(id);
                            if (msgType == -1) {
                                sendMnsEvent(MESSAGE_DELETED, id,
                                        "TELECOM/MSG/OUTBOX", null, "SMS_GSM");
                            } else {
                                String newFolder = getMAPFolder(msgType);
                                if ((newFolder != null)
                                        && (!newFolder
                                                .equalsIgnoreCase("outbox"))) {
                                    // The message has moved on MAP virtual
                                    // folder representation.
                                    sendMnsEvent(MESSAGE_SHIFT, id,
                                            "TELECOM/MSG/" + newFolder,
                                            "TELECOM/MSG/OUTBOX", "SMS_GSM");
                                }
                            }

                        } else {
                            // The current(old) query doesn't have this row;
                            // implies it was added
                        }
                        break;
                    case BOTH:
                        // handle case where a row with the same key is in both
                        // cursors
                        break;
                    }
                }
            }
            if (currentCRSmsQueued == CR_SMS_QUEUED_A) {
                currentCRSmsQueued = CR_SMS_QUEUED_B;
            } else {
                currentCRSmsQueued = CR_SMS_QUEUED_A;
            }
        }
    }

    /**
     * Start MNS connection
     */
    public void start(BluetoothDevice mRemoteDevice) {
        /* check Bluetooth enable status */
        /*
         * normally it's impossible to reach here if BT is disabled. Just check
         * for safety
         */
        if (!mAdapter.isEnabled()) {
            Log.e(TAG, "Can't send event when Bluetooth is disabled ");
            return;
        }

        mDestination = mRemoteDevice;
        int channel = -1;
        // TODO Fix this exception below
        // int channel =
        // BluetoothMnsPreference.getInstance(mContext).getChannel(
        // mDestination, MNS_UUID16);

        if (channel != -1) {
            if (D) Log.d(TAG, "Get MNS channel " + channel + " from cache for "
                    + mDestination);
            mTimestamp = System.currentTimeMillis();
            mSessionHandler.obtainMessage(SDP_RESULT, channel, -1, mDestination)
                    .sendToTarget();
        } else {
            sendMnsSdp();
        }
    }

    /**
     * Stop the transfer
     */
    public void stop() {
        if (V)
            Log.v(TAG, "stop");
        if (mConnectThread != null) {
            try {
                mConnectThread.interrupt();
                if (V) Log.v(TAG, "waiting for connect thread to terminate");
                mConnectThread.join();
            } catch (InterruptedException e) {
                if (V) Log.v(TAG,
                        "Interrupted waiting for connect thread to join");
            }
            mConnectThread = null;
        }
        if (mSession != null) {
            if (V)
                Log.v(TAG, "Stop mSession");
            mSession.disconnect();
            mSession = null;
        }
        // TODO Do this somewhere else - Should the handler thread be gracefully closed.
    }

    /**
     * Connect the MNS Obex client to remote server
     */
    private void startObexSession() {

        if (V)
            Log.v(TAG, "Create Client session with transport "
                    + mTransport.toString());
        mSession = new BluetoothMnsObexSession(mContext, mTransport);
        mSession.connect();
    }

    /**
     * Check if local database contains remote device's info
     * Else start sdp query
     */
    private void sendMnsSdp() {
        if (V)
            Log.v(TAG, "Do Opush SDP request for address " + mDestination);

        mTimestamp = System.currentTimeMillis();

        int channel = -1;

        Method m;
        try {
            m = mDestination.getClass().getMethod("getServiceChannel",
                    new Class[] { ParcelUuid.class });
            channel = (Integer) m.invoke(mDestination, BluetoothUuid_ObexMns);
        } catch (SecurityException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        } catch (NoSuchMethodException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        } catch (IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // channel = mDestination.getServiceChannel(BluetoothUuid_ObexMns);

        if (channel != -1) {
            if (D)
                Log.d(TAG, "Get MNS channel " + channel + " from SDP for "
                        + mDestination);

            mSessionHandler
                    .obtainMessage(SDP_RESULT, channel, -1, mDestination)
                    .sendToTarget();
            return;

        } else {

            boolean result = false;
            if (V)
                Log.v(TAG, "Remote Service channel not in cache");

            Method m2;
            try {
                // m2 = mDestination.getClass().getMethod("fetchUuidsWithSdp",
                // (Class[]) null );
                // result = (Boolean) m2.invoke(mDestination);
                m2 = mDestination.getClass().getMethod("fetchUuidsWithSdp",
                        new Class[] {});
                result = (Boolean) m2.invoke(mDestination);

            } catch (SecurityException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (NoSuchMethodException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            } catch (IllegalArgumentException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            if (result == false) {
                // if (!mDestination.fetchUuidsWithSdp()) {
                Log.e(TAG, "Start SDP query failed");
            } else {
                // we expect framework send us Intent ACTION_UUID. otherwise we
                // will fail
                if (V)
                    Log.v(TAG, "Start new SDP, wait for result");
                // IntentFilter intentFilter = new
                // IntentFilter(BluetoothDevice.ACTION_UUID);
                IntentFilter intentFilter = new IntentFilter(
                        "android.bleutooth.device.action.UUID");
                mContext.registerReceiver(mReceiver, intentFilter);
                return;
            }
        }
        Message msg = mSessionHandler.obtainMessage(SDP_RESULT, channel, -1,
                mDestination);
        mSessionHandler.sendMessageDelayed(msg, 2000);
    }

    /**
     * Receives the response of SDP query
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            Log.d(TAG, " MNS BROADCAST RECV intent: " + intent.getAction());

            // if (intent.getAction().equals(BluetoothDevice.ACTION_UUID)) {
            if (intent.getAction().equals(
                    "android.bleutooth.device.action.UUID")) {
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (V)
                    Log.v(TAG, "ACTION_UUID for device " + device);
                if (device.equals(mDestination)) {
                    int channel = -1;
                    // Parcelable[] uuid =
                    // intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    Parcelable[] uuid = intent
                            .getParcelableArrayExtra("android.bluetooth.device.extra.UUID");
                    if (uuid != null) {
                        ParcelUuid[] uuids = new ParcelUuid[uuid.length];
                        for (int i = 0; i < uuid.length; i++) {
                            uuids[i] = (ParcelUuid) uuid[i];
                        }

                        boolean result = false;

                        // TODO Fix this error
                        result = true;

                        try {
                            Class c = Class
                                    .forName("android.bluetooth.BluetoothUuid");
                            Method m = c.getMethod("isUuidPresent",
                                    new Class[] { ParcelUuid[].class,
                                            ParcelUuid.class });

                            Boolean bool = false;
                            bool = (Boolean) m.invoke(c, uuids,
                                    BluetoothUuid_ObexMns);
                            result = bool.booleanValue();

                        } catch (ClassNotFoundException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (SecurityException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (NoSuchMethodException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (IllegalArgumentException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (IllegalAccessException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (InvocationTargetException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }

                        if (result) {
                            // if (BluetoothUuid.isUuidPresent(uuids,
                            // BluetoothUuid.ObexMns)) {
                            if (V)
                                Log.v(TAG, "SDP get MNS result for device "
                                        + device);

                            // channel = mDestination
                            // .getServiceChannel(BluetoothUuid_ObexMns);
                            Method m1;
                            try {
                                // m1 =
                                // mDestination.getClass().getMethod("getServiceChannel",
                                // new Class[]{ ParcelUuid.class });
                                // Integer chan = (Integer)
                                // m1.invoke(mDestination,
                                // BluetoothUuid_ObexMns);

                                m1 = device.getClass().getMethod(
                                        "getServiceChannel",
                                        new Class[] { ParcelUuid.class });
                                Integer chan = (Integer) m1.invoke(device,
                                        BluetoothUuid_ObexMns);

                                channel = chan.intValue();
                                Log.d(TAG, " MNS SERVER Channel no " + channel);
                                if (channel == -1) {
                                    channel = 2;
                                    Log.d(TAG, " MNS SERVER USE TEMP CHANNEL "
                                            + channel);
                                }
                            } catch (SecurityException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (NoSuchMethodException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (IllegalArgumentException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (IllegalAccessException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            } catch (InvocationTargetException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }
                    mSessionHandler.obtainMessage(SDP_RESULT, channel, -1,
                            mDestination).sendToTarget();
                }
            }
        }
    };

    private SocketConnectThread mConnectThread;

    /**
     * This thread is used to establish rfcomm connection to
     * remote device
     */
    private class SocketConnectThread extends Thread {
        private final String host;

        private final BluetoothDevice device;

        private final int channel;

        private boolean isConnected;

        private long timestamp;

        private BluetoothSocket btSocket = null;

        /* create a Rfcomm Socket */
        public SocketConnectThread(BluetoothDevice device, int channel) {
            super("Socket Connect Thread");
            this.device = device;
            this.host = null;
            this.channel = channel;
            isConnected = false;
        }

        public void interrupt() {
        }

        @Override
        public void run() {

            timestamp = System.currentTimeMillis();

            /* Use BluetoothSocket to connect */
            try {
                try {
                    // btSocket = device.createInsecureRfcommSocket(channel);
                    Method m = device.getClass().getMethod(
                            "createInsecureRfcommSocket",
                            new Class[] { int.class });
                    btSocket = (BluetoothSocket) m.invoke(device, channel);
                } catch (SecurityException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            } catch (Exception e1) {
                // TODO Auto-generated catch block
                Log.e(TAG, "Rfcomm socket create error");
                markConnectionFailed(btSocket);
                return;
            }

            try {
                btSocket.connect();
                if (V) Log.v(TAG, "Rfcomm socket connection attempt took "
                        + (System.currentTimeMillis() - timestamp) + " ms");
                BluetoothMnsRfcommTransport transport;
                transport = new BluetoothMnsRfcommTransport(btSocket);

                BluetoothMnsPreference.getInstance(mContext).setChannel(device,
                        MNS_UUID16, channel);
                BluetoothMnsPreference.getInstance(mContext).setName(device,
                        device.getName());

                if (V) Log.v(TAG, "Send transport message "
                        + transport.toString());

                mSessionHandler.obtainMessage(RFCOMM_CONNECTED, transport)
                        .sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Rfcomm socket connect exception ");
                BluetoothMnsPreference.getInstance(mContext).removeChannel(
                        device, MNS_UUID16);
                markConnectionFailed(btSocket);
                return;
            }
        }

        /**
         * RFCOMM connection failed
         */
        private void markConnectionFailed(Socket s) {
            try {
                s.close();
            } catch (IOException e) {
                Log.e(TAG, "TCP socket close error");
            }
            mSessionHandler.obtainMessage(RFCOMM_ERROR).sendToTarget();
        }

        /**
         * RFCOMM connection failed
         */
        private void markConnectionFailed(BluetoothSocket s) {
            try {
                s.close();
            } catch (IOException e) {
                if (V) Log.e(TAG, "Error when close socket");
            }
            mSessionHandler.obtainMessage(RFCOMM_ERROR).sendToTarget();
            return;
        }
    }
}
