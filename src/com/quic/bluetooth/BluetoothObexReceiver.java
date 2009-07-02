/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quic.bluetooth;

import com.quic.bluetooth.R;

import android.app.Service;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothIntent;
import android.bluetooth.obex.BluetoothObexIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.os.PowerManager;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

/**
 * BluetoothObexAuthorize is a receiver for any Bluetooth
 * OBEX Authorization request. It checks if the Bluetooth
 * Settings is currently visible and brings up Accept/Reject
 * dialog. Otherwise it puts a Notification in the status bar,
 * which can be clicked to bring up the Accept/Reject entry
 * dialog.
 */
public class BluetoothObexReceiver extends BroadcastReceiver {
   static final Object mStartingServiceSync = new Object();
   static PowerManager.WakeLock mStartingService;

   private static final String TAG = "BluetoothObexAuthorize";
   private String mAddress;
   private String mFileName;
   private String mObjectType;

   @Override
   public void onReceive(Context context, Intent intent) {
      String action = intent.getAction();
      if (action.equals(BluetoothObexIntent.AUTHORIZE_ACTION)) {
         intent.setClass(context, BluetoothObexReceiverService.class);
         beginStartingService(context, intent);
      } else if (action.equals(BluetoothObexIntent.RX_COMPLETE_ACTION)) {
         Log.v(TAG, "onReceive : RX_COMPLETE_ACTION ");
         boolean rxCompleteSuccess = intent.getBooleanExtra(BluetoothObexIntent.SUCCESS, true);

         /* If Notifications are disabled */
         if (BluetoothObexReceiverService.mNoNotification == false) {
            NotificationManager manager = (NotificationManager) context
                                             .getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(BluetoothObexReceiverService.NOTIFICATION_ID);
         }
         /** If it was a successfull receive, add the received object
         *   into the Android system (Contacts Database for vCard or run
         *   media scanner if it is a media file.
         */
         if (rxCompleteSuccess != false) {
            intent.setClass(context, BluetoothObexReceiverService.class);
            beginStartingService(context, intent);
         }
      }
   }

   /**
    * Start the service to process the current event notifications, acquiring
    * the wake lock before returning to ensure that the service will run.
    */
   public static void beginStartingService(Context context, Intent intent) {
      synchronized (mStartingServiceSync) {
         if (mStartingService == null) {
            PowerManager pm =
            (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            mStartingService = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                                              "StartingAlertService");
            mStartingService.setReferenceCounted(false);
         }
         mStartingService.acquire();
         context.startService(intent);
      }
   }

   /**
    * Called back by the service when it has finished processing notifications,
    * releasing the wake lock if the service is now stopping.
    */
   public static void finishStartingService(Service service, int startId) {
      synchronized (mStartingServiceSync) {
         if (mStartingService != null) {
            if (service.stopSelfResult(startId)) {
               mStartingService.release();
            }
         }
      }
   }
}
