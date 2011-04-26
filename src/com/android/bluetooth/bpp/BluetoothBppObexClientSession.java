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

import com.android.bluetooth.opp.BluetoothOppSendFileInfo;
import com.android.bluetooth.opp.Constants;
import com.android.bluetooth.opp.BluetoothShare;
import com.android.bluetooth.opp.BluetoothOppShareInfo;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.ResponseCodes;
import javax.obex.Authenticator;

import android.content.Context;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.os.Process;
import android.content.Intent;
import android.content.IntentFilter;
import android.app.Activity;

public class BluetoothBppObexClientSession{

    private static final String TAG = "BluetoothBppObexClientSession";
    private static final boolean D = BluetoothBppConstant.DEBUG;
    private static final boolean V = BluetoothBppConstant.VERBOSE;

    private ClientThread mThread;

    private ObexTransport mTransport;

    private Context mContext;

    private volatile boolean mInterrupted;

    private volatile boolean mWaitingForRemote;

    private BluetoothBppAuthenticator mAuth = null;

    public static IOException mJobStatus = null;

    public enum SoapReqSeq {
        STANDBY,
        PROCESSING,
        GETATTRIBUTE,
        CREATJOB,
        GETEVENT,
        SENDDOCUMENT,
        CANCEL,
        CANCELLING,
        CANCELLED,
        DONE
    }

    public volatile static SoapReqSeq mSoapProcess = SoapReqSeq.GETATTRIBUTE;

    static Handler mCallback;

    static final int MSG_SESSION_COMPLETE           = 1;
    static final int MSG_SESSION_EVENT_COMPLETE     = 2;
    static final int MSG_SHARE_COMPLETE             = 3;
    static final int MSG_SESSION_STOP               = 4;
    static final int MSG_SESSION_ERROR              = 5;
    static final int MSG_SHARE_INTERRUPTED          = 6;
    static final int MSG_CONNECT_TIMEOUT            = 7;
    static final int MSG_GETEVENT_RESPONSE          = 8;

    public BluetoothBppObexClientSession(Context context, ObexTransport transport) {
        if (transport == null) {
            throw new NullPointerException("transport is null");
        }

        if (D) Log.d(TAG, "context: "+ context + ", transport: " + transport);
        mContext = context;
        mTransport = transport;
    }

    public void start(Handler handler, Authenticator auth) {
        if (D) Log.d(TAG, "Start!");
        mCallback = handler;
        mAuth = (BluetoothBppAuthenticator) auth;
        mThread = new ClientThread(mContext, mTransport);
        mThread.start();
    }

    public void stop() {
        if (D) Log.d(TAG, "Stop!");
        if (mThread != null) {
            mInterrupted = true;
            try {
                mThread.interrupt();
                if (V) Log.v(TAG, "waiting for thread to terminate");
                mThread.join();
                mThread = null;
            } catch (InterruptedException e) {
                if (V) Log.v(TAG, "Interrupted waiting for thread to join");
            }
        }
        mCallback = null;
    }


    public void addShare(BluetoothOppShareInfo share) {
        mThread.addShare(share);
    }

    private class ClientThread extends Thread {

        private static final int sSleepTime = 500;

        private Context mContext1;

        private BluetoothOppShareInfo mInfo;

        private volatile boolean waitingForShare;

        private ObexTransport mTransport1;

        private int mChannel;

        private ClientSession mCs;

        private WakeLock wakeLock;

        private BluetoothOppSendFileInfo mFileInfo = null;

        private boolean mConnected = false;

        public ClientThread(Context context, ObexTransport transport) {

            super("BtBpp ClientThread");
            mContext1 = context;
            mTransport1 = transport;
            waitingForShare = false;
            mWaitingForRemote = false;

            PowerManager pm = (PowerManager)mContext1.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }

