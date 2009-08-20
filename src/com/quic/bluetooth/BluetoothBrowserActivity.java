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

package com.quic.bluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.IOException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.TabActivity;
import android.os.Environment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.Toast;
import android.widget.TabHost.TabSpec;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnClickListener;
import android.text.TextUtils;

import com.quic.bluetooth.R;

public class BluetoothBrowserActivity extends TabActivity implements BluetoothObexTransfer.Callback{

    public static final boolean V = true;
    private static final String TAG = "BluetoothBrowserActivity";

    protected static final int FINISHEDID = 0x1337;
    protected static final int UPDATESTATS = 0x1338;
    protected static final int ADDSENDFILE = 0x1339;
    protected static final int ADDGETFILE = 0x1340;

    private static final int DIALOG_BACK_DISCONNECT = 1;
    private static final int DIALOG_PROGRESS = 2;

    private static final String LOCAL_TAB_TAG = "LocalFiles";
    private static final String REMOTE_TAB_TAG = "RemoteFiles";
    private RemoteFileManagerActivity mRemoteFileManagerActivity;
    private LocalFileManagerActivity mLocalFileManagerActivity;

    private static final boolean ALWAYS_USE_BT_RX_FOLDER = false;
    private static String mFTPClientRecieveFolder = "/sdcard/bluetooth/ReceivedFiles/";


   /**
    * For transfering contact over Bluetooth
    */
   private BluetoothObexTransfer mBluetoothObexTransfer;

