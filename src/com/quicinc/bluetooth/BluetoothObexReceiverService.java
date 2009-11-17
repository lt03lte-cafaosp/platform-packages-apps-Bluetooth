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

package com.quicinc.bluetooth;

import com.quicinc.bluetooth.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.Environment;
import android.app.Activity;
import android.app.Service;
import android.util.Log;
import android.widget.Toast;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothIntent;
import android.bluetooth.obex.BluetoothObexIntent;
import android.bluetooth.obex.BluetoothOpp;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.MediaScannerConnection;
import android.media.MediaScannerConnection.MediaScannerConnectionClient;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.File;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;


/**
 * This service essentially plays the role of a "worker thread"
 */
public class BluetoothObexReceiverService extends Service {
   private static final String TAG = "BluetoothObexReceiverService";
   private static final boolean V = false;

   private BluetoothOpp mBluetoothOPP;

   /* If Notification is not needed, then set this to true. If the alert dialog for
      authorize needs to be handled through a notification, set this to false
      */
   public static final boolean mNoNotification = true;
   public static final int NOTIFICATION_ID = android.R.drawable.stat_sys_data_bluetooth;

   private ServiceHandler mServiceHandler;
   private Looper mServiceLooper;
   private int mServiceStartId;

   private static final String FILE_TYPE_VCARD     = "text/x-vCard";
   private static final String FILE_TYPE_VCALENDER = "text/x-vcalendar";
   private static final int MAX_VCARD_SIZE = 1000000;

