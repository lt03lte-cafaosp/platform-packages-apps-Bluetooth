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
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.TabActivity; //import android.net.Uri;
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

import java.io.OutputStreamWriter;
import java.io.FileOutputStream;

import com.quic.bluetooth.R;

public class BluetoothBrowserActivity extends TabActivity implements BluetoothObexTransfer.Callback{

    public static final boolean V = true;
    private static final String TAG = "BluetoothBrowserActivity";


    protected static final int FINISHEDID = 0x1337;
    protected static final int UPDATESTATS = 0x1338;
    protected static final int ADDSENDFILE = 0x1339;
    protected static final int ADDGETFILE = 0x1340;

    private static final int DIALOG_BACK_DISCONNECT = 1;

    private static final String LOCAL_TAB_TAG = "LocalFiles";
    private static final String REMOTE_TAB_TAG = "RemoteFiles";
    private RemoteFileManagerActivity mRemoteFileManagerActivity;
    // private LocalFileManagerActivity mLocalFileManagerActivity;

    private static String mFTPClientRecieveFolder = "/sdcard/bluetooth/ReceivedFiles/";


   /**
    * For transfering contact over Bluetooth
    */
   private BluetoothObexTransfer mBluetoothObexTransfer;

    private TabHost mTabHost;
    private TabSpec mLocalTabSpec;
    private TabSpec mRemoteTabSpec;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        // Request for the progress bar to be shown in the title
        requestWindowFeature(Window.FEATURE_PROGRESS);

        setContentView(R.layout.main);
        /**
         * For transfering contact over Bluetooth
         */
        mBluetoothObexTransfer = new BluetoothObexTransfer(BluetoothBrowserActivity.this);
        mTabHost = getTabHost();
        setupLocalFilesTab();
        setupRemoteFilesTab();
        mTabHost.setCurrentTab(1);
        setProgressBarVisibility(false);
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
        switch (id) {
      case DIALOG_BACK_DISCONNECT:
         if(mRemoteFileManagerActivity != null) {
            if(mRemoteFileManagerActivity.mFTPClient != null) {
               String msg = "Disconnet from : " + mRemoteFileManagerActivity.mFTPClient.getName() + " ?";
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
               msg = "Disconnet from : " + mRemoteFileManagerActivity.mFTPClient.getName() + " ?";
            }
         }
            ((AlertDialog) dialog).setTitle(msg);
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
        // mLocalFileManagerActivity = activity;
    }

    /**
     * Initiate Disconnect
     */
    public void initiateServerDisConnect() {
        onCancelTransfer();
        hideTransferProgress();
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
        hideTransferProgress();
    }

    /**
     * When a Server is disconnected, and Tab = "Remote Files", then 1. Clean up
     * the server listing.
     */
    public void onServerDisconnect() {
        onCancelTransfer();
        hideTransferProgress();
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
      hideTransferProgress();
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
            //If Visible or not
            setProgressBarVisibility(transferInProgress);
            if(transferInProgress) {
               int progressNow = mRemoteFileManagerActivity.mFTPClient.getCurrentProgressPercent();
               if ((progressNow >= 0) && (progressNow <= 100)) {
                  // Title progress is in range 0..10000
                  setProgress(100 * progressNow);
               }
            }
         }
      }
    }

    // Worker thread. Should post a task to update the progress bar too
    // Starts immediately.
    public void showTransferProgress() {
      setProgressBarVisibility(true);
    }

   public void hideTransferProgress() {
      setProgressBarVisibility(false);
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
            hideTransferProgress();
                Log.i(TAG, "ProgressHandler->FINISHEDID -> progressDone");
                break;
            }

            case UPDATESTATS: {
                updateProgressStats();
                break;
            }
            case ADDSENDFILE: {
                updateProgressStats();
                break;
            }
            case ADDGETFILE: {
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
         bluetoothEnabled = mBluetoothObexTransfer.isEnabled();
      }
        return bluetoothEnabled;
    }

   public String getFTPClientRecieveFolder() {
      return mFTPClientRecieveFolder;
   }

    public String getFTPServerName() {
      if(mRemoteFileManagerActivity != null) {
         if(mRemoteFileManagerActivity.mFTPClient != null) {
            return mRemoteFileManagerActivity.mFTPClient.getName();
         }
      }
      return "";
    }

}