        public void addShare(BluetoothOppShareInfo info) {
            mInfo = info;
            mFileInfo = processShareInfo();
            waitingForShare = false;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            if (V) Log.v(TAG, "acquire partial WakeLock");
            wakeLock.acquire();
            mSoapProcess = SoapReqSeq.GETATTRIBUTE;

            try {
                Thread.sleep(100);
            } catch (InterruptedException e1) {
                if (V) Log.v(TAG, "Client thread was interrupted (1), exiting");
                mInterrupted = true;
            }
            if (!mInterrupted) {
                connect();
            }

            while (!mInterrupted) {
                if(mSoapProcess == SoapReqSeq.GETATTRIBUTE){
                    mSoapProcess = SoapReqSeq.PROCESSING;
                    // GetPrinterAttributes SOAP Request
                    if (D) Log.d(TAG, "SOAP Request: GetPrinterAttributes");
                    int status = sendSoapRequest(BluetoothBppSoap.SOAP_REQ_GET_PR_ATTR);

                    if(status != BluetoothBppConstant.STATUS_SUCCESS){
                        if (D) Log.d(TAG, "SOAP Request fail : " + status );
                        Message msg = Message.obtain(mCallback);
                        msg.what = BluetoothBppObexClientSession.MSG_SESSION_ERROR;
                        mInfo.mStatus = BluetoothShare.STATUS_CANCELED;
                        msg.obj = mInfo;
                        msg.sendToTarget();
                    }else{
                        Intent in = new Intent(mContext1, BluetoothBppPrintPrefActivity.class);
                        in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext1.startActivity(in);

                        BluetoothBppActivity.mOPPstop = false;
                        ((Activity) BluetoothBppActivity.mContext).finish();
                    }
                } else if(mSoapProcess == SoapReqSeq.CREATJOB){
                    mSoapProcess = SoapReqSeq.PROCESSING;
                    // CreateJob SOAP Request
                    if (D) Log.d(TAG, "SOAP Request: CreateJob");
                    int status = sendSoapRequest(BluetoothBppSoap.SOAP_REQ_CREATE_JOB);

                    if(status != BluetoothBppConstant.STATUS_SUCCESS){
                        if (D) Log.d(TAG, "SOAP Request fail : " + status );
                        Message msg = Message.obtain(mCallback);
                        msg.what = BluetoothBppObexClientSession.MSG_SESSION_ERROR;
                        mInfo.mStatus = BluetoothShare.STATUS_CANCELED;
                        msg.obj = mInfo;
                        msg.sendToTarget();
                    }
                } else if((mSoapProcess == SoapReqSeq.SENDDOCUMENT) && !waitingForShare ){
                    doSend();
                } else if(mSoapProcess == SoapReqSeq.CANCEL){
                    mSoapProcess = SoapReqSeq.CANCELLING;
                    if(BluetoothBppEvent.mConnected == true){
                        if (D) Log.d(TAG, "SOAP Request: CancelJob");
                        int status = sendSoapRequest(BluetoothBppSoap.SOAP_REQ_CANCEL_JOB);
                        if(status != BluetoothBppConstant.STATUS_SUCCESS){
                            if (D) Log.d(TAG, "SOAP Request fail : " + status );
                            Message msg = Message.obtain(mCallback);
                            msg.what = BluetoothBppObexClientSession.MSG_SESSION_ERROR;
                            mInfo.mStatus = BluetoothShare.STATUS_CANCELED;
                            msg.obj = mInfo;
                            msg.sendToTarget();
                        }
                    } else {
                        mSoapProcess = SoapReqSeq.CANCELLED;

                        Message msg = Message.obtain(mCallback);
                        msg.what = BluetoothBppObexClientSession.MSG_SESSION_ERROR;
                        mInfo.mStatus = BluetoothShare.STATUS_CANCELED;
                        msg.obj = mInfo;
                        msg.sendToTarget();
                    }

                } else {
                    try {
                        if (D) Log.d(TAG, "Client thread waiting for next share, sleep for "
                            + sSleepTime);
                        Thread.sleep(sSleepTime);
                    } catch (InterruptedException e) {

                    }
                }
            }

            disconnect();

            if (wakeLock.isHeld()) {
                if (V) Log.v(TAG, "release partial WakeLock");
                wakeLock.release();
            }
            Message msg = Message.obtain(mCallback);
            msg.what = BluetoothBppObexClientSession.MSG_SESSION_COMPLETE;
            msg.obj = mInfo;
            msg.sendToTarget();
        }

