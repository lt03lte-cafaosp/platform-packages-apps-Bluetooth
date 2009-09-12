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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

/**
 * BluetoothOppActivity to handle the OPP transfer
 */
public class BluetoothOppActivity extends Activity {
    private static final String TAG = "BluetoothOppActivity";

    private String mAddress;
    private String mFileName;
    private Intent mIntent;

    private static BluetoothDevice mBluetoothDevice = null;
    private static BluetoothObexTransfer mBluetoothObexTransfer = null;
    private static BluetoothOppClient mBluetoothOppClient = null;

    private static final int DIALOG_BT_PROGRESS = 1;
    private static final int DIALOG_BT_PROGRESS_INDETERMINATE = 2;
    private Uri mUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!SystemProperties.getBoolean("ro.qualcomm.proprietary_obex", false)) {
           Log.e(TAG,"Error: Qualcomm Bluetooth OBEX not enabled ");
           finish();
           return;
        }

        Intent mIntent = getIntent();
        mUri = mIntent.getData();
        String action = mIntent.getAction();
        String profile = mIntent.getStringExtra(BluetoothAppIntent.PROFILE);
        String serverAddress = mIntent.getStringExtra(BluetoothDevicePicker.ADDRESS);
        String serverName = mIntent.getStringExtra(BluetoothDevicePicker.NAME);

        boolean parametersOk=true;
        if (!mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_BUSINESS_CARD)
         && !mIntent.getAction().equals(BluetoothAppIntent.ACTION_PULL_BUSINESS_CARD)
           && !mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_FILE))
        {
            Log.e(TAG,
                  "Error: this activity may be started only with intents " +
                  BluetoothAppIntent.ACTION_PUSH_BUSINESS_CARD +
                  " or "+ BluetoothAppIntent.ACTION_PULL_BUSINESS_CARD+
                  " or "+ BluetoothAppIntent.ACTION_PUSH_FILE);
            parametersOk=false;
        }

        /* For OPP Push operations URI is mandatory parameter */
        if ( (mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_BUSINESS_CARD)
           || mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_FILE))
           && (mUri == null))
        {
         Log.e(TAG,
               "Error: Contact or Media Uri has to be specified to send it to the Remote device");
          parametersOk=false;
        }
        if (TextUtils.isEmpty(serverAddress)) {
           Log.e(TAG, "Error: OPP Server Address not specified");
           parametersOk=false;
        }
        if(parametersOk==false)
        {
           finish();
           return;
        }
        if (mUri != null) {
           Log.v(TAG, "onCreate : mUri - "+ mUri.toString());
        }
        Log.v(TAG, "onCreate : action - "+ action);
        Log.v(TAG, "onCreate : profile - "+ profile);
        Log.v(TAG, "onCreate : serverAddress - "+ serverAddress);
        Log.v(TAG, "onCreate : serverName - "+ serverName);

      if(parametersOk==true)
      {
         parametersOk=false;
         mBluetoothOppClient = new BluetoothOppClient(this, mTransferProgressCallback);
         mBluetoothObexTransfer = new BluetoothObexTransfer(this );
         mBluetoothObexTransfer.registerCallback(mBluetoothOppClient);
         if (mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_BUSINESS_CARD))
         {
            mBluetoothOppClient.sendContact(mUri, serverAddress);
            parametersOk=true;
         }
         else if (mIntent.getAction().equals(BluetoothAppIntent.ACTION_PULL_BUSINESS_CARD))
         {
            mBluetoothOppClient.getContact(serverAddress);
            parametersOk=true;
         }
         else if (mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_FILE))
         {
              mBluetoothOppClient.sendMedia(mUri, serverAddress, BluetoothOppActivity.this);
              parametersOk=true;
         }
      }
      if(parametersOk==false)
      {
         finish();
         return;
      }
    }

    /**
     * Called when the activity is being destroyed
     */
    @Override
    public void onDestroy()
    {
       super.onDestroy();
       if(mBluetoothOppClient != null){
          mBluetoothOppClient.Cleanup();
       }

       if(mBluetoothObexTransfer != null){
          mBluetoothObexTransfer.onDestroy();
       }

       mBluetoothObexTransfer=null;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_BT_PROGRESS:
        case DIALOG_BT_PROGRESS_INDETERMINATE:
            return createBluetoothProgressDialog(id);
        }
        return null;
    }

    /*************************************
      Bluetooth transfer related UI - Start
      ************************************ */
    /** Dialog that displays the progress of the Put/Get */
    private ProgressDialog mProgressDialog=null;
    private int mProgressDlgId ;
    private BluetoothOppClient.TransferProgressCallback mTransferProgressCallback = new BluetoothOppClient.TransferProgressCallback () {

       public void onStart(boolean showCancelProgress) {
          if(mBluetoothObexTransfer != null)
          {
             if (showCancelProgress) {
                showDialog(DIALOG_BT_PROGRESS);
             }
             else
             {
                showDialog(DIALOG_BT_PROGRESS_INDETERMINATE);
             }
             if(mProgressHandler != null) {
                mProgressHandler.sendEmptyMessage(0);
             }
          }
       }

       public void onUpdate() {
           if(mProgressHandler != null) {
               mProgressDialog.setTitle(mBluetoothOppClient.getActiveRemoteOPPServerName());
               mProgressDialog.setMessage(mBluetoothOppClient.getTransferFileMessage());
               if (! mProgressDialog.isIndeterminate()) {
                  mProgressDialog.setMax((int)mBluetoothOppClient.getTotalBytes());
                  mProgressDialog.setProgress((int)mBluetoothOppClient.getDoneBytes());
               }
           }
       }

       public void onComplete() {
           if(mProgressHandler != null) {
              mProgressHandler.sendEmptyMessage(0);
           }
       }
    };

    private Handler mProgressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (mProgressDialog != null) {
               if(mBluetoothOppClient.isTransferinProgress()) {
                  mProgressHandler.sendEmptyMessageDelayed(0, 3000);
               }
               else
               {
                  removeDialog(mProgressDlgId);
                  mProgressDialog=null;
                  finish();
               }
            }
        }
    };

    private Dialog createBluetoothProgressDialog(int id) {
       Dialog dlg = null;
       if(mBluetoothOppClient != null)
       {
           mProgressDlgId = id;
          /* If the transfer completed even before the progress dialog is launched,
             no need to open the transfer progress
             */
          if(mBluetoothOppClient.isTransferinProgress()) {
             mProgressDialog = new ProgressDialog(BluetoothOppActivity.this);

             mProgressDialog.setTitle(mBluetoothOppClient.getActiveRemoteOPPServerName());
             mProgressDialog.setMessage(mBluetoothOppClient.getTransferFileMessage());
             mProgressDialog.setIcon(R.drawable.ic_launcher_bluetooth);
             if(mProgressDlgId == DIALOG_BT_PROGRESS)
             {
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);

                mProgressDialog.setMax((int)mBluetoothOppClient.getTotalBytes());
                mProgressDialog.setProgress((int)mBluetoothOppClient.getDoneBytes());
                mProgressDialog.setButton(DialogInterface.BUTTON_POSITIVE,
                                          getText(R.string.cancel_transfer),
                    new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                      if(mBluetoothOppClient != null)
                      {
                         mBluetoothOppClient.CancelTransfer();
                      }
                    }
                }
                );
                mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                   public void onCancel(DialogInterface dialog) {
                      if(mBluetoothOppClient != null)
                      {
                         mBluetoothOppClient.CancelTransfer();
                      }
                   }
                }
                );
             }
             else {
                mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
             }
          }
       }
       return mProgressDialog;
    }
}
