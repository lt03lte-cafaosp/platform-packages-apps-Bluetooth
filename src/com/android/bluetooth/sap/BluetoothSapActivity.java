/*
 * Copyright (c) 2011-2012, Qualcomm, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.sap;

import com.android.bluetooth.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.Preference;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.text.InputFilter.LengthFilter;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

public class BluetoothSapActivity extends AlertActivity implements
        DialogInterface.OnClickListener, Preference.OnPreferenceChangeListener, TextWatcher {
    private static final String TAG = "BluetoothSapActivity";

    private static final int DIALOG_YES_NO_CONNECT = 1;

    private static final String KEY_USER_TIMEOUT = "user_timeout";

    private View mView;

    private EditText mKeyView;

    private TextView messageView;

    private int mCurrentDialog;

    private Button mOkButton;

    private CheckBox mAlwaysAllowed;

    private boolean mTimeout = false;

    private boolean mAlwaysAllowedValue = true;

    private static final int DISMISS_TIMEOUT_DIALOG = 0;

    private static final int DISMISS_TIMEOUT_DIALOG_VALUE = 2000;

    public static final String USER_CONFIRM_TIMEOUT_ACTION =
            "com.android.bluetooth.sap.userconfirmtimeout";
    public static final String EXTRA_ALWAYS_ALLOWED = "com.android.bluetooth.sap.alwaysallowed";
    public static final int USER_TIMEOUT = 2;
    public static final String ACCESS_REQUEST_ACTION = "com.android.bluetooth.sap.accessrequest";

    public static final String ACCESS_ALLOWED_ACTION = "com.android.bluetooth.sap.accessallowed";

    public static final String ACCESS_DISALLOWED_ACTION =
            "com.android.bluetooth.sap.accessdisallowed";

    public static final int USER_CONFIRM_TIMEOUT_VALUE = 30000;

    private String mRemoteName = "Unknown";
    private String mRemoteAddress = "00:00:00:00:00:00";

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!USER_CONFIRM_TIMEOUT_ACTION.equals(intent.getAction())) {
                return;
            }
            onTimeout();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent i = getIntent();
        String action = i.getAction();
        mRemoteName = i.getStringExtra("name");
        mRemoteAddress = i.getStringExtra("address");
        if (mRemoteName == null)
            mRemoteName = "Unknown";
        if (action.equals(ACCESS_REQUEST_ACTION)) {
            showSapDialog(DIALOG_YES_NO_CONNECT);
            mCurrentDialog = DIALOG_YES_NO_CONNECT;
        } else {
            Log.e(TAG, "Error: this activity may be started only with intent "
                    + "SAP_ACCESS_REQUEST");
            finish();
        }
        registerReceiver(mReceiver, new IntentFilter(USER_CONFIRM_TIMEOUT_ACTION));
    }

    private void showSapDialog(int id) {
        final AlertController.AlertParams p = mAlertParams;
        switch (id) {
            case DIALOG_YES_NO_CONNECT:
                p.mIconId = android.R.drawable.ic_dialog_info;
                p.mTitle = getString(R.string.sap_acceptance_dialog_header);
                p.mView = createView(DIALOG_YES_NO_CONNECT);
                p.mPositiveButtonText = getString(android.R.string.yes);
                p.mPositiveButtonListener = this;
                p.mNegativeButtonText = getString(android.R.string.no);
                p.mNegativeButtonListener = this;
                mOkButton = mAlert.getButton(DialogInterface.BUTTON_POSITIVE);
                setupAlert();
                break;
            default:
                break;
        }
    }

    private String createDisplayText(final int id) {
        switch (id) {
            case DIALOG_YES_NO_CONNECT:
                String mMessage1 = getString(R.string.sap_acceptance_dialog_title, mRemoteName,
                        mRemoteName);
                return mMessage1;
            default:
                return null;
        }
    }

    private View createView(final int id) {
        switch (id) {
            case DIALOG_YES_NO_CONNECT:
                mView = getLayoutInflater().inflate(R.layout.access, null);
                messageView = (TextView)mView.findViewById(R.id.message);
                messageView.setText(createDisplayText(id));
                mAlwaysAllowed = (CheckBox)mView.findViewById(R.id.alwaysallowed);
                mAlwaysAllowed.setChecked(true);
                mAlwaysAllowed.setOnCheckedChangeListener(new OnCheckedChangeListener() {
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            mAlwaysAllowedValue = true;
                        } else {
                            mAlwaysAllowedValue = false;
                        }
                    }
                });
                return mView;
            default:
                return null;
        }
    }

    private void onPositive() {
        Log.i(TAG, "onPositive" + mTimeout);
        if (!mTimeout) {
            if (mCurrentDialog == DIALOG_YES_NO_CONNECT) {
                Log.i(TAG, "broadcasting access allowed");
                sendIntentToReceiver(ACCESS_ALLOWED_ACTION,
                                     EXTRA_ALWAYS_ALLOWED, mAlwaysAllowedValue);
            }
        }
        mTimeout = false;
        finish();
    }

    private void onNegative() {
        if (mCurrentDialog == DIALOG_YES_NO_CONNECT) {
            sendIntentToReceiver(ACCESS_DISALLOWED_ACTION, EXTRA_ALWAYS_ALLOWED, false);
        }
        finish();
    }


    private void sendIntentToReceiver(final String intentName, final String extraName,
            final boolean extraValue) {
        Intent intent = new Intent(intentName);
        if (extraName != null) {
            intent.putExtra(extraName, extraValue);
        }
        intent.putExtra("address", mRemoteAddress);
        sendBroadcast(intent);
    }

    public void onClick(DialogInterface dialog, int which) {
        Log.i(TAG, "SAP onClick" + which);
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                Log.i(TAG, "Calling onPositive");
                onPositive();
                break;

            case DialogInterface.BUTTON_NEGATIVE:
                Log.i(TAG, "Calling onNegative");
                onNegative();
                break;
            default:
                break;
        }
    }

    private void onTimeout() {
        mTimeout = true;
        if (mCurrentDialog == DIALOG_YES_NO_CONNECT) {
            messageView.setText(getString(R.string.sap_acceptance_timeout_message,
                    mRemoteName));
            mAlert.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
            mAlwaysAllowed.setVisibility(View.GONE);
            mAlwaysAllowed.clearFocus();
        }

        mTimeoutHandler.sendMessageDelayed(mTimeoutHandler.obtainMessage(DISMISS_TIMEOUT_DIALOG),
                DISMISS_TIMEOUT_DIALOG_VALUE);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mTimeout = savedInstanceState.getBoolean(KEY_USER_TIMEOUT);
        Log.i(TAG, "onRestoreInstanceState() mTimeout: " + mTimeout);
        if (mTimeout) {
            onTimeout();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_USER_TIMEOUT, mTimeout);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
    }

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return true;
    }

    public void beforeTextChanged(CharSequence s, int start, int before, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(android.text.Editable s) {
        if (s.length() > 0) {
            mOkButton.setEnabled(true);
        }
    }

    private final Handler mTimeoutHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case DISMISS_TIMEOUT_DIALOG:
                    Log.i(TAG, "Received DISMISS_TIMEOUT_DIALOG msg.");
                    finish();
                    break;
                default:
                    break;
            }
        }
    };
}
