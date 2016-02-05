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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Environment;
import android.util.Log;

import com.android.bluetooth.avrcp.AvrcpControllerService;
import com.android.bluetooth.BluetoothObexTransport;

import javax.obex.ObexTransport;
import javax.obex.ClientOperation;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.Operation;
import javax.obex.ResponseCodes;

import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import com.android.internal.util.FastXmlSerializer;

import java.util.HashMap;
import java.util.ArrayList;

public class AvrcpBipInitiator {

    private final static String TAG = "AvrcpBipInitiator";

    private final static String LOG_TAG = "AvrcpBipInitiator";

    private static final boolean D = true;

    private static boolean V = Log.isLoggable(LOG_TAG, Log.VERBOSE);

    private static final int SOCKET_CONNECTED = 10;

    private static final int SOCKET_ERROR = 11;

    private static final int L2CAP_INVALID_PSM = -1;

    private SocketConnectThread mConnectThread = null;

    private ObexTransport mObexTransport = null;

    private AvrcpBipIntObexClientSession mObexSession = null;

    /* device associated with BIP client */
    private final BluetoothDevice mDevice;

    private boolean mIsObexConnected = false;

    private String mReceiveFilePath;

    private static final byte OAP_TAGID_IMG_HANDLE = 0x30;

    private static final byte OAP_TAGID_IMG_DESCRIPTOR = 0x71;

    private static int mL2capPsm = L2CAP_INVALID_PSM;

    private AvrcpBipRequest mRequest;
    private AvrcpBipRequest mPendingRequest;
    private boolean mIsRequestProcessing = false;
    private AvrcpControllerService mAvrcpControllerService;
    final private String THUMB_PREFIX = "AVRCP_BIP_THUMB_";
    final private String IMG_PREFIX = "AVRCP_BIP_IMG_";

    /** Where we store temp Bluetooth received files on the external storage */
    public static final String DEFAULT_STORE_SUBDIR = "/bluetooth";


    public AvrcpBipInitiator(BluetoothDevice device,int psm) {
        mL2capPsm = psm;
        mDevice = device;
        mAvrcpControllerService = AvrcpControllerService.getAvrcpControllerService();
    }

    public boolean isObexConnected() {
        return mIsObexConnected;
    }
    public boolean isBipFetchInProgress () {
        if (mPendingRequest == null)
            return false;
        return true;
    }
    public boolean GetLinkedThumbnail(String imgHandle){
        String localPath = CheckCoverArtOnLocalPath(imgHandle, THUMB_PREFIX);
        Log.e(TAG, "localPath " + localPath);
        if (localPath == null) {
            AvrcpBipRequest request = new AvrcpBipGetLinkedThumbnail(imgHandle);
            return processBipRequest(request);
        } else {
            return true;
        }
    }

    private boolean GetImageProperties(String imgHandle, AvrcpBipImgDescriptor reqImgDescriptor){
        AvrcpBipRequest request = new AvrcpBipGetImageProperties(imgHandle, reqImgDescriptor);
        return processBipRequest(request);
    }

    public boolean GetImage(String imgHandle, String encoding, String pixel, long maxSize) {
        String localPath = CheckCoverArtOnLocalPath(imgHandle, IMG_PREFIX);
        Log.e(TAG, "localPath " + localPath);
        if (localPath == null) {
            String transformation = "stretch";
            String maxsize = Long.toString(maxSize);
            AvrcpBipImgDescriptor imgDescriptor = new AvrcpBipImgDescriptor(encoding, pixel,
                    maxsize, transformation);
            return GetImageProperties(imgHandle, imgDescriptor);
         } else {
            return true;
         }
    }