   public Handler mToastHandler = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         Toast.makeText(BluetoothObexReceiverService.this, R.string.opp_media_file_received,
                        Toast.LENGTH_SHORT).show();
      }
   };

   @Override
   public void onCreate() {
      if (V) {
         Log.i(TAG, "onCreate");
      }

      // Start up the thread running the service.  Note that we create a
      // separate thread because the service normally runs in the process's
      // main thread, which we don't want to block.
      HandlerThread thread = new HandlerThread(TAG, Process.THREAD_PRIORITY_BACKGROUND);
      thread.start();

      mServiceLooper = thread.getLooper();
      mServiceHandler = new ServiceHandler(mServiceLooper);
   }

   @Override
   public void onStart(Intent intent, int startId) {
      if (V) {
         Log.i(TAG, "onStart: #" + startId + ": " + intent.getExtras());
      }

      Message msg = mServiceHandler.obtainMessage();
      msg.arg1 = startId;
      msg.obj = intent;
      mServiceHandler.sendMessage(msg);
   }

   @Override
   public void onDestroy() {
      if (V) {
         Log.i(TAG, "onDestroy");
      }
      mServiceLooper.quit();
   }

   @Override
   public IBinder onBind(Intent intent) {
      return null;
   }

   private final class ServiceHandler extends Handler {
      public ServiceHandler(Looper looper) {
         super(looper);
      }

      /**
       * Handle incoming transaction requests.
       */
      @Override
      public void handleMessage(Message msg) {
         boolean handlingComplete = true;
         if (V) {
            Log.i(TAG, "Handling incoming Obex Object: " + msg);
         }
         Intent intent = (Intent)msg.obj;
         mServiceStartId = msg.arg1;

         String action = intent.getAction();

         if (BluetoothObexIntent.AUTHORIZE_ACTION.equals(action)) {
            handleObexAuthorize(intent);
         } else if (BluetoothObexIntent.RX_COMPLETE_ACTION.equals(action)) {
            if (V) {
               Log.i(TAG, "handleMessage : RX_COMPLETE_ACTION ");
            }
            handlingComplete = handleObexObjectReceived(intent);
         }

         if (handlingComplete == true) {
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            BluetoothObexReceiver.finishStartingService(BluetoothObexReceiverService.this, mServiceStartId);
         }
      }
   }

   /* Handle posting a Notification for user to authorize the Transfer,
      if the object type is one of what is supported
      Support Object types: VCard, VCalender, Audio and Picture file
      types supported by Android Media Scanner
   */
   private void handleObexAuthorize(Intent intent) {
      LocalBluetoothManager localManager = LocalBluetoothManager.getInstance(this);

      /* Get the parameters for the Authorize Intent */
      String mAddress = intent.getStringExtra(BluetoothObexIntent.ADDRESS);
      String mFileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
      String mObjectType = intent.getStringExtra(BluetoothObexIntent.OBJECT_TYPE);

      if ( (mAddress != null)
           && (mFileName != null)
           && (true == isSupportedFileType(mFileName, mObjectType)) ) {

         String filePath = new String();
         if ( isFileVcard(mFileName, mObjectType) || isFileVcalender(mFileName, mObjectType) ) {

            if (V) {
               Log.i(TAG, "vCard/vCalender File authorization : " + mFileName);
            }

            FileOutputStream fos = null;
            try {
                /* Get a temporary File object to strip off any path info
                   that may be in the filename */
                File file = new File(mFileName);

                /* Open a stream to a private File then get the path to that
                   file */
                fos = this.openFileOutput(file.getName(), Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
                file = this.getFileStreamPath(file.getName());
                filePath = file.getCanonicalPath();
            } catch (IOException ioe) {
                ioe.printStackTrace();
            } finally {
                try {
                   /* Clean up after the write */
                   if (fos != null) {
                      fos.close();
                   }
                } catch (IOException e) {
                   e.printStackTrace();
                }
            }
         } else if ( Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED) ){
            if (V) {
               Log.i(TAG, "Media File authorization : " + mFileName);
            }
            filePath =
            Environment.getExternalStorageDirectory().getAbsolutePath()+ "/" + mFileName;

         } else {
            Toast.makeText(BluetoothObexReceiverService.this, R.string.no_sdcard_exist, Toast.LENGTH_LONG).show();
            return;
         }
         if (V) {
            Log.i(TAG, "handleObexAuthorize - File Name : <" + filePath + ">");
         }

         Intent authorizeIntent = new Intent();
         authorizeIntent.setClass(this, BluetoothObexAuthorizeDialog.class);

         authorizeIntent.putExtra(BluetoothObexIntent.ADDRESS, mAddress);
         authorizeIntent.putExtra(BluetoothObexIntent.OBJECT_FILENAME, filePath);

         authorizeIntent.setAction(BluetoothObexIntent.AUTHORIZE_ACTION);
         authorizeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_HISTORY);

         if (mNoNotification == true) {
            startActivity(authorizeIntent);
         } else {
            // Put up a notification that leads to the dialog
            Resources res = getResources();
            Notification notification = new Notification(
                                                        NOTIFICATION_ID,
                                                        res.getString(R.string.bluetooth_notify_obexauthorize_ticker),
                                                        System.currentTimeMillis());

            PendingIntent pending = PendingIntent.getActivity(this, 0,
                                                              authorizeIntent, PendingIntent.FLAG_ONE_SHOT);

            String name = localManager.getLocalDeviceManager().getName(mAddress);

            notification.setLatestEventInfo(this,
                                            res.getString(R.string.bluetooth_notif_obexauthorize_title),
                                            res.getString(R.string.bluetooth_notif_obexauthorize_file_message) + name,
                                            pending);
            notification.flags |= Notification.FLAG_AUTO_CANCEL;

            NotificationManager manager = (NotificationManager)
                                          getSystemService(Context.NOTIFICATION_SERVICE);
            manager.notify(NOTIFICATION_ID, notification);
         }
      } else {
         /* Reject the transfer */
         if (V) {
            Log.i(TAG, "handleObexAuthorize - Reject the Transfer - Unsupported File type -- FileName : " + mFileName);
         }
         mBluetoothOPP = new BluetoothOpp();
         mBluetoothOPP.obexAuthorizeComplete(mFileName, false, mFileName);
      }
      return;
   }

   /* An OBEX Object is completely received, Now check again if it is a
      supported file type and do the following:
      vCard: Add the contact into Contact Database.
      vCalender: Add to Calendar.
      Media: Invoke Media scanner on the file.
   */
   private boolean handleObexObjectReceived(Intent intent) {
      boolean handlingComplete = true;
      String fileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
      int profile = intent.getIntExtra(BluetoothObexIntent.PROFILE, -1);

      if (V) {
         Log.i(TAG, "handleObexObjectReceived - File Name : -" + fileName + "-" + " Profile : " + profile);
      }
      if ( (fileName != null) && (!TextUtils.isEmpty(fileName)) ) {

        if ( true == isFileVcard(fileName, "") ) {
           if (V) {
              Log.i(TAG, "vCard File received : " + fileName);
           }
           if (profile == BluetoothObexIntent.PROFILE_OPP) {
               if (V) {
                   Log.i(TAG, "Received BluetoothObexIntent.PROFILE_OPP : " + fileName);
               }
               handlingComplete = addContact(fileName);
               if (false == handlingComplete) {
                   Toast.makeText(BluetoothObexReceiverService.this, R.string.opp_vcard_outofmemory,
                       Toast.LENGTH_LONG).show();
               }
           } else if (profile == BluetoothObexIntent.PROFILE_FTP) {
               if (V) {
                   Log.i(TAG, "Received BluetoothObexIntent.PROFILE_FTP : " + fileName);
               }
           } else {
               Log.e(TAG, "Error: No OBEX profile specified for received vCard.");
           }
        } else if ( true == isFileVcalender(fileName, "") ) {
           /* vCal is not supported yet. */
           if (V) {
              Log.i(TAG, "vCalender File received : " + fileName);
           }
           handlingComplete = true;
        } else {
           /* Let handleMediaFile check the media file type. */
           if (V) {
              Log.i(TAG, "Check for Media File received : " + fileName);
           }
           handlingComplete = handleMediaFile(fileName);
        }

      }
      return handlingComplete;
   }
   /* Invoke addContact will read a vCard file and add the
   *   information into the Android Contact database.
   *
   * @param String The Name of the vCard file
   *
   * @return None.
   */
   private boolean addContact(String vCardFileName) {

      FileInputStream fis = null;
      InputStreamReader isr = null;
      String vCard = "";

      if (V) {
         Log.i(TAG, "Received new vCard File : " + vCardFileName);
      }
      try {
         File newFile = new File(vCardFileName);
         int fileSize = (int)newFile.length();
         if (fileSize > MAX_VCARD_SIZE) {
             /* Some vCard is too big to have in memory. But it should be rare. */
             Log.e(TAG, "Received vCard is too big to have in memory. Ignore the entry : " + vCardFileName);
             newFile.delete();
              return false;
         }
         fis = new FileInputStream(newFile);
         if (fis != null) {
            isr = new InputStreamReader(fis);
            Reader in = new BufferedReader(isr);

            StringBuffer buffer = new StringBuffer(fileSize);
            if (buffer != null) {
               int input;
               while ((input = in.read()) > -1) {
                   buffer.append((char) input);
               }
               vCard = buffer.toString();
               in.close();
            }
         }
         /* Delete the vCard file after adding to the stream */
         newFile.delete();

      } catch (IOException ioe) {
         ioe.printStackTrace();

      } finally {
         try {
            if (isr != null) {
               isr.close();
            }
            if (fis != null) {
               fis.close();
            }
         } catch (IOException e) {
            e.printStackTrace();
         }
      }
      if (TextUtils.isEmpty(vCard) == false) {
         VCardManager vManager = new VCardManager(this, vCard);
         Uri uri = vManager.save();
         if (V) {
            Log.i(TAG, "New Contact <" + vManager.getName() + "> is added at : " + uri.toString());
         }
      } else {
         if (V) {
            Log.i(TAG, "Empty vCard received? ");
         }
      }
      return true;
   }

   /* For Music/Picture: Invoke Media scanner on the file.
   */
   private boolean handleMediaFile(String fileName) {
      boolean handlingComplete = true;
      int fileType = 0;
      String mimeType = "";
      String fileExt = getFileExtension(fileName);
      MediaFile mediaFile = new MediaFile();
      if (V) {
         Log.i(TAG, "handleMediaFile: " + fileName);
      }

      MediaFile.MediaFileType mediaFileType = mediaFile.getFileType(fileName);
      if (mediaFileType != null) {
         fileType = mediaFileType.fileType;
         mimeType = mediaFileType.mimeType;
         if ( (mediaFile.isImageFileType(fileType)) ||
              (mediaFile.isAudioFileType(fileType)) ||
              (mediaFile.isVideoFileType(fileType)) ) {
            /* Invoke Media Scanner connection */
            new MediaScannerNotifier(this, fileName, mimeType);
            handlingComplete = false;
         }
      }
      return handlingComplete;
   }

   /* Get the File Extension from the path */
   private static String getFileExtension(String path) {
      if (path == null) {
         return null;
      }
      int lastDot = path.lastIndexOf(".");
      if (lastDot < 0) {
         return null;
      }
      return path.substring(lastDot + 1).toUpperCase();
   }


   /* Check from the objectType and File extension, if it is a vCard */
   private static boolean isFileVcard(String path, String objectType) {
      /* First check for object type */
      if (objectType != null) {
         if ( FILE_TYPE_VCARD.equalsIgnoreCase(objectType) ) {
            return true;
         }
      }

      if (path != null) {
         String fileExt = getFileExtension(path);
         /* Check File extension */
         if (fileExt != null) {
            // Get file type or mime type from file extension if objectType is null or invalid.
            if ( fileExt.equalsIgnoreCase("VCF") || fileExt.equalsIgnoreCase("VCARD") ) {
               return true;
            }
         }
      }
      return false;
   }

   /* Check from the objectType and File extension, if it is a vCalender */
   private static boolean isFileVcalender(String path, String objectType) {
      /* First check for object type */
      if (objectType != null) {
         if ( FILE_TYPE_VCALENDER.equalsIgnoreCase(objectType) ) {
            return true;
         }
      }

      if (path != null) {
         String fileExt = getFileExtension(path);
         /* Check File extension */
         if (fileExt != null) {
            // Get file type or mime type from file extension if objectType is null or invalid.
            if ( fileExt.equalsIgnoreCase("VCS") || fileExt.equalsIgnoreCase("VCAL") ) {
               return true;
            }
         }
      }
      return false;
   }

   /* Check from the objectType and File path, if it is a Audio or Image file type
      that the MediaScanner support
    */
   private static boolean isSupportedMediaType(String path, String objectType) {
      MediaFile mediaFile = new MediaFile();

      if (V) {
         Log.i(TAG, "isSupportedMediaType: " + path + "objectType : " + objectType);
      }

      if (objectType != null) {
         // Get file type and MIME type from objectType if objectType is not null.
         int fileType = mediaFile.getFileTypeForMimeType(objectType);
         if (fileType != 0) {
            return true;
         }
      }
      if (path != null) {
         MediaFile.MediaFileType mediaFileType = mediaFile.getFileType(path);
         if (mediaFileType != null) {
            if (V) {
               Log.i(TAG, "isSupportedMediaType: mediaFileType "+ mediaFileType.fileType);
            }
            if ( (mediaFile.isImageFileType(mediaFileType.fileType)) ||
                 (mediaFile.isAudioFileType(mediaFileType.fileType)) ||
                 (mediaFile.isVideoFileType(mediaFileType.fileType)) ) {
               return true;
            }
         }
      }
      return false;
   }

   private boolean isSupportedFileType(String path, String objectType) {
      boolean supported = false;
      /* Is it a vCard ? */
      if ( true == isFileVcard(path, objectType)) {
         return true;
      }
      /* Is it Calendar ? */
      if ( true == isFileVcalender(path, objectType)) {
         return true;
      }
      /* Is it Music or Picture ? */
      if ( true == isSupportedMediaType(path, objectType)) {
         return true;
      }
      /* Unsupported file */
      return false;
   }

   /**
    * This notifier is created after an attachment completes downloaded.  It attaches to the
    * media scanner and waits to handle the completion of the scan.
   */
   private class MediaScannerNotifier implements MediaScannerConnectionClient {
      private Context mContext;
      private MediaScannerConnection mConnection;
      private String mPath;
      private String mMimeType;

      public MediaScannerNotifier(Context context, String path, String mimeType) {
         if (V) {
            Log.i(TAG, "MediaScannerNotifier: " + path + " " + mimeType);
         }
         mContext = context;
         mPath = path;
         mMimeType = mimeType;
         mConnection = new MediaScannerConnection(context, this);
         if (V) {
            Log.i(TAG, "MediaScannerConnection: " + mPath + " " + mMimeType);
         }
         mConnection.connect();
      }

      public void onMediaScannerConnected() {
         if (V) {
            Log.i(TAG, "onMediaScannerConnected: " + mPath + " " + mMimeType);
         }
         mConnection.scanFile(mPath, mMimeType);
      }

      public void onScanCompleted(String path, Uri uri) {
         mConnection.disconnect();
         mContext = null;

         // NOTE: We MUST not call stopSelf() directly, since we need to
         // make sure the wake lock acquired by AlertReceiver is released.
         BluetoothObexReceiver.finishStartingService(BluetoothObexReceiverService.this, mServiceStartId);
      }
   }
}