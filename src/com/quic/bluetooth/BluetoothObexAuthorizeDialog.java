/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothIntent;
import android.bluetooth.obex.BluetoothObexIntent;
import android.bluetooth.obex.BluetoothOpp;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.quic.bluetooth.R;
import java.io.File;

/**
 * BluetoothPinDialog asks the user to enter a PIN for pairing with a remote
 * Bluetooth device. It is an activity that appears as a dialog.
 */
public class BluetoothObexAuthorizeDialog extends AlertActivity implements DialogInterface.OnClickListener {
    private static final String TAG = "BluetoothObexAuthorizeDialog";

    private LocalBluetoothManager mLocalManager;
    private String mAddress;
    private String mFileName;
    private String mFileDisplayName;
    private String mObjectType;
    private Intent mIntent;

    private BluetoothOpp mBluetoothOPP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (!intent.getAction().equals(BluetoothObexIntent.AUTHORIZE_ACTION))
        {
            Log.e(TAG,
                  "Error: this activity may be started only with intent " +
                  BluetoothObexIntent.AUTHORIZE_ACTION);
            finish();
        }

        mLocalManager = LocalBluetoothManager.getInstance(this);
        mAddress = intent.getStringExtra(BluetoothObexIntent.ADDRESS);
        mFileName = intent.getStringExtra(BluetoothObexIntent.OBJECT_FILENAME);
        mObjectType = intent.getStringExtra(BluetoothObexIntent.OBJECT_TYPE);

        File f = new File (mFileName);
        if (f != null) {
           mFileDisplayName = f.getName();
        } else {
           mFileDisplayName = mFileName;
        }
        Log.v(TAG, "Extra Data: " + mAddress + " " + mFileName + " " + mObjectType);

        // Set up the "dialog"
        final AlertController.AlertParams p = mAlertParams;
        p.mIconId = android.R.drawable.ic_dialog_info;
        p.mTitle = getString(R.string.bluetooth_obex_authorize);
        p.mView = createView();
        p.mPositiveButtonText = getString(android.R.string.ok);
        p.mPositiveButtonListener = this;
        p.mNegativeButtonText = getString(android.R.string.cancel);
        p.mNegativeButtonListener = this;
        setupAlert();

        mBluetoothOPP = new BluetoothOpp();
    }

    private View createView() {
        View view = getLayoutInflater().inflate(R.layout.bluetooth_obex_authorize, null);
        String deviceName = mLocalManager.getLocalDeviceManager().getName(mAddress);
        //String deviceName = "Bluetooth Device";
        TextView messageView = (TextView) view.findViewById(R.id.message);
        if(mFileDisplayName == null) {
            Log.e(TAG, "Error: mObjectType null >> " + mAddress + " " + mFileDisplayName);
        }
        else {
           messageView.setText(getString(R.string.bluetooth_authorize_receive_file_msg, deviceName, mFileDisplayName));
        }

        return view;
    }

    private void onAccept() {
        Toast.makeText(BluetoothObexAuthorizeDialog.this, "Accept " + mFileDisplayName ,Toast.LENGTH_SHORT).show();
        mBluetoothOPP.obexAuthorizeComplete(mFileDisplayName, true, mFileName);
    }

    private void onReject() {
        Toast.makeText(BluetoothObexAuthorizeDialog.this, "Reject " + mFileDisplayName ,Toast.LENGTH_SHORT).show();
        mBluetoothOPP.obexAuthorizeComplete(mFileDisplayName, false, mFileName);
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                onAccept();
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                onReject();
                break;
        }
    }
}
