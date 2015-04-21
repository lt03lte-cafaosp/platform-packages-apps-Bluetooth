/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.bluetooth.hfp;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionManager.OnSubscriptionsChangedListener;
import android.util.Log;
import android.bluetooth.BluetoothDevice;


// Note:
// All methods in this class are not thread safe, donot call them from
// multiple threads. Call them from the HeadsetPhoneStateMachine message
// handler only.
class HeadsetPhoneState {
    private static final String TAG = "HeadsetPhoneState";

    private HeadsetStateMachine mStateMachine;
    private TelephonyManager mTelephonyManager;
    private ServiceState mServiceState;

    // HFP 1.6 CIND service
    private int mService = HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE;

    // Number of active (foreground) calls
    private int mNumActive = 0;

    // Current Call Setup State
    private int mCallState = HeadsetHalConstants.CALL_STATE_IDLE;

    // Number of held (background) calls
    private int mNumHeld = 0;

    // Phone Number
    private String mNumber;

    // Type of Phone Number
    private int mType = 0;

    // HFP 1.6 CIND signal
    private int mSignal = 0;

    // HFP 1.6 CIND roam
    private int mRoam = HeadsetHalConstants.SERVICE_TYPE_HOME;

    // HFP 1.6 CIND battchg
    private int mBatteryCharge = 0;

    private int mSpeakerVolume = 0;

    private int mMicVolume = 0;

    private boolean mListening = false;

    // when HFP Service Level Connection is established
    private boolean mSlcReady = false;

    private Context mContext = null;

    private PhoneStateListener mPhoneStateListener = null;

    private SubscriptionManager mSubMgr;

    private OnSubscriptionsChangedListener mOnSubscriptionsChangedListener =
            new OnSubscriptionsChangedListener() {
        @Override
        public void onSubscriptionsChanged() {
            listenForPhoneState(false);
            listenForPhoneState(true);
        }
    };


