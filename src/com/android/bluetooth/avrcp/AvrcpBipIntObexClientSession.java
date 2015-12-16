/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
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


package com.android.bluetooth.avrcp;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.ObexTransport;
import javax.obex.ObexHelper;
import javax.obex.ResponseCodes;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;


import com.android.bluetooth.avrcp.AvrcpBipInitiator.*;


public class AvrcpBipIntObexClientSession {

    private static final String TAG = "AvrcpBipIntObexClientSession";

    static final int MSG_OBEX_CONNECTED = 100;
    static final int MSG_OBEX_DISCONNECTED = 101;
    static final int MSG_REQUEST_COMPLETED = 102;

    private final ObexTransport mTransport;

    private final Handler mSessionHandler;

    private ClientThread mClientThread;

    private volatile boolean mInterrupted;

    private static final byte[] avrcpBipRsp = new byte[] {
         (byte)0x71, (byte)0x63, (byte)0xDD, (byte)0x54,
         (byte)0x4A, (byte)0x7E, (byte)0x11, (byte)0xE2,
         (byte)0xB4, (byte)0x7C, (byte)0x00, (byte)0x50,
         (byte)0xC2, (byte)0x49, (byte)0x00, (byte)0x48
    };

    private class ClientThread extends Thread {
        private final ObexTransport mTransport;

        private ClientSession mSession;

        private AvrcpBipRequest mRequest;

        private boolean mConnected;

        public ClientThread(ObexTransport transport) {
            super("BIP ClientThread");

            mTransport = transport;
            mConnected = false;
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);

            connect();

            if (mConnected) {
                mSessionHandler.obtainMessage(MSG_OBEX_CONNECTED).sendToTarget();
            } else {
                mSessionHandler.obtainMessage(MSG_OBEX_DISCONNECTED).sendToTarget();
                return;
            }

            while (!mInterrupted) {
                synchronized (this) {
                    if (mRequest == null) {
                        try {
                            this.wait();
                        } catch (InterruptedException e) {
                            mInterrupted = true;
                        }
                    }
                }

                if (!mInterrupted && mRequest != null) {
                    try {
                        mRequest.execute(mSession);
                    } catch (IOException e) {
                        // this will "disconnect" to cleanup
                        mInterrupted = true;
                    }

                    AvrcpBipRequest oldReq = mRequest;
                    mRequest = null;

                    mSessionHandler.obtainMessage(MSG_REQUEST_COMPLETED, oldReq).sendToTarget();
                }
            }

            disconnect();

            mSessionHandler.obtainMessage(MSG_OBEX_DISCONNECTED).sendToTarget();
        }

        private void connect() {
            try {
                Log.w(TAG, "connect:");
                mSession = new ClientSession(mTransport);

                HeaderSet headerset = new HeaderSet();
                headerset.setHeader(HeaderSet.TARGET, avrcpBipRsp);

                headerset = mSession.connect(headerset);
                Log.d(TAG," Rsp Code: " + headerset.getResponseCode());
                if (headerset.getResponseCode() == ResponseCodes.OBEX_HTTP_OK) {
                    mConnected = true;
                } else {
                    disconnect();
                }
            } catch (IOException e) {
                Log.w(TAG, "handled connect exception: ", e);
            }
        }

        private void disconnect() {
            Log.w(TAG, "disconnect: ");
            try {
                mSession.disconnect(null);
            } catch (IOException e) {
                Log.w(TAG, "handled disconnect exception:", e);
            }

            try {
                if (mConnected)
                    mSession.close();
            } catch (IOException e) {
                Log.w(TAG, "handled disconnect exception:", e);
            }

            mConnected = false;
        }

        public synchronized boolean schedule(AvrcpBipRequest request) {
            if (mRequest != null) {
                return false;
            }

            mRequest = request;
            notify();

            return true;
        }

        private void shutdown() {
            Log.w(TAG, "shutdown ");
            mInterrupted = true;
            interrupt();
        }
    }

    public AvrcpBipIntObexClientSession(ObexTransport transport, Handler handler) {
        mTransport = transport;
        mSessionHandler = handler;
    }

    public void start() {
        if (mClientThread == null) {
            mClientThread = new ClientThread(mTransport);
            mClientThread.start();
        }

    }

    public void stop() {
        if (mClientThread != null) {
            mClientThread.shutdown();

            Thread t = new Thread(new Runnable() {
                public void run () {
                    Log.d(TAG, "Spawning a new thread for stopping obex session");
                    try {
                        mClientThread.join();
                        mClientThread = null;
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Interrupted while waiting for thread to join");
                    }
                }
            });
            t.start();
            Log.d(TAG, "Exiting from the stopping thread");
        }
    }

    public boolean makeRequest(AvrcpBipRequest request) {
        if (mClientThread == null) {
            return false;
        }
        mClientThread.mSession.setLocalSrmStatus(true);
        return mClientThread.schedule(request);
    }
}

