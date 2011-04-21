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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.ClientSession.eventParser;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.ResponseCodes;
import javax.obex.PrivateInputStream;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.PowerManager.WakeLock;
import android.util.Log;

public class BluetoothBppEvent {
    private static final String TAG = "BluetoothBppEvent";
    private static final boolean D = BluetoothBppConstant.DEBUG;
    private static final boolean V = BluetoothBppConstant.VERBOSE;
    private ClientThread mThread;
    private ObexTransport mTransport;
    private Context mContext;
    private volatile boolean mInterrupted;
    private volatile boolean mWaitingForRemote;
    static Handler mCallback;
    private ClientSession mCs;
    static final int MSG_SESSION_COMPLETE = 1;
    static final int MSG_GET_JOBID = 2;
    static public boolean mDocumentSent;
    private eventParser bppEp;
    private long mTimeStamp;
    static public boolean mConnected = false;
    static public IOException mEventStatus = null;

    public BluetoothBppEvent(Context context, ObexTransport transport) {
        if (transport == null) {
            throw new NullPointerException("transport is null");
        }

        if (D) Log.d(TAG, "context: "+ context + ", transport: " + transport);
        mContext = context;
        mTransport = transport;
        mInterrupted = false;
        bppEp = new BppEventParser();
    }

    public void start(Handler handler) {
        if (D) Log.d(TAG, "Start!");
        mCallback = handler;
        mThread = new ClientThread(mContext, mTransport);
        mThread.start();
    }