    HeadsetPhoneState(Context context, HeadsetStateMachine stateMachine) {
        mStateMachine = stateMachine;
        mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        mContext = context;

        // Register for SubscriptionInfo list changes which is guaranteed
        // to invoke onSubscriptionInfoChanged and which in turns calls
        // loadInBackgroud.
        mSubMgr = SubscriptionManager.from(mContext);
        mSubMgr.addOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);
    }

    public void cleanup() {
        listenForPhoneState(false);
        mSubMgr.removeOnSubscriptionsChangedListener(mOnSubscriptionsChangedListener);

        mTelephonyManager = null;
        mStateMachine = null;
    }

    void listenForPhoneState(boolean start) {

        mSlcReady = start;

        if (start) {
            startListenForPhoneState();
        } else {
            stopListenForPhoneState();
        }

    }

    private void startListenForPhoneState() {
        if (!mListening && mSlcReady) {

            int subId = SubscriptionManager.getDefaultSubId();

            if (SubscriptionManager.isValidSubscriptionId(subId)) {
                mPhoneStateListener = getPhoneStateListener(subId);

                try {
                    mTelephonyManager.listen(mPhoneStateListener,
                                         PhoneStateListener.LISTEN_SERVICE_STATE |
                                         PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
                    mListening = true;
                } catch (NullPointerException npe) {
                    // Handle case where Telephoneymanager crashes
                    // and context becomes NULL
                    Log.e(TAG, "NullPointerException for Telephonymanager while startListen", npe);
                }

            }
        }
    }

    private void stopListenForPhoneState() {
        if (mListening) {
            try {
                mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            } catch (NullPointerException npe) {
                // Handle case where Telephoneymanager crashes
                // and context becomes NULL
                Log.e(TAG, "NullPointerException for Telephonymanager while stopListen", npe);
            }
            mListening = false;
        }
    }

    int getService() {
        return mService;
    }

    int getNumActiveCall() {
        return mNumActive;
    }

    void setNumActiveCall(int numActive) {
        mNumActive = numActive;
    }

    int getCallState() {
        return mCallState;
    }

    void setCallState(int callState) {
        mCallState = callState;
    }

    int getNumHeldCall() {
        return mNumHeld;
    }

    void setNumHeldCall(int numHeldCall) {
        mNumHeld = numHeldCall;
    }

    void setNumber(String mNumberCall ) {
        mNumber = mNumberCall;
    }

    String getNumber()
    {
        return mNumber;
    }

    void setType(int mTypeCall) {
        mType = mTypeCall;
    }

    int getType() {
        return mType;
    }

    int getSignal() {
        return mSignal;
    }

    int getRoam() {
        return mRoam;
    }

    void setRoam(int roam) {
        if (mRoam != roam) {
            mRoam = roam;
            sendDeviceStateChanged();
        }
    }

    void setBatteryCharge(int batteryLevel) {
        if (mBatteryCharge != batteryLevel) {
            mBatteryCharge = batteryLevel;
            sendDeviceStateChanged();
        }
    }

    int getBatteryCharge() {
        return mBatteryCharge;
    }

    void setSpeakerVolume(int volume) {
        mSpeakerVolume = volume;
    }

    int getSpeakerVolume() {
        return mSpeakerVolume;
    }

    void setMicVolume(int volume) {
        mMicVolume = volume;
    }

    int getMicVolume() {
        return mMicVolume;
    }

    boolean isInCall() {
        return (mNumActive >= 1);
    }

    void sendDeviceStateChanged()
    {
        // When out of service, send signal strength as 0. Some devices don't
        // use the service indicator, but only the signal indicator
        int signal = mService == HeadsetHalConstants.NETWORK_STATE_AVAILABLE ? mSignal : 0;

        Log.d(TAG, "sendDeviceStateChanged. mService="+ mService +
                   " mSignal=" + signal +" mRoam="+ mRoam +
                   " mBatteryCharge=" + mBatteryCharge);
        HeadsetStateMachine sm = mStateMachine;
        if (sm != null) {
            sm.sendMessage(HeadsetStateMachine.DEVICE_STATE_CHANGED,
                new HeadsetDeviceState(mService, mRoam, signal, mBatteryCharge));
        }
    }

    private PhoneStateListener getPhoneStateListener(int subId) {
        PhoneStateListener mPhoneStateListener = new PhoneStateListener(subId) {
            @Override
            public void onServiceStateChanged(ServiceState serviceState) {

                mServiceState = serviceState;
                mService = (serviceState.getState() == ServiceState.STATE_IN_SERVICE) ?
                    HeadsetHalConstants.NETWORK_STATE_AVAILABLE :
                    HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE;
                setRoam(serviceState.getRoaming() ? HeadsetHalConstants.SERVICE_TYPE_ROAMING
                                                  : HeadsetHalConstants.SERVICE_TYPE_HOME);
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                int prevSignal = mSignal;
                if (mService == HeadsetHalConstants.NETWORK_STATE_NOT_AVAILABLE)
                    mSignal = 0;
                else
                    mSignal = (signalStrength.getLevel() == 4) ? 5 : signalStrength.getLevel();
                // network signal strength is scaled to BT 1-5 levels.
                // This results in a lot of duplicate messages, hence this check
                if (prevSignal != mSignal)
                    sendDeviceStateChanged();
            }
        };
        return mPhoneStateListener;
    }
}

class HeadsetDeviceState {
    int mService;
    int mRoam;
    int mSignal;
    int mBatteryCharge;

    HeadsetDeviceState(int service, int roam, int signal, int batteryCharge) {
        mService = service;
        mRoam = roam;
        mSignal = signal;
        mBatteryCharge = batteryCharge;
    }
}

class HeadsetCallState {
    int mNumActive;
    int mNumHeld;
    int mCallState;
    String mNumber;
    int mType;

    public HeadsetCallState(int numActive, int numHeld, int callState, String number, int type) {
        mNumActive = numActive;
        mNumHeld = numHeld;
        mCallState = callState;
        mNumber = number;
        mType = type;
    }
}

class HeadsetClccResponse {
    int mIndex;
    int mDirection;
    int mStatus;
    int mMode;
    boolean mMpty;
    String mNumber;
    int mType;

    public HeadsetClccResponse(int index, int direction, int status, int mode, boolean mpty,
                               String number, int type) {
        mIndex = index;
        mDirection = direction;
        mStatus = status;
        mMode = mode;
        mMpty = mpty;
        mNumber = number;
        mType = type;
    }
}

class HeadsetVendorSpecificResultCode {
    BluetoothDevice mDevice;
    String mCommand;
    String mArg;

    public HeadsetVendorSpecificResultCode(BluetoothDevice device, String command, String arg) {
        mDevice = device;
        mCommand = command;
        mArg = arg;
    }
}