        private void disconnect() {
            try {
                if (mCs != null) {
                    mCs.disconnect(null);
                }
                mCs = null;
                if (D) Log.d(TAG, "OBEX session disconnected");
            } catch (IOException e) {
                Log.w(TAG, "OBEX session disconnect error" + e);
                mJobStatus = e;
            }
            try {
                if (mCs != null) {
                    if (D) Log.d(TAG, "OBEX session close mCs");
                    mCs.close();
                    if (D) Log.d(TAG, "OBEX session closed");
                }
            } catch (IOException e) {
                Log.w(TAG, "OBEX session close error" + e);
                mJobStatus = e;
            }
            if (mTransport1 != null) {
                try {
                    mTransport1.close();
                } catch (IOException e) {
                    Log.e(TAG, "mTransport.close error");
                    mJobStatus = e;
                }
            }
        }

        private void connect() {
            if (D) Log.d(TAG, "Create ClientSession with transport " + mTransport1.toString());
            try {
                mCs = new ClientSession(mTransport1);
                mCs.setAuthenticator(mAuth);
                mConnected = true;
            } catch (IOException e1) {
                Log.e(TAG, "OBEX session create error");
            }
            if (mConnected) {
                mConnected = false;
                HeaderSet hs = new HeaderSet();

                // OBEX Authentication Setup
                if(BluetoothBppSetting.bpp_auth == true){
                    if (D) Log.d(TAG, "OBEX Authentication is initiated");
                    try {
                        hs.createAuthenticationChallenge(null, false, true);
                    } catch (IOException e1) {
                        // TODO Auto-generated catch block
                        e1.printStackTrace();
                    }
                }

                hs.setHeader(HeaderSet.TARGET, BluetoothBppConstant.DPS_Target_UUID);

                synchronized (this) {
                    mWaitingForRemote = true;
                }
                try {
                    HeaderSet retHs = mCs.connect(hs);
                    if (D) Log.d(TAG, "OBEX session created");
                    mConnected = true;
                } catch (IOException e) {
                    Log.e(TAG, "OBEX session connect error");
                    mJobStatus = e;
                }
            }
            synchronized (this) {
                mWaitingForRemote = false;
            }
        }

        private void doSend() {
            int status = BluetoothShare.STATUS_SUCCESS;

            if (D) Log.d(TAG, "OBEX: Sending data ...");
            /* connection is established too fast to get first mInfo */
            while (mFileInfo == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    status = BluetoothShare.STATUS_CANCELED;
                }
            }

            if (!mConnected) {
                // Obex connection error
                status = BluetoothShare.STATUS_CONNECTION_ERROR;
                if (D) Log.d(TAG, "no OBEX Connection");
            }
            if (status == BluetoothBppConstant.STATUS_SUCCESS) {
                // do real send
                if (mFileInfo.mFileName != null) {
                    // Send Document
                    status = sendFile(mFileInfo);
                } else {
                    // this is invalid request
                    status = mFileInfo.mStatus;
                }
                waitingForShare = true;
            } else {
                Constants.updateShareStatus(mContext1, mInfo.mId, status);
            }

            if (status == BluetoothBppConstant.STATUS_SUCCESS) {
                Message msg = Message.obtain(mCallback);
                msg.what = BluetoothBppObexClientSession.MSG_SHARE_COMPLETE;
                msg.obj = mInfo;
                msg.sendToTarget();
            } else {
                Message msg = Message.obtain(mCallback);
                msg.what = BluetoothBppObexClientSession.MSG_SESSION_ERROR;
                mInfo.mStatus = status;
                msg.obj = mInfo;
                msg.sendToTarget();
            }
        }

        /*
                * Validate this ShareInfo
                */