    private void onAvrcpBipGetImageProperties(String imgHandle,AvrcpBipGetImageProperties prop) {
        AvrcpBipImgDescriptor imgDesc;
        try {
            imgDesc= getPreferredImgDescriptor(prop);
        } catch (Exception e) {
            Log.e(TAG, "onAvrcpBipGetImageProperties"  + e);
            // recieve file is set to null, to indicate failure;
            mAvrcpControllerService.onImageFetched(imgHandle, null);
            return;
        }
        byte[] imgDescXmL = createXmlFromImgDescriptor(imgDesc);
        AvrcpBipRequest request = new AvrcpBipGetImage(imgHandle, imgDesc, imgDescXmL);
        processBipRequest(request);
    }

    private AvrcpBipImgDescriptor getPreferredImgDescriptor(AvrcpBipGetImageProperties prop) {
        Log.d(TAG," reqImgeDesc " + prop.mReqImgDesc + " resImageDEsc " + prop.mResImgDesc);
        if (prop.mReqImgDesc != null && prop.mResImgDesc != null) {
            AvrcpBipImgDescriptor resNativeImgDesc = null;
            /* Check if the req pixel fields contains only 1 "*". */
            if ((prop.mReqImgDesc.mPixel.indexOf("*") == -1) ||
                (prop.mReqImgDesc.mPixel.indexOf("*") != prop.mReqImgDesc.mPixel.lastIndexOf("*")))
            {
                Log.e(TAG, "GetPreferredImgDescriptor: req pixel field is invalid:"
                        + prop.mReqImgDesc.mPixel);
                return null;
            }
            int reqWidth = Integer.parseInt(prop.mReqImgDesc.mPixel.substring(
                    0, prop.mReqImgDesc.mPixel.indexOf("*")));
            int reqHeight = Integer.parseInt(prop.mReqImgDesc.mPixel.substring(
                    prop.mReqImgDesc.mPixel.lastIndexOf("*") + 1));

            for (int i = 0; i < prop.mResImgDesc.size(); i++) {
                AvrcpBipImgDescriptor mResImgDesc = prop.mResImgDesc.get(i);
                if (i==0) {
                    /* storing native format for image, incase if UI preferance does not match.*/
                    resNativeImgDesc = mResImgDesc;
                }
                if ((prop.mReqImgDesc.mEncoding).equals(mResImgDesc.mEncoding)) {
                    Log.e(TAG, "GetPreferredImgDescriptor: " + mResImgDesc.mEncoding);

                    if ((mResImgDesc.mPixel.indexOf("-") != -1)) {
                        Log.e(TAG, "GetPreferredImgDescriptor Range");
                        if (mResImgDesc.mPixel.indexOf("-") != mResImgDesc.mPixel.lastIndexOf("-"))
                        {
                            Log.e(TAG, "GetPreferredImgDescriptor: res pixel field is invalid:"
                                    + mResImgDesc.mPixel);
                        } else {
                             String range[] = mResImgDesc.mPixel.split("-",2);
                             if ((range[1].indexOf("*") == -1) ||
                                 (range[1].indexOf("*") != range[1].lastIndexOf("*"))) {
                                 Log.e(TAG, "GetPreferredImgDescriptor: res pixel is invalid:"
                                        + range[1]);
                                 return null;
                             }
                             int highWidth = Integer.parseInt(range[1].substring(
                                     0, range[1].indexOf("*")));
                             int highHeight = Integer.parseInt(range[1].substring(
                                     range[1].lastIndexOf("*") + 1));
                             if (V) Log.e (TAG, "lowest range: " + range[0]);
                             if (range[0].indexOf("**") == -1) {
                                 if ((range[0].indexOf("*") == -1) ||
                                     (range[0].indexOf("*") != range[0].lastIndexOf("*"))) {
                                     Log.e(TAG, "GetPreferredImgDescriptor: res pixel is invalid:"
                                            + range[0]);
                                     return null;
                                 }
                                int lowWidth = Integer.parseInt(range[0].substring(
                                        0, range[0].indexOf("*")));
                                int lowHeight = Integer.parseInt(range[0].substring(
                                        range[0].lastIndexOf("*") + 1));
                                if (V) Log.e(TAG, "reqWidth: " + reqWidth + "reqHeight: "
                                        + reqHeight + "lowWidth: " + lowWidth + "lowHeight: "
                                        + lowHeight+ "highWidth: " + highWidth + "highHeight: "
                                        + highHeight);
                                if ((reqWidth >= lowWidth && reqWidth <= highWidth)
                                    && (reqHeight >= lowHeight && reqHeight <= highHeight)){
                                    Log.e(TAG, "GetPreferredImgDescriptor: Match found @ " + i);
                                    return new AvrcpBipImgDescriptor(prop.mReqImgDesc.mEncoding,
                                            prop.mReqImgDesc.mPixel,mResImgDesc.mSize,
                                            mResImgDesc.mTransformation);
                                }
                             } else {
                                 if ((range[0].indexOf("*") == -1)) {
                                     Log.e(TAG, "GetPreferredImgDescriptor: res field is invalid:"
                                            + range[0]);
                                     return null;
                                 }
                                int lowWidth = Integer.parseInt(range[0].substring(
                                        0, range[0].indexOf("*")));
                                int lowHeight = (lowWidth * highHeight)/highWidth;
                                if (V) Log.e(TAG, "reqWidth: " + reqWidth + "reqHeight: "
                                        + reqHeight + "lowWidth: " + lowWidth + "lowHeight: "
                                        + lowHeight+ "highWidth: " + highWidth + "highHeight: "
                                        + highHeight);

                                if ((reqWidth >= lowWidth && reqWidth <= highWidth)
                                    && (reqHeight >= lowHeight && reqHeight <= highHeight)){
                                    Log.e(TAG, "GetSuiableImgDescrpriptor: Match found @ " + i);
                                    return new AvrcpBipImgDescriptor(prop.mReqImgDesc.mEncoding,
                                            prop.mReqImgDesc.mPixel,mResImgDesc.mSize,
                                            mResImgDesc.mTransformation);
                                }
                             }
                        }
                    } else {

                        int resWidth, resHeight;
                        /* Check if the pixel fields contains only 1 "*". */
                        if ((mResImgDesc.mPixel.indexOf("*") == -1) ||
                            mResImgDesc.mPixel.indexOf("*") != mResImgDesc.mPixel.lastIndexOf("*"))
                        {
                            Log.e(TAG, "GetPreferredImgDescriptor: res pixel field is invalid:"
                                    + mResImgDesc.mPixel);
                            return null;
                        }
                        resWidth = Integer.parseInt(mResImgDesc.mPixel.substring(
                                0, mResImgDesc.mPixel.indexOf("*")));
                        resHeight = Integer.parseInt(mResImgDesc.mPixel.substring(
                                mResImgDesc.mPixel.lastIndexOf("*") + 1));
                        if (V) Log.e(TAG, "reqWidth: " + reqWidth + "reqHeight: " + reqHeight
                                + "resWidth: " + resWidth + "resHeight: " + resHeight);
                        if (reqWidth <= resWidth && reqHeight <= resHeight){
                            Log.e(TAG, "GetPreferredImgDescriptor: Match found @ " + i);
                            return new AvrcpBipImgDescriptor(prop.mReqImgDesc.mEncoding,
                                    prop.mReqImgDesc.mPixel,mResImgDesc.mSize,
                                    mResImgDesc.mTransformation);
                        }
                    }
                }
            }

            if (resNativeImgDesc != null) {
                Log.e(TAG, "No valid match found, seclecting native encoding ");
                return new AvrcpBipImgDescriptor(resNativeImgDesc.mEncoding,
                        resNativeImgDesc.mPixel,resNativeImgDesc.mSize,
                        resNativeImgDesc.mTransformation);
            }
        }

        //this should never happen
        Log.d(TAG, "Native encoding not found, returning default request param");
        return prop.mReqImgDesc;
    }