    public void stop() {
        if (D) Log.d(TAG, "Stop!");
        if (mThread != null) {
        //mInterrupted = true;
            try {
                if (V) Log.v(TAG, "try interrupt");
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

    private class ClientThread extends Thread {

        private static final int sSleepTime = 500;
        private Context mContext1;
        private volatile boolean waitingForShare;
        private ObexTransport mTransport1;
        private WakeLock wakeLock;

        public ClientThread(Context context, ObexTransport transport) {

            super("BtBpp Event ClientThread");
            mContext1 = context;
            mTransport1 = transport;
            waitingForShare = false;
            mWaitingForRemote = false;
            mDocumentSent = false;

            PowerManager pm = (PowerManager)mContext1.getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        }

        @Override
        public void run() {

            int status = BluetoothBppConstant.STATUS_SUCCESS;
            long timestamp = 0;

            if (V) Log.v(TAG, "acquire partial WakeLock");
            wakeLock.acquire();

            try {
                Thread.sleep(100);
            } catch (InterruptedException e1) {
                if (V) Log.v(TAG, "Client thread was interrupted (1), exiting");
                mInterrupted = true;
            }
            if (!mInterrupted) {
                connect();
            }

            while(!mInterrupted){
                if (D) Log.d(TAG, "SOAP Request: GetEvent");
                status = sendSoapRequest(BluetoothBppSoap.SOAP_REQ_GET_EVT);
                if(status != BluetoothBppConstant.STATUS_SUCCESS){
                    break;
                }
                try {
                    timestamp = System.currentTimeMillis();
                    Thread.sleep(500);
                } catch (InterruptedException e1) {
                    if (V) Log.v(TAG, "EvenMonitor thread was interrupted (1), exiting");
                    break;
                }
            }

            if (D) Log.d(TAG, "Event Monitor: Try to disconnect");
            disconnect();

            if (wakeLock.isHeld()) {
                if (V) Log.v(TAG, "release partial WakeLock");
                wakeLock.release();
            }
            BluetoothBppTransfer.mSessionHandler.obtainMessage(
                        BluetoothBppObexClientSession.MSG_SESSION_STOP, -1).sendToTarget();

        }

        @Override
        public void interrupt() {
            if (V) Log.v(TAG, "super.interrupt()");
            super.interrupt();
            if(mInterrupted){
                if (V) Log.v(TAG, "Interupt already in progress");
                return;
            }
            mInterrupted = true;

            if (mWaitingForRemote) {
                if (V) Log.v(TAG, "Interrupted when waitingForRemote");
                try {
                    mTransport1.close();
                } catch (IOException e) {
                    Log.e(TAG, "mTransport.close error");
                }
            }
        }

        private void disconnect() {
            try {
                if (mCs != null) {
                    mCs.disconnect(null);
                }
                mCs = null;
                mConnected = false;
                if (D) Log.d(TAG, "OBEX session disconnected");
            } catch (IOException e) {
                Log.w(TAG, "OBEX session disconnect error" + e);
                mEventStatus = e;
            }
            try {
                if (mCs != null) {
                    if (D) Log.d(TAG, "OBEX session close mCs");
                    mCs.close();
                    if (D) Log.d(TAG, "OBEX session closed");
                }
            } catch (IOException e) {
                Log.w(TAG, "OBEX session close error" + e);
                mEventStatus = e;
            }
            if (mTransport1 != null) {
                try {
                    if (D) Log.d(TAG, "Try mTransport1.close");
                    mTransport1.close();
                } catch (IOException e) {
                    Log.e(TAG, "mTransport.close error");
                    mEventStatus = e;
                }
            }
        }

        private void connect() {
            if (D) Log.d(TAG, "Create ClientSession with transport " + mTransport1.toString());
            try {
                mCs = new ClientSession(mTransport1);
                mConnected = true;
            } catch (IOException e1) {
                Log.e(TAG, "OBEX session create error");
            }
            if (mConnected) {
                mConnected = false;
                HeaderSet hs = new HeaderSet();

                hs.setHeader(HeaderSet.TARGET, BluetoothBppConstant.STS_Target_UUID);

                synchronized (this) {
                    mWaitingForRemote = true;
                }
                try {
                    int responseCode;
                    mCs.connect(hs);

                    if (D) Log.d(TAG, "OBEX session created");
                    mConnected = true;
                } catch (IOException e) {
                    Log.e(TAG, "OBEX session connect error");
                    mEventStatus = e;
                }
            }
            synchronized (this) {
                mWaitingForRemote = false;
            }
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

            /* register Bpp Soap Msg parser to ClientSession. */
            mCs.enableEventParser(bppEp);
            request = new HeaderSet();
            request.setHeader(HeaderSet.TYPE, BluetoothBppConstant.MIME_TYPE_SOAP);

            try {
                // it sends GET command with current existing Header now..
                getOperation = (ClientOperation)mCs.get(request);
            } catch (IOException e) {
                Log.e(TAG, "Error when get HeaderSet : " + e);
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

                            if (V) Log.v(TAG, "Waiting for Response..." +
                                                (System.currentTimeMillis() - timestamp) + "ms");
                            //Wait for response code
                            responseCode = getOperation.getResponseCode();

                            if (V) Log.v(TAG, "Tx - OBEX Response: " + ((responseCode
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

                                /* In case that a remote send a length for SOAP response right away
                                                            after SOAP request..
                                                        */
                                if((getOperation.getLength()!= -1) && (readBuff == null)){
                                    if (V) Log.v(TAG, "#1 Get OBEX Length - "
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
                    if (V) Log.v(TAG, "outputStream.close()");
                    outputStream.close();

                    // Prepare to get input data
                    if (V) Log.v(TAG, "getOperation.openInputStream()");
                    inputStream = getOperation.openInputStream();
                    // Check response code from the remote
                    if (V) Log.v(TAG, "getOperation.getResponseCode()");
                        mTimeStamp = System.currentTimeMillis();
                        if( (responseCode = getOperation.getResponseCode())
                            == ResponseCodes.OBEX_HTTP_CONTINUE) {
                            if (V) Log.v(TAG, "Rx - OBEX Response: Continue");
                            if((getOperation.getLength()!= -1) && (readBuff == null)){
                                if (V) Log.v(TAG, "#2 Get OBEX Length - " +
                                                                getOperation.getLength());
                                responseLength = (int)getOperation.getLength();
                                readBuff = new byte[responseLength];
                            }
                            if(readBuff != null){
                                if (V) Log.v(TAG, "Rx - Start Read buffer");
                                offset += inputStream.read(readBuff, offset,
                                                                (responseLength - offset));
                                if (V) Log.v(TAG, "OBEX Data Read: " + offset + "/"
                                                                + responseLength + "bytes");
                                readBuff= null;
                            }
                        }
                        // For Status Channel, it keeps send Continue response.
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
                status = BluetoothBppConstant.STATUS_CANCELED;
                mEventStatus = e;
            } catch (NullPointerException e) {
                if (V) Log.v(TAG, " NullPointerException : " + e);
                status = BluetoothBppConstant.STATUS_CANCELED;
            } catch (IndexOutOfBoundsException e) {
                if (V) Log.v(TAG, " IndexOutOfBoundsException : " + e);
                status = BluetoothBppConstant.STATUS_CANCELED;
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
                mCs.enableEventParser(null);

                synchronized (this) {
                    mWaitingForRemote = false;
                }
            }
            return status;
        }
    }

    public class BppEventParser implements eventParser{
        @Override
        public boolean Callback(String data) {
            if (V) Log.v(TAG, "GetEvent Response - taken " + (System.currentTimeMillis()
                                - mTimeStamp) + "ms");
            return BluetoothBppSoap.Parser("GetEvent", data);
        }
    }
}
