/*
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *    * Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *    * Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in the
 *      documentation and/or other materials provided with the distribution.
 *    * Neither the name of Code Aurora nor
 *      the names of its contributors may be used to endorse or promote
 *      products derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.quicinc.bluetooth;


import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.obex.BluetoothObexIntent;

import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.nio.channels.FileChannel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.os.Handler;
import android.os.Message;
import android.os.Environment;
import android.os.SystemProperties;
import android.provider.MediaStore.Audio;

public class BluetoothObexTransfer {
   static final boolean V = true;

   private static final boolean useSdCardForVCard = false;

   private static final String TAG = "BluetoothObexTransfer";
   protected static final int FINISHEDID = 0x1337;
   protected static final int UPDATESTATS = 0x1338;

   private BluetoothDevice mBluetooth;

   private ProgressBar mProgressBar;
   private TextView mProgressText;
   private Activity mActivity;
   private List<Callback> mCallbacks = new ArrayList<Callback>();

   private boolean mRegisteredBroadcasts=false;

   public BluetoothObexTransfer(Activity activity) {
      mActivity = activity;
      mBluetooth = (BluetoothDevice) mActivity.getSystemService(Context.BLUETOOTH_SERVICE);
      registerBluetoothOBexIntentHandler();
   }

   public void onDestroy() {
      unRegisterBluetoothOBexIntentHandler();
   }

   public void registerCallback(Callback callback) {
       synchronized (mCallbacks) {
           mCallbacks.add(callback);
           Log.i(TAG, "registerCallback callback added ");
       }
   }

   public void unregisterCallback(Callback callback) {
       synchronized (mCallbacks) {
           mCallbacks.remove(callback);
           Log.i(TAG, "unregisterCallback callback removed ");
       }
   }

   /* Is Bluetooth Enabled or not? */
   public boolean isBluetoothEnabled() {
      boolean enabled = false;
      if (mBluetooth != null) {
         enabled = mBluetooth.isEnabled();
      }
      return(enabled);
   }

   /* Is Bluetooth OBEX as supported by Qualcomm/QuIC Inc. BM3 enabled? */
   public boolean isBluetoothOBEXEnabled() {
      boolean enabled = SystemProperties.getBoolean("ro.qualcomm.proprietary_obex", false);
      return(enabled);
   }

   private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
         if (V) {
            Log.i(TAG, "Received " + intent.getAction());
         }

         String action = intent.getAction();

         if (action.equals(BluetoothObexIntent.PROGRESS_ACTION)) {

            String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
            int bytesTotal = intent.getIntExtra(BluetoothObexIntent.OBJECT_SIZE, 0);
            int bytesDone = intent.getIntExtra(BluetoothObexIntent.BYTES_TRANSFERRED, 0);
            synchronized (mCallbacks) {
                for (Callback callback : mCallbacks) {
                   callback.onProgressIndication(fileName, bytesTotal, bytesDone);
                }
            }


         } else if (action.equals(BluetoothObexIntent.RX_COMPLETE_ACTION)) {
            String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
            boolean success = intent.getBooleanExtra(BluetoothObexIntent.SUCCESS, false);
            synchronized (mCallbacks) {
                for (Callback callback : mCallbacks) {
                   callback.onReceiveCompleteIndication(fileName, success);
                }
            }

         } else if (action.equals(BluetoothObexIntent.TX_COMPLETE_ACTION)) {

            String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
            boolean success = intent.getBooleanExtra(BluetoothObexIntent.SUCCESS, false);
            String errorMsg = intent.getStringExtra(BluetoothObexIntent.ERROR_MESSAGE);

            if( false == success ) {
                Toast.makeText(mActivity, R.string.opp_ftp_send_failed, Toast.LENGTH_LONG).show();
            }

            synchronized (mCallbacks) {
                for (Callback callback : mCallbacks) {
                   callback.onTransmitCompleteIndication(fileName, success, errorMsg);
                }
            }

         }
      }
   };

   public void registerBluetoothOBexIntentHandler() {
      /* Register only if not already registered */
      synchronized (this) {
         if (mRegisteredBroadcasts ==  false) {
            IntentFilter filter = new IntentFilter();

            // Bluetooth on/off broadcasts
            filter.addAction(BluetoothObexIntent.PROGRESS_ACTION);
            filter.addAction(BluetoothObexIntent.TX_COMPLETE_ACTION);
            filter.addAction(BluetoothObexIntent.RX_COMPLETE_ACTION);
            mActivity.registerReceiver(mBroadcastReceiver, filter);
            mRegisteredBroadcasts=true;
            if (V) {
               Log.i(TAG, "registerBluetoothOBexIntentHandler");
            }
         }
      }
   }

   public void unRegisterBluetoothOBexIntentHandler() {
      synchronized (this) {
         if (mRegisteredBroadcasts ==  true) {
            mActivity.unregisterReceiver(mBroadcastReceiver);
            mRegisteredBroadcasts = false;
            if (V) {
               Log.i(TAG, "unRegisterBluetoothOBexIntentHandler");
            }
         }
      }
   }
   public interface Callback {
     /*
      * @param fileName Name of the file that completed transfer
      *
      * @param success true if transmit completed successfully
      *                otherwise false
      * @param errorString if success = false, errorString contains the errorstring
      */
      void onTransmitCompleteIndication(String fileName, boolean success, String errorString);

      /*
       * @param fileName Name of the file that completed transfer
       *
       * @param success true if receive completed successfully
       *                otherwise false
       */
      void onReceiveCompleteIndication(String fileName, boolean success);

      /*
      * @param fileName: The name of the file that needs to be sent.
      * @param totalBytes:  Total number of bytes sent so far
      * @param bytesDone:   Total number of bytes of the file
       */
      void onProgressIndication(String fileName, int bytesTotal, int bytesDone);
  }
}