    private boolean processBipRequest(AvrcpBipRequest req){
        Log.e(TAG, "processBipRequest: " + req);
        if (req != null) {
            if (mIsRequestProcessing) {
                mPendingRequest = req;
                Log.d(TAG," current request processing, pending req updated");
                return true;
            } else {
                mIsRequestProcessing = true;
                mRequest = req;
                if (mObexSession != null) {
                    return mObexSession.makeRequest(mRequest);
                }
                else {
                    Log.e(TAG," obexSession already disconnected ");
                    return false;
                }
            }
        }
        return false;
    }

    private void processBipRequestCompleted(AvrcpBipRequest req, boolean isSuccess){
        Log.e(TAG, "processBipRequestCompleted: " + req + "isSuccess: " + isSuccess);
        if (req != null) {

            if (mAvrcpControllerService != null) {
                if (req instanceof AvrcpBipGetLinkedThumbnail) {
                    mAvrcpControllerService.onThumbNailFetched(
                            ((AvrcpBipGetLinkedThumbnail)req).mImgHandle,
                            isSuccess ? mReceiveFilePath:null);
                } else if (req instanceof AvrcpBipGetImage){
                    mAvrcpControllerService.onImageFetched(((AvrcpBipGetImage)req).mImgHandle,
                            isSuccess ? mReceiveFilePath:null);
                } else if (req instanceof AvrcpBipGetImageProperties){
                    onAvrcpBipGetImageProperties(((AvrcpBipGetImageProperties)req).mImgHandle,
                            (AvrcpBipGetImageProperties)req);
                }
            }
        }
        mIsRequestProcessing = false;
        mRequest = null;
        if (mPendingRequest != null){
            mIsRequestProcessing = true;
            mRequest = mPendingRequest;
            mPendingRequest = null;
            if (mObexSession != null)
                mObexSession.makeRequest(mRequest);
            else
                Log.e(TAG," ObexSession already disconnected ");
        }
    }