    private TabHost mTabHost;
    private TabSpec mLocalTabSpec;
    private TabSpec mRemoteTabSpec;
    /** Dialog that displays the progress of the Put/Get */
    private ProgressDialog mProgressDialog=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main);
        /**
         * For transfering contact over Bluetooth
         */
        mBluetoothObexTransfer = new BluetoothObexTransfer(BluetoothBrowserActivity.this);
        mTabHost = getTabHost();
        setupLocalFilesTab();

        /* Enable the Remote File browser only if Bluetooth OBEX is enabled*/
        if(isBluetoothOBEXEnabled())
        {
           setupRemoteFilesTab();
           mTabHost.setCurrentTab(1);
        }
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.v(TAG, "onSaveInstanceState");
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.v(TAG, "onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.v(TAG, "onStop");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "onPause");
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy");
        if(mBluetoothObexTransfer != null) {
            mBluetoothObexTransfer.onDestroy();
      }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        Log.v(TAG, "onCreateOptionsMenu");

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        Log.v(TAG, "onPrepareOptionsMenu");

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        Log.v(TAG, "onPrepareOptionsMenu");

        return true;
    }



    private void setupLocalFilesTab() {
        mLocalTabSpec = mTabHost.newTabSpec(LOCAL_TAB_TAG);
        mLocalTabSpec.setIndicator(getString(R.string.my_files));
    //    , getResources().getDrawable(R.drawable.ic_folder));
        mLocalTabSpec.setContent(new Intent(this,
                LocalFileManagerActivity.class));
        mTabHost.addTab(mLocalTabSpec);
    }

    private void setupRemoteFilesTab() {

        /* Create the folder where files have to be saved */
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            mFTPClientRecieveFolder = Environment.getExternalStorageDirectory().getAbsolutePath() + "/bluetooth/ReceivedFiles/";
            File dir = new File (mFTPClientRecieveFolder);
            dir.mkdirs();
        }

        mRemoteTabSpec = mTabHost.newTabSpec(REMOTE_TAB_TAG);
        mRemoteTabSpec.setIndicator(getString(R.string.server_files, "Remote"));
    //    , getResources().getDrawable(R.drawable.ic_bluetooth_small));
        mRemoteTabSpec.setContent(new Intent(this,
                RemoteFileManagerActivity.class));
        mTabHost.addTab(mRemoteTabSpec);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isServerConnected()) {
                showDialog(DIALOG_BACK_DISCONNECT);
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    } /* onKeyDown */

    @Override
    protected Dialog onCreateDialog(int id) {
       if(mRemoteFileManagerActivity != null) {
          if(mRemoteFileManagerActivity.mFTPClient != null) {
              switch (id) {
              case DIALOG_BACK_DISCONNECT:
                     String msg = "Disconnet from : " + getFTPServerName() + " ?";
                     return new AlertDialog.Builder(this).setTitle(msg).setIcon(
                           android.R.drawable.ic_dialog_alert).setPositiveButton(
                           android.R.string.ok, new OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) {
                                 /** Disconnect */
                                 mRemoteFileManagerActivity
                                       .handleServerDisconnect();
                              }
                           }).setNegativeButton(android.R.string.cancel,
                           new OnClickListener() {
                              public void onClick(DialogInterface dialog, int which) {
                                 // Cancel should not do anything.
                              }
                           }).create();
              case DIALOG_PROGRESS:
                  mProgressDialog = new ProgressDialog(BluetoothBrowserActivity.this);
                  mProgressDialog.setTitle(getFTPServerName());
                  mProgressDialog.setMessage(getTransferFileMessage());

                  mProgressDialog.setProgress(0);
                  mProgressDialog.setMax(100);
                  mProgressDialog.setIcon(R.drawable.ic_launcher_bluetooth);
                  mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

                  mProgressDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                                            getText(R.string.cancel_transfer),
                      new DialogInterface.OnClickListener() {
                          public void onClick(DialogInterface dialog, int whichButton) {
                              onCancelTransfer();
                          }
                      }
                  );
                  mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                      public void onCancel(DialogInterface dialog) {
                          onCancelTransfer();
                      }
                  }

                  );

                  return mProgressDialog;
              }
          }
       }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        switch (id) {
        case DIALOG_BACK_DISCONNECT:
         String msg = "Disconnect from Bluetooth Device";
         if(mRemoteFileManagerActivity != null) {
            if(mRemoteFileManagerActivity.mFTPClient != null) {
               msg = "Disconnet from : " + getFTPServerName() + " ?";
            }
         }
            ((AlertDialog) dialog).setTitle(msg);
            break;

        case DIALOG_PROGRESS:
           if (mProgressDialog != null) {
               mProgressDialog.setTitle(getFTPServerName());
               mProgressDialog.setMessage(getTransferFileMessage());
           }
            break;

        }
    }

    /** {@inheritDoc} */
    public void onTabChanged(String tabId) {
        // Because we're using Activities as our tab children, we trigger
        // onWindowFocusChanged() to let them know when they're active. This may
        // seem to duplicate the purpose of onResume(), but it's needed because
        // onResume() can't reliably check if a key-guard is active.
        Activity activity = getLocalActivityManager().getActivity(tabId);
        if (activity != null) {
            activity.onWindowFocusChanged(true);
        }
    }

    public void setRemoteManagerActivity(RemoteFileManagerActivity activity) {
        mRemoteFileManagerActivity = activity;
    }

    public void setLocalManagerActivity(LocalFileManagerActivity activity) {
        mLocalFileManagerActivity = activity;
    }

    /**
     * Initiate Disconnect
     */
    public void initiateServerDisConnect() {
        onCancelTransfer();
        closeTransferProgress();
    }

    /**
     * When a Server Connection is complete, and Tab = "Remote Files", then 1.
     * Get file listing. 2. Install the the broadcast receivers for Bluetooth
     * FTP/OBEX related Intents
     */
    public void onServerConnected() {
        if(mBluetoothObexTransfer != null) {
            mBluetoothObexTransfer.registerCallback(BluetoothBrowserActivity.this);
        }
        closeTransferProgress();
    }

    /**
     * When a Server is disconnected, and Tab = "Remote Files", then 1. Clean up
     * the server listing.
     */
    public void onServerDisconnect() {
        onCancelTransfer();
        closeTransferProgress();
        if(mBluetoothObexTransfer != null) {
            mBluetoothObexTransfer.unregisterCallback(BluetoothBrowserActivity.this);
        }
    }

    /**
     * When a Server is disconnected, and Tab = "Remote Files", then 1. Clean up
     * the server listing.
     */
    public boolean isServerConnected() {
        boolean connected = false;
        if(mRemoteFileManagerActivity != null) {
            if(mRemoteFileManagerActivity.mFTPClient != null) {
                connected = mRemoteFileManagerActivity.mFTPClient.isConnectionActive();
            }
        }
        return connected;
    }

    /**
     * Initiate a Send File
     */
    public void initiateSendFile(File file) {

        try {
         if(mRemoteFileManagerActivity != null) {
            if(mRemoteFileManagerActivity.mFTPClient != null) {
                   String filename = file.getCanonicalPath();
                   Log.i(TAG, "File to Send: " + file.getCanonicalPath());
                   if(true != mRemoteFileManagerActivity.mFTPClient.putFile(filename, file.getName()))
                   {
                       Toast.makeText(
                               this,
                               "Failed to send the file : " + file.getName()
                                       + " to :" + getFTPServerName(), Toast.LENGTH_LONG)
                               .show();
                   }
               }
         }
        } catch (IOException e) {
            Log.e(TAG, "sendFile - getCanonicalPath: IOException", e);
        }

    }

    public void onCancelTransfer() {
        /** Clean up after the cancel */
      closeTransferProgress();
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            mRemoteFileManagerActivity.mFTPClient.CancelTransfer();
         }
        }
    }

    public void updateProgressStats() {
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            boolean transferInProgress = mRemoteFileManagerActivity.mFTPClient.isTransferInProgress();
            if (mProgressDialog != null)
            {
               /* Update the progress bar */
               if (transferInProgress == true) {
                  mProgressDialog.setMessage(getTransferFileMessage());
                  mProgressDialog.setMax((int)mRemoteFileManagerActivity.mFTPClient.getTotalBytes());
                  mProgressDialog.setProgress((int)mRemoteFileManagerActivity.mFTPClient.getDoneBytes());
               }
               else
               {
                  closeTransferProgress();
               }
            }
         }
      }
    }

   /* Routine to provide the name of the file(s) being sent/received.
      Right now, we only support only one transfer at a time
      */
   public String getTransferFileMessage() {
      String szTxString = "";
      String szRxString = "";
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            int txCount = mRemoteFileManagerActivity.mFTPClient.getTxFilesCount();
            if( txCount > 0) {
               if( txCount == 1) {
                 BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) mRemoteFileManagerActivity.mFTPClient.getTxFileItem(0);
                 szTxString = getString(R.string.sending_progress_one, fileInfo.getDisplayName());
               }else {
                 szTxString = getString(R.string.sending_progress_more, (String)(""+txCount));
               }
            }
            int rxCount = mRemoteFileManagerActivity.mFTPClient.getRxFilesCount();
            if( rxCount > 0) {
               if (txCount > 0) {
                 szTxString += ", ";
               }

               if( rxCount == 1) {
                 BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) mRemoteFileManagerActivity.mFTPClient.getRxFileItem(0);
                 szRxString = getString(R.string.get_progress_one, fileInfo.getDisplayName());
               }else {
                 szRxString = getString(R.string.get_progress_more, (String)(""+rxCount));
               }
            }
         }
     }
     return (szTxString + szRxString);
   }

   public void closeTransferProgress() {
      if (mProgressDialog != null) {
         removeDialog(DIALOG_PROGRESS);
         mProgressDialog=null;
      }
   }


    /**
     * Initiate a Send File
     */
    public void putFileStarted() {
        Message msg = new Message();
        msg.what = ADDSENDFILE;
        ProgressHandler.sendMessage(msg);
    }

    /**
     * Initiate a Get File
     */
    public void getFileStarted() {
        Message msg = new Message();
        msg.what = ADDGETFILE;
        ProgressHandler.sendMessage(msg);
    }

    private Handler ProgressHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case FINISHEDID: {
            if(mRemoteFileManagerActivity != null) {
               if(mRemoteFileManagerActivity.mFTPClient != null) {
                  mRemoteFileManagerActivity.mFTPClient.TransferComplete();
               }
            }

            closeTransferProgress();
                Log.i(TAG, "ProgressHandler->FINISHEDID -> progressDone");
                break;
            }

            case UPDATESTATS: {
                updateProgressStats();
                break;
            }
            case ADDSENDFILE:
            case ADDGETFILE:{
                  initiateTransferUI();
                  updateProgressStats();
                  break;
            }
            default:
                break;
            }
            super.handleMessage(msg);
        }
    };

   /* onProgressIndication
    *  This routine will handle the PROGRESS_ACTION broadcast and
    *  updates the progress if the progress was for one of its
    *  file.
    *
    * @param fileName: The name of the file that is being transfered
    * @param totalBytes:  Total number of bytes of the file
    * @param bytesDone:   Number of bytes sent so far
    *
    * @return None.
    */
   public void onProgressIndication(String fileName, int bytesTotal, int bytesDone) {
      Log.i(TAG, "onProgressIndication : fileName " + fileName +
                  " Total : " + bytesTotal  +
                  " done so far : " + bytesDone);
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            mRemoteFileManagerActivity.mFTPClient.onProgressIndication(fileName, bytesTotal, bytesDone);
         }
      }
      updateProgressStats();
   }

   public void onTransmitCompleteIndication(String fileName, boolean success, String errorString) {
      Log.i(TAG, "onTransmitCompleteIndication : fileName " + fileName +
                  " success : " + success  +
                  " errorString : " + errorString);
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            mRemoteFileManagerActivity.mFTPClient.onTransmitCompleteIndication(fileName, success, errorString);
         }
      }
      updateProgressStats();
   }

   public void onReceiveCompleteIndication(String fileName, boolean success) {
      Log.i(TAG, "onReceiveCompleteIndication : fileName " + fileName + " success : " + success);
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            mRemoteFileManagerActivity.mFTPClient.onReceiveCompleteIndication(fileName, success);
         }
      }
      updateProgressStats();
   }


    public boolean isBluetoothEnabled() {
      boolean bluetoothEnabled = false;
      if (mBluetoothObexTransfer != null) {
         bluetoothEnabled = mBluetoothObexTransfer.isBluetoothEnabled();
      }
        return bluetoothEnabled;
    }

    public boolean isBluetoothOBEXEnabled() {
      boolean bluetoothObexEnabled = false;
      if (mBluetoothObexTransfer != null) {
         bluetoothObexEnabled = mBluetoothObexTransfer.isBluetoothOBEXEnabled();
      }
      return bluetoothObexEnabled;
    }

    /* The files received over Bluetooth will be stored in the current Local browsed folder or
       if ALWAYS_USE_BT_RX_FOLDER is set to true, it will always be in a specific folder pointed by
       mFTPClientRecieveFolder
     */
   public String getFTPClientRecieveFolder() {
      String path = mFTPClientRecieveFolder;
      if (ALWAYS_USE_BT_RX_FOLDER == false) {
         if(mLocalFileManagerActivity != null) {
            String dir = mLocalFileManagerActivity.getCurrentDirectory();
            if (dir != null) {
               if (! TextUtils.isEmpty(dir)) {
                  if (dir.endsWith("/")) {
                     path = dir;
                  }
                  else
                  {
                     path = dir+"/";
                  }
               }
            }
         }
      }
      return path;
   }

    public String getFTPServerName() {
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            return mRemoteFileManagerActivity.mFTPClient.getName();
         }
      }
      return "";
    }

    private void initiateTransferUI() {
       if(mRemoteFileManagerActivity != null) {
          if(mRemoteFileManagerActivity.mFTPClient != null) {
             showDialog(DIALOG_PROGRESS);
          }
       }
    }
}