        private BluetoothOppSendFileInfo processShareInfo() {
            if (V) Log.v(TAG, "Client thread processShareInfo() " + mInfo.mId);

            BluetoothOppSendFileInfo fileInfo = BluetoothOppSendFileInfo.generateFileInfo(
                        mContext1, mInfo.mUri, mInfo.mMimetype, mInfo.mDestination);
            if (fileInfo.mFileName == null || fileInfo.mLength == 0) {
                if (V) Log.v(TAG, "BluetoothOppSendFileInfo get invalid file");
                Constants.updateShareStatus(mContext1, mInfo.mId, fileInfo.mStatus);

            } else {
                if (V) {
                    Log.v(TAG, "Generate BluetoothOppSendFileInfo:");
                    Log.v(TAG, "filename  :" + fileInfo.mFileName);
                    Log.v(TAG, "length    :" + fileInfo.mLength);
                    Log.v(TAG, "mimetype  :" + fileInfo.mMimetype);
                }

                ContentValues updateValues = new ContentValues();
                Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);

                updateValues.put(BluetoothShare.FILENAME_HINT, fileInfo.mFileName);
                updateValues.put(BluetoothShare.TOTAL_BYTES, fileInfo.mLength);
                updateValues.put(BluetoothShare.MIMETYPE, fileInfo.mMimetype);
                mContext1.getContentResolver().update(contentUri, updateValues, null, null);
            }
            return fileInfo;
        }

        private synchronized int sendSoapRequest(String soapReq){
            boolean error = false;
            int responseCode = -1;
            HeaderSet request;
            int status = BluetoothBppConstant.STATUS_SUCCESS;
            int responseLength = 0;

            ClientOperation getOperation = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;

            request = new HeaderSet();
            request.setHeader(HeaderSet.TYPE, BluetoothBppConstant.MIME_TYPE_SOAP);

            try {
                // it sends GET command with current existing Header now..
                getOperation = (ClientOperation)mCs.get(request);
            } catch (IOException e) {
                Log.e(TAG, "Error when get HeaderSet ");
                error = true;
            }
            synchronized (this) {
                mWaitingForRemote = false;
            }

            if (!error) {
                try {
                    outputStream = getOperation.openOutputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error when openOutputStream");
                    error = true;
                }
            }

            try {
                synchronized (this) {
                    mWaitingForRemote = true;
                }

                if (!error) {
                    int position = 0;
                    int offset = 0;
                    int readLength = 0;
                    int remainedData = 0;
                    long timestamp = 0;
                    int maxBuffSize = getOperation.getMaxPacketSize();
                    byte[] readBuff = null;
                    String soapMsg = null;
                    int soapRequestSize = 0;

                    // Building SOAP Message based on SOAP Request
                    soapMsg = BluetoothBppSoap.Builder(soapReq);
                    soapRequestSize = soapMsg.length();

                    if (!mInterrupted && (position != soapRequestSize)) {
                        synchronized (this) {
                            mWaitingForRemote = true;
                        }

                        if (V) timestamp = System.currentTimeMillis();

                        // Sending Soap Request Data
                        while(position != soapRequestSize){
                            // Check if the data size is bigger than max packet size.
                            remainedData = soapRequestSize - position;
                            readLength = (maxBuffSize > remainedData)? remainedData : maxBuffSize;

                            if (V) Log.v(TAG, "Soap Request is now being sent...");
                            outputStream.write(soapMsg.getBytes(), position, readLength);

                            //Wait for response code
                            responseCode = getOperation.getResponseCode();

                            if (V) Log.v(TAG, "Rx - OBEX Response: " + ((responseCode
                                == ResponseCodes.OBEX_HTTP_CONTINUE)? "Continue"
                                    :(responseCode == ResponseCodes.OBEX_HTTP_OK)?
                                    "SUCCESS": responseCode ));
                            if (responseCode != ResponseCodes.OBEX_HTTP_CONTINUE
                                && responseCode != ResponseCodes.OBEX_HTTP_OK) {

                                outputStream.close();
                                getOperation.close();

                                status = BluetoothBppConstant.STATUS_CANCELED;
                                return status;
                            } else {
                                /*In case that a remote send a length for SOAP response right away
                                                            after SOAP request.. */
                                if((getOperation.getLength()!= -1) && (readBuff == null)){
                                    if (V) Log.v(TAG, "Get OBEX Length - "
                                                                    + getOperation.getLength());
                                    responseLength = (int)getOperation.getLength();
                                    readBuff = new byte[responseLength];
                                }

                                position += readLength;
                                if (V) {
                                    Log.v(TAG, "Soap Request is sent...\r\n" +
                                            " - Total Size : " + soapRequestSize + "bytes" +
                                            "\r\n - Sent Size  : " + position + "bytes" +
                                            "\r\n - Taken Time : " + (System.currentTimeMillis()
                                                        - timestamp) + " ms");
                                }
                            }
                        }
                        // Sending data completely !!
                        outputStream.close();

                        synchronized (this) {
                            mWaitingForRemote = false;
                        }

                        // Prepare to get input data
                        inputStream = getOperation.openInputStream();

                        // Check response code from the remote
                        while( (responseCode = getOperation.getResponseCode())
                            == ResponseCodes.OBEX_HTTP_CONTINUE) {
                            if (V) Log.v(TAG, "Rx - OBEX Response: Continue");

                            if((getOperation.getLength()!= -1) && (readBuff == null)){
                                if (V) Log.v(TAG, "Get OBEX Length - " + getOperation.getLength());
                                responseLength = (int)getOperation.getLength();
                                readBuff = new byte[responseLength];
                            }

                            if(readBuff != null){
                                offset += inputStream.read(readBuff, offset,
                                                            (responseLength - offset));
                                if (V) Log.v(TAG, "OBEX Data Read: " + offset + "/"
                                                            + responseLength + "bytes");
                            }
                        }

                        if(responseCode == ResponseCodes.OBEX_HTTP_OK){
                            if (V) Log.v(TAG, "OBEX GET: SUCCESS");
                            if (V) Log.v(TAG, "Get OBEX Length - " + getOperation.getLength());

                            if(responseLength == 0){
                                if((getOperation.getLength()!= -1) && (readBuff == null)){
                                    if (V) Log.v(TAG, "Get OBEX Length - "
                                                            + getOperation.getLength());
                                    responseLength = (int)getOperation.getLength();
                                    readBuff = new byte[responseLength];
                                }
                            }

                            if(readBuff != null){
                                if(offset != responseLength){
                                    offset += inputStream.read(readBuff, offset,
                                        (responseLength - offset));
                                    if (V) Log.v(TAG, "OBEX Data Read: " + offset
                                        + "/" + responseLength + "bytes");
                                }

                                if(offset != responseLength){
                                    if (V) Log.v(TAG, "OBEX Data Read: Fail!! - missing "
                                        + (responseLength - offset) + "bytes");
                                    status = BluetoothBppConstant.STATUS_OBEX_DATA_ERROR;
                                    return status;
                                }

                                String soapRsp = new String(readBuff);
                                if (V) Log.v(TAG, "Soap Response Message:\r\n" + soapRsp);
                                BluetoothBppSoap.Parser(soapReq, soapRsp);
                            }
                        }
                        else
                        {
                            if (V) Log.v(TAG, "OBEX GET: FAIL - " + responseCode );
                            switch(responseCode)
                            {
                                case ResponseCodes.OBEX_HTTP_FORBIDDEN:
                                case ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE:
                                    status = BluetoothBppConstant.STATUS_FORBIDDEN;
                                    break;
                                case ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE:
                                    status = BluetoothBppConstant.STATUS_NOT_ACCEPTABLE;
                                    break;
                                default:
                                    status = BluetoothBppConstant.STATUS_CANCELED;
                                    break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (V) Log.v(TAG, " IOException e) : " + e);
                mJobStatus = e;
            } catch (NullPointerException e) {
                if (V) Log.v(TAG, " NullPointerException : " + e);
            } catch (IndexOutOfBoundsException e) {
                if (V) Log.v(TAG, " IndexOutOfBoundsException : " + e);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        if (V) Log.v(TAG, "inputStream.close IOException : " + e);
                    }
                }
                if (getOperation != null) {
                    try {
                        getOperation.close();
                    } catch (IOException e) {
                        if (V) Log.v(TAG, "getOperation.close IOException : " + e);
                    }
                }
            }
            return status;
        }

        private int sendFile(BluetoothOppSendFileInfo fileInfo) {
            boolean error = false;
            int responseCode = -1;
            int status = BluetoothBppConstant.STATUS_SUCCESS;
            Uri contentUri = Uri.parse(BluetoothShare.CONTENT_URI + "/" + mInfo.mId);
            ContentValues updateValues;
            HeaderSet request;

            if(BluetoothBppSoap.bppJobId == null){
                status = BluetoothBppConstant.STATUS_OBEX_DATA_ERROR;
                if (V) Log.v(TAG, "BPP Error: Missing JobId");
                return status;
            }

            if (V) Log.v(TAG, "sendFile - File name: " + fileInfo.mFileName +
                                "\r\n\t Mime Type: " + fileInfo.mMimetype +
                                "\r\n\t Size: " + fileInfo.mLength );

            BluetoothBppTransfer.mFileSending = true;
            BluetoothBppStatusActivity.mTrans_Progress.setMax((int)fileInfo.mLength);
            request = new HeaderSet();
            request.setHeader(HeaderSet.NAME, fileInfo.mFileName );
            String newMimeType;
            if ((newMimeType = BluetoothBppTransfer.checkUnknownMimetype(fileInfo.mMimetype,
                                                                fileInfo.mFileName)) != null){
                request.setHeader(HeaderSet.TYPE, newMimeType );
                if(V) Log.v(TAG, "set new MIME Type: " + newMimeType);

            } else {
                request.setHeader(HeaderSet.TYPE, fileInfo.mMimetype );
            }

            byte appParam[] = new byte[6];
            appParam[0] = BluetoothBppConstant.OBEX_HDR_APP_PARAM_JOBID; // Tag
            appParam[1] = 4; // Length
            appParam[2] = BluetoothBppSoap.bppJobId[3];
            appParam[3] = BluetoothBppSoap.bppJobId[2];
            appParam[4] = BluetoothBppSoap.bppJobId[1];
            appParam[5] = BluetoothBppSoap.bppJobId[0];
            request.setHeader(HeaderSet.APPLICATION_PARAMETER, appParam);

            applyRemoteDeviceQuirks(request, fileInfo);

            Constants.updateShareStatus(mContext1, mInfo.mId, BluetoothShare.STATUS_RUNNING);

            request.setHeader(HeaderSet.LENGTH, fileInfo.mLength);
            ClientOperation putOperation = null;
            OutputStream outputStream = null;
            InputStream inputStream = null;
            try {
                synchronized (this) {
                    mWaitingForRemote = true;
                }
                try {
                    if (V) Log.v(TAG, "put headerset for " + fileInfo.mFileName);
                    putOperation = (ClientOperation)mCs.put(request);
                } catch (IOException e) {
                    status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
                    Constants.updateShareStatus(mContext1, mInfo.mId, status);

                    Log.e(TAG, "Error when put HeaderSet ");
                    error = true;
                }
                synchronized (this) {
                    mWaitingForRemote = false;
                }

                if (!error) {
                    try {
                        if (V) Log.v(TAG, "openOutputStream " + fileInfo.mFileName);
                        outputStream = putOperation.openOutputStream();
                        inputStream = putOperation.openInputStream();
                    } catch (IOException e) {
                        status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
                        Constants.updateShareStatus(mContext1, mInfo.mId, status);
                        Log.e(TAG, "Error when openOutputStream");
                        error = true;
                    }
                }

                if (!error) {
                    updateValues = new ContentValues();
                    updateValues.put(BluetoothShare.CURRENT_BYTES, 0);
                    updateValues.put(BluetoothShare.STATUS, BluetoothShare.STATUS_RUNNING);
                    mContext1.getContentResolver().update(contentUri, updateValues, null, null);
                }

                if (!error) {
                    int position = 0;
                    int readLength = 0;
                    boolean okToProceed = false;
                    long timestamp = 0;
                    int outputBufferSize = putOperation.getMaxPacketSize();
                    byte[] buffer = new byte[outputBufferSize];
                    BufferedInputStream a = new BufferedInputStream(fileInfo.mInputStream, 0x4000);

                    if (!mInterrupted && (position != fileInfo.mLength )) {
                        readLength = a.read(buffer, 0, outputBufferSize);

                        synchronized (this) {
                            mWaitingForRemote = true;
                        }
                        // first packet will block here
                        outputStream.write(buffer, 0, readLength);
                        position += readLength;
                        BluetoothBppStatusActivity.updateProgress(position, 0);

                        if (position != fileInfo.mLength) {
                            synchronized (this) {
                                mWaitingForRemote = false;
                            }
                        } else {
                            // if file length is smaller than buffer size, only one packet
                            // so block point is here
                            outputStream.close();
                            synchronized (this) {
                                mWaitingForRemote = false;
                            }
                        }
                        // check remote accept or reject
                        responseCode = putOperation.getResponseCode();

                        if (responseCode == ResponseCodes.OBEX_HTTP_CONTINUE
                            || responseCode == ResponseCodes.OBEX_HTTP_OK) {
                            if (V) Log.v(TAG, "Remote accept");
                            okToProceed = true;
                            updateValues = new ContentValues();
                            updateValues.put(BluetoothShare.CURRENT_BYTES, position);
                            mContext1.getContentResolver().update(contentUri, updateValues, null,
                                null);
                        } else {
                            Log.i(TAG, "Remote reject, Response code is " + responseCode);
                        }
                    }

                    while (!mInterrupted && okToProceed && (position != fileInfo.mLength)
                            && (mSoapProcess != SoapReqSeq.CANCEL)) {
                        {
                            if (V) timestamp = System.currentTimeMillis();

                            readLength = a.read(buffer, 0, outputBufferSize);
                            outputStream.write(buffer, 0, readLength);

                            // check remote abort
                            responseCode = putOperation.getResponseCode();
                            if (V) Log.v(TAG, "Response code is " + responseCode);
                            if (responseCode != ResponseCodes.OBEX_HTTP_CONTINUE
                                    && responseCode != ResponseCodes.OBEX_HTTP_OK) {
                                // abort happens
                                okToProceed = false;
                            } else {
                                position += readLength;
                                BluetoothBppStatusActivity.updateProgress(position, 0);

                                if (V) {
                                    Log.v(TAG, "Sending file position = " + position
                                        + " readLength " + readLength + " bytes took "
                                        + (System.currentTimeMillis() - timestamp) + " ms");
                                }
                                updateValues = new ContentValues();
                                updateValues.put(BluetoothShare.CURRENT_BYTES, position);
                                mContext1.getContentResolver().update(contentUri, updateValues,
                                        null, null);
                            }
                        }
                    }
                    if(mSoapProcess == SoapReqSeq.CANCEL){
                        mSoapProcess = SoapReqSeq.CANCELLING;
                        error = true;
                        status = BluetoothShare.STATUS_CANCELED;

                        putOperation.abort();
                        // Send CancelJob Request.
                        sendSoapRequest(BluetoothBppSoap.SOAP_REQ_CANCEL_JOB);

                        Log.i(TAG, "SendFile interrupted when send out file " + fileInfo.mFileName
                                + " at " + position + " of " + fileInfo.mLength);
                    } else if (responseCode == ResponseCodes.OBEX_HTTP_FORBIDDEN
                            || responseCode == ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE) {
                        Log.i(TAG, "Remote reject file " + fileInfo.mFileName + " length "
                                + fileInfo.mLength);
                        status = BluetoothShare.STATUS_FORBIDDEN;
                    } else if (responseCode == ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE) {
                        Log.i(TAG, "Remote reject file type " + fileInfo.mMimetype);
                        status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                    } else if (!mInterrupted && position == fileInfo.mLength) {
                        Log.i(TAG, "SendFile finished send out file " + fileInfo.mFileName
                                + " length " + fileInfo.mLength);
                        outputStream.close();
                    } else {
                        error = true;
                        status = BluetoothShare.STATUS_CANCELED;
                        putOperation.abort();
                        /* interrupted */
                        Log.i(TAG, "SendFile interrupted when send out file " + fileInfo.mFileName
                                + " at " + position + " of " + fileInfo.mLength);
                    }
                }
            } catch (IOException e) {

            } catch (NullPointerException e) {

            } catch (IndexOutOfBoundsException e) {

            } finally {
                try {
                    fileInfo.mInputStream.close();
                    if (!error) {
                        responseCode = putOperation.getResponseCode();
                        if (responseCode != -1) {
                            if (V) Log.v(TAG, "Get response code " + responseCode);
                            if (responseCode != ResponseCodes.OBEX_HTTP_OK) {
                                Log.i(TAG, "Response error code is " + responseCode);
                                status = BluetoothShare.STATUS_UNHANDLED_OBEX_CODE;
                                if (responseCode == ResponseCodes.OBEX_HTTP_UNSUPPORTED_TYPE) {
                                    status = BluetoothShare.STATUS_NOT_ACCEPTABLE;
                                }
                                if (responseCode == ResponseCodes.OBEX_HTTP_FORBIDDEN
                                        || responseCode == ResponseCodes.OBEX_HTTP_NOT_ACCEPTABLE) {
                                    status = BluetoothShare.STATUS_FORBIDDEN;
                                }
                            }
                        } else {
                            // responseCode is -1, which means connection error
                            status = BluetoothShare.STATUS_CONNECTION_ERROR;
                        }
                    }
                    Constants.updateShareStatus(mContext1, mInfo.mId, status);

                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (putOperation != null) {
                        putOperation.close();
                    }
                } catch (IOException e) {
                    Log.e(TAG, "Error when closing stream after send");
                }
            }
            return status;
        }

        private void handleSendException(String exception) {
            Log.e(TAG, "Error when sending file: " + exception);
            int status = BluetoothShare.STATUS_OBEX_DATA_ERROR;
            Constants.updateShareStatus(mContext1, mInfo.mId, status);
            mCallback.removeMessages(BluetoothBppObexClientSession.MSG_CONNECT_TIMEOUT);
        }

        @Override
        public void interrupt() {
            super.interrupt();
            if (mWaitingForRemote) {
                if (V) Log.v(TAG, "Interrupted when waitingForRemote");
                try {
                    mTransport1.close();
                } catch (IOException e) {
                    Log.e(TAG, "mTransport.close error");
                }

                Message msg = Message.obtain(mCallback);
                msg.what = BluetoothBppObexClientSession.MSG_SHARE_INTERRUPTED;
                if (mInfo != null) {
                    msg.obj = mInfo;
                }
                msg.sendToTarget();
            }
        }
    }

    public static void applyRemoteDeviceQuirks(HeaderSet request,
                                                     BluetoothOppSendFileInfo info) {
        String address = info.mDestAddr;
        if (address == null) {
            return;
        }
        if (address.startsWith("00:04:48")) {
            // Poloroid Pogo
            // Rejects filenames with more than one '.'. Rename to '_'.
            // for example: 'a.b.jpg' -> 'a_b.jpg'
            //      'abc.jpg' NOT CHANGED
            String filename = info.mFileName;

            char[] c = filename.toCharArray();
            boolean firstDot = true;
            boolean modified = false;
            for (int i = c.length - 1; i >= 0; i--) {
                if (c[i] == '.') {
                    if (!firstDot) {
                        modified = true;
                        c[i] = '_';
                    }
                    firstDot = false;
                }
            }
            if (modified) {
                String newFilename = new String(c);
                request.setHeader(HeaderSet.NAME, newFilename);
                Log.i(TAG, "Sending file \"" + filename + "\" as \"" + newFilename +
                        "\" to workaround Poloroid filename quirk");
            }
        }
    }

    public void unblock() {
        // Not used for client case
    }
}