    private String CheckCoverArtOnLocalPath(String imgHandle, String imgPrefix) {
        String fileToBeSearch = imgPrefix + imgHandle;

        File btDir = checkForBluetoothDir();
        if (btDir != null) {
            File file[] = btDir.listFiles();
            if (file != null) {
                for (int i = 0; i < file.length; i++) {
                    if (file[i].getName().startsWith(fileToBeSearch)) {
                        Log.e("TAG", "file is present @ " + file[i].getAbsolutePath());
                        if (mAvrcpControllerService != null) {
                            if (imgPrefix.equals(THUMB_PREFIX)) {
                                mAvrcpControllerService.onThumbNailFetched(
                                        imgHandle, file[i].getAbsolutePath());
                            } else if (imgPrefix.equals(IMG_PREFIX)) {
                                mAvrcpControllerService.onImageFetched(
                                    imgHandle, file[i].getAbsolutePath());
                            }
                        }
                        return file[i].getAbsolutePath();
                    }
                }
            }
        }
        return null;
    }

    private File checkForBluetoothDir()
    {
        File btDir = null;

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String root = Environment.getExternalStorageDirectory().getPath();
            btDir = new File(root + DEFAULT_STORE_SUBDIR);
            if (!btDir.isDirectory() && !btDir.mkdir()) {
                if (D) Log.d(TAG, "Receive File aborted - can't create base directory "
                            + btDir.getPath());
                return null;
            }
            return btDir;
        } else {
            if (D) Log.d(TAG, "Receive File aborted - no external storage");
            return btDir;
        }
    }

    private byte[] createXmlFromImgDescriptor(AvrcpBipImgDescriptor imgDescriptor)
    {

        StringWriter sw = new StringWriter();
        XmlSerializer xmlMsgElement = new FastXmlSerializer();
        // contruct the XML tag for a imgDescriptor single Msg listing
        try {
            xmlMsgElement.setOutput(sw);
            xmlMsgElement.startDocument("UTF-8", true);
            xmlMsgElement.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output",
                    true);
            xmlMsgElement.startTag(null, "image-descriptor");
            xmlMsgElement.attribute(null, "version",
                imgDescriptor.mVersion);
            xmlMsgElement.startTag(null, "image");
            xmlMsgElement.attribute(null, "encoding",
                imgDescriptor.mEncoding);
            xmlMsgElement.attribute(null, "pixel",
                imgDescriptor.mPixel);
            if (imgDescriptor.mMaxSize != null) {
                xmlMsgElement.attribute(null, "maxsize",
                        imgDescriptor.mMaxSize);
            }
            if (imgDescriptor.mTransformation != null) {
                xmlMsgElement.attribute(null, "transformation",
                        imgDescriptor.mTransformation);
            }
            xmlMsgElement.endTag(null, "image");
            xmlMsgElement.endTag(null, "image-descriptor");
            xmlMsgElement.endDocument();
            if (V) Log.v(TAG, "Image descriptor XML = " + sw.toString());
            return sw.toString().getBytes("UTF-8");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, e);
        } catch (IllegalStateException e) {
            Log.w(TAG, e);
        } catch (IOException e) {
            Log.w(TAG, e);
        }
        return null;
    }

    public void cleanup() {
        Log.d(TAG,"Cleanup");
        disconnect();
        clearCoverArtMapFromLocalPath();
    }

    private void clearCoverArtMapFromLocalPath() {
       /*
        * In this case hashmap of handle and images has to be cleared.
        */
        String fileToBeDeletedPrefix = "AVRCP_BIP_";
        File btDir = checkForBluetoothDir();
        if (btDir != null) {
            File file[] = btDir.listFiles();
            if (file != null) {
                for (int i = 0; i < file.length; i++) {
                    if (file[i].getName().startsWith(fileToBeDeletedPrefix)) {
                        Log.e(TAG, "file is deleted  @ " + file[i].getAbsolutePath());
                        file[i].delete();
                    }
                }
            }
        }
    }

    /**
     * Connects to BIP
     * <p>
     * Upon completion callback handler will receive {@link #EVENT_CONNECT}
     */
    public void connect() {
        if (mConnectThread == null && mObexSession == null) {

            mConnectThread = new SocketConnectThread();
            mConnectThread.start();
        }
    }

    /**
     * Disconnects from BIP
     * <p>
     * Upon completion callback handler will receive {@link #EVENT_CONNECT}
     */
    public void disconnect() {
        if (mConnectThread == null && mObexSession == null) {
            return;
        }

        if (mConnectThread != null) {
            mConnectThread.interrupt();
            mConnectThread = null;
        }

        if (mObexSession != null) {
            mObexSession.stop();
            mObexSession = null;
        }
        mIsRequestProcessing = false;
    }

    private class SocketConnectThread extends Thread {
        private BluetoothSocket socket = null;

        public SocketConnectThread() {
            super("SocketConnectThread");
        }

        @Override
        public void run() {
            if (connectL2capSocket())
            {
                Log.e(TAG, "L2CAP socket is connected Successfully");
                BluetoothObexTransport transport;
                transport = new BluetoothObexTransport(socket);
                mHandler.obtainMessage(SOCKET_CONNECTED, transport).sendToTarget();
            }
            else
            {
                Log.e(TAG, "Error when creating/connecting L2CAP socket");
                return;
            }
        }

        private boolean connectL2capSocket() {
            try {
                /* Use BluetoothSocket to connect */
                Log.v(TAG,"connectL2capSocket: PSM: " + mL2capPsm);
                if (mL2capPsm != L2CAP_INVALID_PSM) {
                    socket = mDevice.createL2capSocket(mL2capPsm);
                    socket.connect();
                } else {
                    return false;
                }

            } catch (IOException e) {
                Log.e(TAG, "Error when creating/connecting L2cap socket", e);
                return false;
            }
            return true;
        }

        @Override
        public void interrupt() {
            closeSocket();
        }

        private void closeSocket() {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error when closing socket", e);
            }
        }
    }

    private final Handler mHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            Log.v(TAG, "handleMessage  " + msg.what);

            switch (msg.what) {
                case SOCKET_ERROR:
                    mConnectThread = null;
                    mIsObexConnected = false;
                    break;

                case SOCKET_CONNECTED:
                    mConnectThread = null;
                    mObexTransport = (ObexTransport) msg.obj;

                    mObexSession = new AvrcpBipIntObexClientSession(mObexTransport, mHandler);
                    mObexSession.start();
                    break;

                case AvrcpBipIntObexClientSession.MSG_OBEX_CONNECTED:
                    mIsObexConnected = true;
                    if (mAvrcpControllerService != null)
                        mAvrcpControllerService.onBipConnected(mDevice);
                    break;

                case AvrcpBipIntObexClientSession.MSG_OBEX_DISCONNECTED:
                     mIsObexConnected = false;
                     mObexSession = null;
                     if (mAvrcpControllerService != null)
                         mAvrcpControllerService.onBipDisconnected();
                     break;

                case AvrcpBipIntObexClientSession.MSG_REQUEST_COMPLETED:
                     AvrcpBipRequest request = (AvrcpBipRequest) msg.obj;
                     boolean isSuccess = request.isSuccess();

                     Log.v(TAG, "MSG_REQUEST_COMPLETED (" + isSuccess + ") for "
                            + request.getClass().getName());
                     processBipRequestCompleted(request, isSuccess);
                     break;
                }
            }
    };

    private class AvrcpBipImgDescriptor {
           public String mVersion;
           public String mEncoding;
           public String mPixel;
           public String mSize;
           public String mMaxSize;
           public String mTransformation;
           public static final String DEFAULT_VERSION = "1.0";
           private String DEFAULT_ENCODING = "JPEG";
           private String DEFAULT_PIXEL = "";

           private AvrcpBipImgDescriptor(String encoding, String pixel,
               String maxSize, String transformation) {
               mVersion = DEFAULT_VERSION;
               mEncoding = encoding;
               mPixel = pixel;
               mMaxSize = maxSize;
               mTransformation = transformation;
               mSize = null;
           }

           private AvrcpBipImgDescriptor(HashMap<String, String> attrs) {
               mVersion = attrs.get("version");
               if (mVersion == null) mVersion = DEFAULT_VERSION;
               mEncoding = attrs.get("encoding");
               mPixel = attrs.get("pixel");
               mSize = attrs.get("size");
               mTransformation = attrs.get("transformation");
               mMaxSize = null;
           }

           public AvrcpBipImgDescriptor() {
               mVersion = DEFAULT_VERSION;
               mEncoding = DEFAULT_ENCODING;
               mPixel = DEFAULT_PIXEL;
           }
    };

    public abstract class AvrcpBipRequest {
        private final static String TAG = "AvrcpBipRequest";

        protected HeaderSet mHeaderSet;

        protected int mResponseCode;

        public AvrcpBipRequest() {
            mHeaderSet = new HeaderSet();
        }

        abstract public void execute(ClientSession session) throws IOException;

        protected void executeGet(ClientSession session) throws IOException {
            ClientOperation op = null;

            try {
                op = (ClientOperation) session.get(mHeaderSet);

                op.setGetFinalFlag(true);

                op.continueOperation(true, false);

                readResponseHeaders(op.getReceivedHeader());

                InputStream is = op.openInputStream();

                readResponse(is);
                is.close();

                op.close();
                mResponseCode = op.getResponseCode();
            } catch (IOException e) {
                mResponseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
                Log.w(TAG, "executeGet: ", e);
                throw e;
            }
        }

        final public boolean isSuccess() {
            Log.w(TAG, "isSuccess: " + mResponseCode);
            return (mResponseCode == ResponseCodes.OBEX_HTTP_OK);
        }

        protected void readResponse(InputStream stream) throws IOException {
            /* nothing here by default */
        }

        protected void readResponseHeaders(HeaderSet headerset) {
            /* nothing here by default */
        }
    }

    final class AvrcpBipGetLinkedThumbnail extends AvrcpBipRequest {

        private static final String TAG = "AvrcpBipGetLinkedThumbnail";

        private static final String TYPE = "x-bt/img-thm";

        private String tmpFilePath ;

        String mImgHandle;

        public AvrcpBipGetLinkedThumbnail(String handle) {
            mImgHandle = handle;
            mHeaderSet.setHeader(HeaderSet.TYPE, TYPE);
            mHeaderSet.setHeader(AvrcpBipInitiator.OAP_TAGID_IMG_HANDLE, mImgHandle);
        }

        @Override
        public void execute(ClientSession session) throws IOException {
            executeGet(session);
        }

        @Override
        protected void readResponse(InputStream is) throws IOException {
            byte[] buffer = new byte[4096]; // To hold file contents
            int bytes_read;
            String tmpFilePath ;
            File btDir;
            FileOutputStream tmp = null;
            btDir = checkForBluetoothDir();
            Log.w(TAG, "mResponseCode: " + mResponseCode + "btDir: " + btDir);
            if (btDir != null) {
                tmpFilePath = btDir.getPath() + "/" + THUMB_PREFIX
                       + mHeaderSet.getHeader(AvrcpBipInitiator.OAP_TAGID_IMG_HANDLE)
                       + ".jpeg";
                try {
                    Log.e(TAG,"readResponse: opening " + tmpFilePath + " file for writing image");
                    tmp = new FileOutputStream(tmpFilePath);
                } catch (FileNotFoundException e) {
                    Log.e(TAG,"readResponse: unable to open tmp File for writing");
                    throw e;
                }
                Log.e(TAG,"readResponse: writing to " + tmpFilePath);
                while ((bytes_read = is.read(buffer)) != -1)
                    tmp.write(buffer, 0, bytes_read);
                /* Flush the data to output stream */
                mReceiveFilePath =  tmpFilePath;
                tmp.flush();
                if (tmp != null)
                    tmp.close();
            }
        }
    }

    final class AvrcpBipGetImageProperties extends AvrcpBipRequest {

        private static final String TAG = "AvrcpBipGetImageProperties";

        private static final String TYPE = "x-bt/img-properties";

        public AvrcpBipImgDescriptor mReqImgDesc;

        public ArrayList<AvrcpBipImgDescriptor> mResImgDesc;

        public String mImgHandle;

        public AvrcpBipGetImageProperties(String handle, AvrcpBipImgDescriptor reqImgDescriptor) {

            mHeaderSet.setHeader(HeaderSet.TYPE, TYPE);
            mHeaderSet.setHeader(AvrcpBipInitiator.OAP_TAGID_IMG_HANDLE, handle);
            mImgHandle = handle;
            mReqImgDesc = reqImgDescriptor;
            mResImgDesc = new ArrayList<AvrcpBipImgDescriptor>();
        }

        @Override
        public void execute(ClientSession session) throws IOException {
            executeGet(session);
        }

       @Override
       protected void readResponse(InputStream is) throws IOException {
            parseImgPropertiesFromXml(is);
       }

       protected void parseImgPropertiesFromXml(InputStream is) {
            if (is == null) {
                Log.e(TAG, "input stream is null, mResponseCode: " + mResponseCode);
                return;
            }

            if (D) Log.d(TAG, "parseImgPropertiesFromXml");
            try {
                XmlPullParser imgDescParser = XmlPullParserFactory.newInstance().newPullParser();
                imgDescParser.setInput(is, "UTF-8");
                HashMap<String, String> attrs = new HashMap<String, String>();
                int event = imgDescParser.getEventType();
                while (event != XmlPullParser.END_DOCUMENT) {
                    switch (event) {
                        case XmlPullParser.START_TAG:
                            if (imgDescParser.getName().equals("image-properties")) {
                                if (V) Log.v(TAG, "image-properties version: " +
                                    imgDescParser.getAttributeValue(0)
                                    + "handle: " + imgDescParser.getAttributeValue(1));
                                attrs.put(imgDescParser.getAttributeName(0),
                                    imgDescParser.getAttributeValue(0));
                                attrs.put(imgDescParser.getAttributeName(1),
                                    imgDescParser.getAttributeValue(1));
                            }
                            if (imgDescParser.getName().equals("native")) {
                                for (int i = 0; i < imgDescParser.getAttributeCount(); i++) {
                                    attrs.put(imgDescParser.getAttributeName(i),
                                        imgDescParser.getAttributeValue(i));
                                    if (V) Log.v(TAG, "native: "
                                                    + imgDescParser.getAttributeName(i) + ":"
                                                    + imgDescParser.getAttributeValue(i));
                                }
                                mResImgDesc.add(new AvrcpBipImgDescriptor(attrs));
                            }
                            if (imgDescParser.getName().equals("variant")) {
                                HashMap<String, String> VarAttrs = new HashMap<String, String>();
                                for (int i = 0; i < imgDescParser.getAttributeCount(); i++) {
                                    VarAttrs.put(imgDescParser.getAttributeName(i),
                                        imgDescParser.getAttributeValue(i));
                                    if (V) Log.v(TAG, "variant: "
                                                    + imgDescParser.getAttributeName(i) + ":"
                                                    + imgDescParser.getAttributeValue(i));
                                }
                                mResImgDesc.add(new AvrcpBipImgDescriptor(VarAttrs));
                            }
                            break;
                    }
                    event = imgDescParser.next();
                }
                if (V) Log.v(TAG, "attrs " + attrs);
            } catch (XmlPullParserException e) {
                Log.e(TAG, "Error when parsing XML", e);
            } catch (IOException e) {
                Log.e(TAG, "I/O error when parsing XML", e);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid event received", e);
            }
            if (D) Log.d(TAG, "parseImgDescXml returning " + mResImgDesc);
            return;
        }

    }


    final class AvrcpBipGetImage extends AvrcpBipRequest {

        private static final String TAG = "AvrcpBipGetImage";

        private static final String TYPE = "x-bt/img-img";

        String mImgHandle;

        byte[] mImgDescXml;

        AvrcpBipImgDescriptor mImgDesc;

        public AvrcpBipGetImage(String handle, AvrcpBipImgDescriptor imgDesc,
                byte[] imgDescXml) {
            mImgHandle = handle;
            mImgDesc = imgDesc;
            mImgDescXml = imgDescXml;
            mHeaderSet.setHeader(HeaderSet.TYPE, TYPE);
            mHeaderSet.setHeader(AvrcpBipInitiator.OAP_TAGID_IMG_HANDLE, mImgHandle);
            mHeaderSet.setHeader(AvrcpBipInitiator.OAP_TAGID_IMG_DESCRIPTOR, mImgDescXml);
        }

        @Override
        public void execute(ClientSession session) throws IOException {
            executeGet(session);
        }

       @Override
       protected void readResponse(InputStream is) throws IOException {
             byte[] buffer = new byte[4096]; // To hold file contents
             int bytes_read;
             String tmpFilePath ;
             File btDir;
             FileOutputStream tmp = null;
             btDir = checkForBluetoothDir();
             Log.w(TAG, "btDir: " + btDir);
             if (btDir != null) {
                 tmpFilePath = btDir.getPath() + "/" + IMG_PREFIX
                        + mHeaderSet.getHeader(AvrcpBipInitiator.OAP_TAGID_IMG_HANDLE)
                        + "." + mImgDesc.mEncoding;
                 try {
                     Log.e(TAG,"readResponse: opening " + tmpFilePath + " file for writing image");
                     tmp = new FileOutputStream(tmpFilePath);
                 } catch (FileNotFoundException e) {
                     Log.e(TAG,"readResponse: unable to open tmp File for writing");
                     throw e;
                 }
                 Log.e(TAG,"readResponse: writing to " + tmpFilePath);
                 while ((bytes_read = is.read(buffer)) != -1)
                     tmp.write(buffer, 0, bytes_read);
                 /* Flush the data to output stream */
                 mReceiveFilePath =  tmpFilePath;
                 tmp.flush();
                 if (tmp != null)
                     tmp.close();
             }
         }
    }
}


