/*
 * Copyright (C) 2014 The Android Open Source Project
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

/**
 * Bluetooth A2dp StateMachine
 *                      (Disconnected)
 *                           |    ^
 *                   CONNECT |    | DISCONNECTED
 *                           V    |
 *                         (Pending)
 *                           |    ^
 *                 CONNECTED |    | CONNECT
 *                           V    |
 *                        (Connected)
 */
package com.android.bluetooth.a2dp;

import android.bluetooth.BluetoothA2dpSink;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetooth;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioDevicePort;
import android.media.AudioPatch;
import android.media.AudioSystem;
import android.media.AudioPortConfig;
import android.media.AudioPort;

import android.os.Handler;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.content.Intent;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.ParcelUuid;
import android.util.Log;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ProfileService;
import com.android.bluetooth.avrcp.AvrcpControllerService;
import com.android.internal.util.IState;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Set;

final class A2dpSinkStateMachine extends StateMachine {
    private static final boolean DBG = false;

    static final int CONNECT = 1;
    static final int DISCONNECT = 2;
    final private static int SET_RENDERING_VOL = 3;
    final private static int EVENT_TYPE_REQUEST_AUDIO_FOCUS = 4;
    private static final int STACK_EVENT = 101;
    private static final int CONNECT_TIMEOUT = 201;

    private static final int IS_INVALID_DEVICE = 0;
    private static final int IS_VALID_DEVICE = 1;
    public static final int AVRC_ID_PLAY = 0x44;
    public static final int AVRC_ID_PAUSE = 0x46;
    public static final int KEY_STATE_PRESSED = 0;
    public static final int KEY_STATE_RELEASED = 1;

    private Disconnected mDisconnected;
    private Pending mPending;
    private Connected mConnected;

    private A2dpSinkService mService;
    private Context mContext;
    private BluetoothAdapter mAdapter;
    private final AudioManager mAudioManager;
    private IntentBroadcastHandler mIntentBroadcastHandler;
    private final WakeLock mWakeLock;

    private static final int MSG_CONNECTION_STATE_CHANGED = 0;

    private static final int AUDIO_FOCUS_LOSS = 0;
    private static final int AUDIO_FOCUS_GAIN = 1;
    private static final int AUDIO_FOCUS_LOSS_TRANSIENT = 2;
    private static final int AUDIO_FOCUS_LOSS_CAN_DUCK = 3;
    private static final int AUDIO_FOCUS_REQUEST_MESSAGE_DELAYED = 500;

    private int mAudioFocusAcquired = AUDIO_FOCUS_LOSS;

    private static final int maxA2dpSinkConnections = 1;

    private final Object mLockForVolUpdate = new Object();
    private static int mCurVolGain = 1;
    private static int MAX_VOL = 40;
    private static int mCurrentStreamType = AudioManager.STREAM_RING;

    private final Object mLockForPatch = new Object();
    private AudioDevicePort mInPortA2dpSink;
    private AudioDevicePort mOutPortSpeaker;
    private AudioPatch mA2dpSinkAudioPatch;
    private String volSetParamString = "ext_hw_plugin_msg_type=3;" + "ext_hw_plugin_usecase=0;" +
                                        "ext_hw_plugin_channel_mask=1;" + "ext_hw_plugin_gain=";
    private String volSetMuteParamString = "ext_hw_plugin_msg_type=4;" + "ext_hw_plugin_usecase=0;"
                                  +"ext_hw_plugin_channel_mask=1;" + "ext_hw_plugin_mute_flag=1";
    private String volSetUnMuteParamString = "ext_hw_plugin_msg_type=4;" +
        "ext_hw_plugin_usecase=0;" +"ext_hw_plugin_channel_mask=1;" + "ext_hw_plugin_mute_flag=0";

    // mCurrentDevice is the device connected before the state changes
    // mTargetDevice is the device to be connected
    // mIncomingDevice is the device connecting to us, valid only in Pending state
    //                when mIncomingDevice is not null, both mCurrentDevice
    //                  and mTargetDevice are null
    //                when either mCurrentDevice or mTargetDevice is not null,
    //                  mIncomingDevice is null
    // Stable states
    //   No connection, Disconnected state
    //                  both mCurrentDevice and mTargetDevice are null
    //   Connected, Connected state
    //              mCurrentDevice is not null, mTargetDevice is null
    // Interim states
    //   Connecting to a device, Pending
    //                           mCurrentDevice is null, mTargetDevice is not null
    //   Disconnecting device, Connecting to new device
    //     Pending
    //     Both mCurrentDevice and mTargetDevice are not null
    //   Disconnecting device Pending
    //                        mCurrentDevice is not null, mTargetDevice is null
    //   Incoming connections Pending
    //                        Both mCurrentDevice and mTargetDevice are null
    private BluetoothDevice mCurrentDevice = null;
    private BluetoothDevice mTargetDevice = null;
    private BluetoothDevice mIncomingDevice = null;
    private BluetoothDevice mPlayingDevice = null;

    private final HashMap<BluetoothDevice,BluetoothAudioConfig> mAudioConfigs
            = new HashMap<BluetoothDevice,BluetoothAudioConfig>();

    static {
        classInitNative();
    }

    private A2dpSinkStateMachine(A2dpSinkService svc, Context context) {
        super("A2dpSinkStateMachine");
        mService = svc;
        mContext = context;
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        initNative();

        mDisconnected = new Disconnected();
        mPending = new Pending();
        mConnected = new Connected();

        addState(mDisconnected);
        addState(mPending);
        addState(mConnected);

        setInitialState(mDisconnected);

        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BluetoothA2dpSinkService");

        mIntentBroadcastHandler = new IntentBroadcastHandler();
        IntentFilter filter = new IntentFilter("com.android.music.musicservicecommand");
        filter.addAction(AudioManager.VOLUME_CHANGED_ACTION);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mContext.registerReceiver(mA2dpReceiver, filter);
        mAudioManager.registerAudioPortUpdateListener(mAudioPortListener);
        mCurVolGain = mAudioManager.getStreamVolume(mCurrentStreamType) * getGainMultiplier();
        mCurVolGain = (mCurVolGain > MAX_VOL) ? MAX_VOL:mCurVolGain;
        mCurVolGain = (mCurVolGain < 1)? 1:mCurVolGain;
    }
    private int getGainMultiplier() {
        return (MAX_VOL/mAudioManager.getStreamMaxVolume(mCurrentStreamType));
    }
    private void releasePatch() {
        synchronized (mLockForPatch) {
            log("releaseAudioPatch AudioPatch = " + mA2dpSinkAudioPatch);
            if(mA2dpSinkAudioPatch != null) {
                mAudioManager.releaseAudioPatch(mA2dpSinkAudioPatch);
                mA2dpSinkAudioPatch = null;
            }
        }
    }
    private void patchPorts() {
        synchronized (mLockForPatch) {
            log("patchPorts : mA2dpSinkAudioPatch: " + mA2dpSinkAudioPatch +
                " mPlayingDevice " + mPlayingDevice + " mOutPortSpeaker " + mOutPortSpeaker);
            if((mA2dpSinkAudioPatch == null) && (mPlayingDevice != null) &&
               (mOutPortSpeaker != null) && (mInPortA2dpSink != null)) {
                if((mAudioConfigs == null)||(!mAudioConfigs.containsKey(mPlayingDevice))) {
                    log(" AudioConfigs not yet received, returning");
                    return;
                }
                int sampleRate = getAudioConfig(mPlayingDevice).getSampleRate();
                int channelMask = getAudioConfig(mPlayingDevice).getChannelConfig();
                int format = getAudioConfig(mPlayingDevice).getAudioFormat();

                AudioPortConfig sourcePortArray[] =
                    {mInPortA2dpSink.buildConfig(sampleRate, channelMask, format, null)};
                AudioPortConfig sinkPortArray[] =
                    {mOutPortSpeaker.buildConfig(sampleRate, channelMask, format, null)};
                AudioPatch patchPortArray[] = {null};
                /*  broadCast Audio State */
                broadcastAudioState(mPlayingDevice, BluetoothA2dpSink.STATE_PLAYING,
                                                 BluetoothA2dpSink.STATE_NOT_PLAYING);

                int ret = mAudioManager.createAudioPatch(patchPortArray, sourcePortArray,
                                                                               sinkPortArray);
                if (ret == 0) {
                    mA2dpSinkAudioPatch = patchPortArray[0];
                    log("PatchCreated success: " + ret + " mA2dpSinkAudioPatch: "
                                                                         + mA2dpSinkAudioPatch);
                    setRenderingVolAfterStart();
                } else {
                    log("PatchCreated failed returned: " + ret);
                }
            }
        }
    }

    private final AudioManager.OnAudioPortUpdateListener mAudioPortListener =
                                                new AudioManager.OnAudioPortUpdateListener(){
        public void onAudioPortListUpdate(AudioPort[] portList) {
            synchronized (mLockForPatch){
                log("onAudioPortListUpdate");
                mOutPortSpeaker = null;
                mInPortA2dpSink = null;

                for (int i = 0; i < portList.length; i++) {
                    AudioPort port = portList[i];
                    if(port instanceof AudioDevicePort) {
                        AudioDevicePort devicePort = (AudioDevicePort)port;
                        if(devicePort.type() == AudioSystem.DEVICE_OUT_SPEAKER) {
                            log("Updating Speaker Port");
                            mOutPortSpeaker = devicePort;
                        } else if(devicePort.type() == AudioSystem.DEVICE_IN_BLUETOOTH_A2DP) {
                            log("Updating In Port A2DP Sink");
                            mInPortA2dpSink = devicePort;
                            /* Check if we still have focus */
                            if ((mAudioFocusAcquired == AUDIO_FOCUS_GAIN) &&
                                (mInPortA2dpSink != null) && (mA2dpSinkAudioPatch == null)) {
                                /* This is the case of Port available
                                 * later than focus acquired. Try patching ports now
                                 */
                                 log(" Sink Port updated, but patch not made");
                                 patchPorts();
                            }
                        }
                    }
                }
            }
        }

        public void onAudioPatchListUpdate(AudioPatch[] patchList){
            log("onAudioPatchListUpdate");
            for(int i = 0; i < patchList.length; i++) {
                log("Patch List " + i +" : "+ patchList[i]);
            }
        }

        public void onServiceDied() {
            log(" Service Died");
        }
    };

    static A2dpSinkStateMachine make(A2dpSinkService svc, Context context) {
        Log.d("A2dpSinkStateMachine", "make");
        A2dpSinkStateMachine a2dpSm = new A2dpSinkStateMachine(svc, context);
        a2dpSm.start();
        return a2dpSm;
    }

    public void doQuit() {
        quitNow();
    }

    public void cleanup() {
        releasePatch();
        cleanupNative();
        if (mContext != null)
           mContext.unregisterReceiver(mA2dpReceiver);
        mAudioConfigs.clear();
        mAudioManager.unregisterAudioPortUpdateListener(mAudioPortListener);
    }

    public void dump(StringBuilder sb) {
        ProfileService.println(sb, "mCurrentDevice: " + mCurrentDevice);
        ProfileService.println(sb, "mTargetDevice: " + mTargetDevice);
        ProfileService.println(sb, "mIncomingDevice: " + mIncomingDevice);
        ProfileService.println(sb, "StateMachine: " + this.toString());
    }

    private class Disconnected extends State {
        @Override
        public void enter() {
            log("Enter Disconnected: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Disconnected process message: " + message.what);
            if (mCurrentDevice != null || mTargetDevice != null  || mIncomingDevice != null) {
                loge("ERROR: current, target, or mIncomingDevice not null in Disconnected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                   BluetoothProfile.STATE_DISCONNECTED);

                    if (!connectA2dpNative(getByteAddress(device)) ) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    synchronized (A2dpSinkStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                    // TODO(BT) remove CONNECT_TIMEOUT when the stack
                    //          sends back events consistently
                    sendMessageDelayed(CONNECT_TIMEOUT, 30000);
                    break;
                case DISCONNECT:
                    // ignore
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.audioConfig, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        @Override
        public void exit() {
            log("Exit Disconnected: " + getCurrentMessage().what);
        }

        // in Disconnected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
            case CONNECTION_STATE_DISCONNECTED:
                logw("Ignore HF DISCONNECTED event, device: " + device);
                break;
            case CONNECTION_STATE_CONNECTING:
                if (okToConnect(device)){
                    logi("Incoming A2DP accepted");
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpSinkStateMachine.this) {
                        mIncomingDevice = device;
                        transitionTo(mPending);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    logi("Incoming A2DP rejected");
                    disconnectA2dpNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device,
                                                           AdapterService.PROFILE_CONN_REJECTED);
                    }
                }
                break;
            case CONNECTION_STATE_CONNECTED:
                logw("A2DP Connected from Disconnected state");
                if (okToConnect(device)){
                    logi("Incoming A2DP accepted");
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = device;
                        transitionTo(mConnected);
                    }
                } else {
                    //reject the connection and stay in Disconnected state itself
                    logi("Incoming A2DP rejected");
                    disconnectA2dpNative(getByteAddress(device));
                    // the other profile connection should be initiated
                    AdapterService adapterService = AdapterService.getAdapterService();
                    if (adapterService != null) {
                        adapterService.connectOtherProfile(device,
                                                           AdapterService.PROFILE_CONN_REJECTED);
                    }
                }
                break;
            case CONNECTION_STATE_DISCONNECTING:
                logw("Ignore HF DISCONNECTING event, device: " + device);
                break;
            default:
                loge("Incorrect state: " + state);
                break;
            }
        }
    }

    private class Pending extends State {
        @Override
        public void enter() {
            log("Enter Pending: " + getCurrentMessage().what);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Pending process message: " + message.what);

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                    deferMessage(message);
                    break;
                case CONNECT_TIMEOUT:
                    onConnectionStateChanged(CONNECTION_STATE_DISCONNECTED,
                                             getByteAddress(mTargetDevice));
                    break;
                case DISCONNECT:
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice != null && mTargetDevice != null &&
                        mTargetDevice.equals(device) ) {
                        // cancel connection to the mTargetDevice
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mTargetDevice = null;
                        }
                    } else {
                        deferMessage(message);
                    }
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            removeMessages(CONNECT_TIMEOUT);
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.audioConfig, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Pending state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    mAudioConfigs.remove(device);
                    if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                        broadcastConnectionState(mCurrentDevice,
                                                 BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_DISCONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mCurrentDevice = null;
                        }

                        if (mTargetDevice != null) {
                            if (!connectA2dpNative(getByteAddress(mTargetDevice))) {
                                broadcastConnectionState(mTargetDevice,
                                                         BluetoothProfile.STATE_DISCONNECTED,
                                                         BluetoothProfile.STATE_CONNECTING);
                                synchronized (A2dpSinkStateMachine.this) {
                                    mTargetDevice = null;
                                    transitionTo(mDisconnected);
                                }
                            }
                        } else {
                            synchronized (A2dpSinkStateMachine.this) {
                                mIncomingDevice = null;
                                transitionTo(mDisconnected);
                            }
                        }
                    } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                        // outgoing connection failed
                        broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mTargetDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                        broadcastConnectionState(mIncomingDevice,
                                                 BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                        synchronized (A2dpSinkStateMachine.this) {
                            mIncomingDevice = null;
                            transitionTo(mDisconnected);
                        }
                    } else {
                        loge("Unknown device Disconnected: " + device);
                    }
                    break;
            case CONNECTION_STATE_CONNECTED:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    // disconnection failed
                    broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTING);
                    if (mTargetDevice != null) {
                        broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTING);
                    }
                    synchronized (A2dpSinkStateMachine.this) {
                        mTargetDevice = null;
                        transitionTo(mConnected);
                    }
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    broadcastConnectionState(mTargetDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = mTargetDevice;
                        mTargetDevice = null;
                        transitionTo(mConnected);
                    }
                } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    broadcastConnectionState(mIncomingDevice, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_CONNECTING);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = mIncomingDevice;
                        mIncomingDevice = null;
                        transitionTo(mConnected);
                    }
                } else {
                    loge("Unknown device Connected: " + device);
                    // something is wrong here, but sync our state with stack
                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                             BluetoothProfile.STATE_DISCONNECTED);
                    synchronized (A2dpSinkStateMachine.this) {
                        mCurrentDevice = device;
                        mTargetDevice = null;
                        mIncomingDevice = null;
                        transitionTo(mConnected);
                    }
                }
                break;
            case CONNECTION_STATE_CONNECTING:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    log("current device tries to connect back");
                    // TODO(BT) ignore or reject
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    // The stack is connecting to target device or
                    // there is an incoming connection from the target device at the same time
                    // we already broadcasted the intent, doing nothing here
                    log("Stack and target device are connecting");
                }
                else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    loge("Another connecting event on the incoming device");
                } else {
                    // We get an incoming connecting request while Pending
                    // TODO(BT) is stack handing this case? let's ignore it for now
                    log("Incoming connection while pending, ignore");
                }
                break;
            case CONNECTION_STATE_DISCONNECTING:
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    // we already broadcasted the intent, doing nothing here
                    if (DBG) {
                        log("stack is disconnecting mCurrentDevice");
                    }
                } else if (mTargetDevice != null && mTargetDevice.equals(device)) {
                    loge("TargetDevice is getting disconnected");
                } else if (mIncomingDevice != null && mIncomingDevice.equals(device)) {
                    loge("IncomingDevice is getting disconnected");
                } else {
                    loge("Disconnecting unknown device: " + device);
                }
                break;
            default:
                loge("Incorrect state: " + state);
                break;
            }
        }

    }

    private class Connected extends State {
        @Override
        public void enter() {
            log("Enter Connected: " + getCurrentMessage().what);
            // Upon connected, the audio starts out as stopped
            broadcastAudioState(mCurrentDevice, BluetoothA2dpSink.STATE_NOT_PLAYING,
                                BluetoothA2dpSink.STATE_PLAYING);
        }

        @Override
        public boolean processMessage(Message message) {
            log("Connected process message: " + message.what);
            if (mCurrentDevice == null) {
                loge("ERROR: mCurrentDevice is null in Connected");
                return NOT_HANDLED;
            }

            boolean retValue = HANDLED;
            switch(message.what) {
                case CONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (mCurrentDevice.equals(device)) {
                        break;
                    }

                    broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTING,
                                   BluetoothProfile.STATE_DISCONNECTED);
                    if (!disconnectA2dpNative(getByteAddress(mCurrentDevice))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTED,
                                       BluetoothProfile.STATE_CONNECTING);
                        break;
                    }

                    synchronized (A2dpSinkStateMachine.this) {
                        mTargetDevice = device;
                        transitionTo(mPending);
                    }
                }
                    break;
                case DISCONNECT:
                {
                    BluetoothDevice device = (BluetoothDevice) message.obj;
                    if (!mCurrentDevice.equals(device)) {
                        break;
                    }
                    broadcastConnectionState(device, BluetoothProfile.STATE_DISCONNECTING,
                                   BluetoothProfile.STATE_CONNECTED);
                    if (!disconnectA2dpNative(getByteAddress(device))) {
                        broadcastConnectionState(device, BluetoothProfile.STATE_CONNECTED,
                                       BluetoothProfile.STATE_DISCONNECTED);
                        break;
                    }

                    if (mAudioFocusAcquired != AUDIO_FOCUS_LOSS) {
                        int status = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                        if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            mAudioFocusAcquired = AUDIO_FOCUS_LOSS;
                        }
                        releasePatch();
                    }
                    mPlayingDevice = null;
                    transitionTo(mPending);
                }
                    break;
                case SET_RENDERING_VOL:
                    setStreamingVol(mAudioManager.getStreamVolume(mCurrentStreamType), true,
                                    mAudioManager.getStreamMaxVolume(mCurrentStreamType));
                    break;
                case EVENT_TYPE_REQUEST_AUDIO_FOCUS:
                    processAudioFocusRequestEvent(1, (BluetoothDevice) message.obj);
                    break;
                case STACK_EVENT:
                    StackEvent event = (StackEvent) message.obj;
                    switch (event.type) {
                        case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                            processConnectionEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_STATE_CHANGED:
                            processAudioStateEvent(event.valueInt, event.device);
                            break;
                        case EVENT_TYPE_AUDIO_CONFIG_CHANGED:
                            processAudioConfigEvent(event.audioConfig, event.device);
                            break;
                        default:
                            loge("Unexpected stack event: " + event.type);
                            break;
                    }
                    break;
                default:
                    return NOT_HANDLED;
            }
            return retValue;
        }

        // in Connected state
        private void processConnectionEvent(int state, BluetoothDevice device) {
            switch (state) {
                case CONNECTION_STATE_DISCONNECTED:
                    mAudioConfigs.remove(device);
                    if ((mPlayingDevice != null) && (device.equals(mPlayingDevice))) {
                        mPlayingDevice = null;
                    }
                    if (mCurrentDevice.equals(device)) {
                        broadcastConnectionState(mCurrentDevice, BluetoothProfile.STATE_DISCONNECTED,
                                                 BluetoothProfile.STATE_CONNECTED);
                        synchronized (A2dpSinkStateMachine.this) {
                            mCurrentDevice = null;
                            transitionTo(mDisconnected);
                        }
                        int status = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                        if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                             mAudioFocusAcquired = AUDIO_FOCUS_LOSS;
                        /* Check if we need to release patch here
                         * Would we ever get disconnect directly without
                         * Suspend or Stoppped being called before */
                        }
                        releasePatch();
                    } else {
                        loge("Disconnected from unknown device: " + device);
                    }
                    break;
              default:
                  loge("Connection State Device: " + device + " bad state: " + state);
                  break;
            }
        }
        private void processAudioStateEvent(int state, BluetoothDevice device) {
            if (!mCurrentDevice.equals(device)) {
                loge("Audio State Device:" + device + "is different from ConnectedDevice:" +
                                                           mCurrentDevice);
                return;
            }
            log(" processAudioStateEvent in state " + state);
            switch (state) {
                case AUDIO_STATE_STARTED:
                    if (mPlayingDevice == null) {
                        mPlayingDevice = device;
                    }
                    requestAudioFocus(true, device, 0);
                    break;
                case AUDIO_STATE_REMOTE_SUSPEND:
                case AUDIO_STATE_STOPPED:
                    mPlayingDevice = null;
                    releasePatch();
                    broadcastAudioState(device, BluetoothA2dpSink.STATE_NOT_PLAYING,
                                        BluetoothA2dpSink.STATE_PLAYING);

                    if (mAudioFocusAcquired == AUDIO_FOCUS_LOSS) {
                       /* Audio Focus was already lost, no need to do it again */
                       return;
                    }
                    if ((mAudioFocusAcquired == AUDIO_FOCUS_LOSS_TRANSIENT) &&
                                     (state == AUDIO_STATE_REMOTE_SUSPEND)) {
                        log(" Dont't Loose audiofocus in case of suspend ");
                        break;
                    }
                    int status = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                    if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        mAudioFocusAcquired = AUDIO_FOCUS_LOSS;
                    }

                    break;
                default:
                  loge("Audio State Device: " + device + " bad state: " + state);
                  break;
            }
        }

        private void processAudioFocusRequestEvent(int enable, BluetoothDevice device) {
            if ((mCurrentDevice != null) && (mCurrentDevice.equals(device))
                    && (1 == enable)) {

                if (mAudioFocusAcquired == AUDIO_FOCUS_LOSS_TRANSIENT) {
                    log(" Transient Loss Still, Don't acquire focus, Send PAUSE");
                    SendPassThruPause(device);
                    return;
                }
                else if (mAudioFocusAcquired == AUDIO_FOCUS_GAIN) {
                    patchPorts();
                    return; /* if we already have focus, don't request again */
                }
                int status = mAudioManager.requestAudioFocus(mAudioFocusListener,
                                  AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
                log(" Audio Focus Request returned " + status);
                if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    mAudioFocusAcquired = AUDIO_FOCUS_GAIN;
                    patchPorts();
                }
                else {
                    log("Can't acquire Focus, request with delay");
                    requestAudioFocus(true, device, AUDIO_FOCUS_REQUEST_MESSAGE_DELAYED);
                }
            }
        }
    }

    private void processAudioConfigEvent(BluetoothAudioConfig audioConfig, BluetoothDevice device) {
        log("processAudioConfigEvent: " + device);
        mAudioConfigs.put(device, audioConfig);
        broadcastAudioConfig(device, audioConfig);
    }

    int getConnectionState(BluetoothDevice device) {
        if (getCurrentState() == mDisconnected) {
            return BluetoothProfile.STATE_DISCONNECTED;
        }

        synchronized (this) {
            IState currentState = getCurrentState();
            if (currentState == mPending) {
                if ((mTargetDevice != null) && mTargetDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTING;
                }
                if ((mCurrentDevice != null) && mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_DISCONNECTING;
                }
                if ((mIncomingDevice != null) && mIncomingDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTING; // incoming connection
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            }

            if (currentState == mConnected) {
                if (mCurrentDevice.equals(device)) {
                    return BluetoothProfile.STATE_CONNECTED;
                }
                return BluetoothProfile.STATE_DISCONNECTED;
            } else {
                loge("Bad currentState: " + currentState);
                return BluetoothProfile.STATE_DISCONNECTED;
            }
        }
    }

    BluetoothAudioConfig getAudioConfig(BluetoothDevice device) {
        return mAudioConfigs.get(device);
    }

    List<BluetoothDevice> getConnectedDevices() {
        List<BluetoothDevice> devices = new ArrayList<BluetoothDevice>();
        synchronized(this) {
            if (getCurrentState() == mConnected) {
                devices.add(mCurrentDevice);
            }
        }
        return devices;
    }

    boolean isPlaying(BluetoothDevice device) {
        synchronized(this) {
            if ((mPlayingDevice != null) && (device.equals(mPlayingDevice))) {
                return true;
            }
        }
        return false;
    }

    boolean okToConnect(BluetoothDevice device) {
        AdapterService adapterService = AdapterService.getAdapterService();
        int priority = mService.getPriority(device);
        boolean ret = false;
        //check if this is an incoming connection in Quiet mode.
        if((adapterService == null) ||
           ((adapterService.isQuietModeEnabled() == true) &&
           (mTargetDevice == null))){
            ret = false;
        }
        // check priority and accept or reject the connection. if priority is undefined
        // it is likely that our SDP has not completed and peer is initiating the
        // connection. Allow this connection, provided the device is bonded
        else if((BluetoothProfile.PRIORITY_OFF < priority) ||
                ((BluetoothProfile.PRIORITY_UNDEFINED == priority) &&
                (device.getBondState() != BluetoothDevice.BOND_NONE))){
            ret= true;
        }
        return ret;
    }

    synchronized List<BluetoothDevice> getDevicesMatchingConnectionStates(int[] states) {
        List<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
        Set<BluetoothDevice> bondedDevices = mAdapter.getBondedDevices();
        int connectionState;

        for (BluetoothDevice device : bondedDevices) {
            ParcelUuid[] featureUuids = device.getUuids();
            if (!BluetoothUuid.isUuidPresent(featureUuids, BluetoothUuid.AudioSource)) {
                continue;
            }
            connectionState = getConnectionState(device);
            for(int i = 0; i < states.length; i++) {
                if (connectionState == states[i]) {
                    deviceList.add(device);
                }
            }
        }
        return deviceList;
    }


    // This method does not check for error conditon (newState == prevState)
    private void broadcastConnectionState(BluetoothDevice device, int newState, int prevState) {

        int delay = mAudioManager.setBluetoothA2dpDeviceConnectionState(device, newState,
                BluetoothProfile.A2DP_SINK);
        connectionKpiMarker(newState, prevState);

        mWakeLock.acquire();
        mIntentBroadcastHandler.sendMessageDelayed(mIntentBroadcastHandler.obtainMessage(
                                                        MSG_CONNECTION_STATE_CHANGED,
                                                        prevState,
                                                        newState,
                                                        device),
                                                        0);
    }

    private void connectionKpiMarker (int newState, int prevState) {
        try {
            File f = new File("/proc/bootkpi/marker_entry");
            if (!f.exists())
                return;
            FileOutputStream fos = new FileOutputStream(f);
            if (prevState == BluetoothProfile.STATE_DISCONNECTED) {
                log("A2DP Start");
                fos.write("A2DP Start".getBytes());
            } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                log("A2DP End");
                fos.write("A2DP End".getBytes());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED &&
                    prevState == BluetoothProfile.STATE_CONNECTING) {
                log("A2DP fail");
                fos.write("A2DP fail".getBytes());
            }
            fos.flush();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void broadcastAudioState(BluetoothDevice device, int state, int prevState) {
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_PLAYING_STATE_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
        intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
//FIXME        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

        log("A2DP Playing state : device: " + device + " State:" + prevState + "->" + state);
    }

    private void broadcastAudioConfig(BluetoothDevice device, BluetoothAudioConfig audioConfig) {
        Intent intent = new Intent(BluetoothA2dpSink.ACTION_AUDIO_CONFIG_CHANGED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
        intent.putExtra(BluetoothA2dpSink.EXTRA_AUDIO_CONFIG, audioConfig);
//FIXME        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);

        log("A2DP Audio Config : device: " + device + " config: " + audioConfig);
    }

    private byte[] getByteAddress(BluetoothDevice device) {
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private void onConnectionStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_CONNECTION_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioStateChanged(int state, byte[] address) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_STATE_CHANGED);
        event.valueInt = state;
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void onAudioConfigChanged(byte[] address, int sampleRate, int channelCount) {
        StackEvent event = new StackEvent(EVENT_TYPE_AUDIO_CONFIG_CHANGED);
        int channelConfig = (channelCount == 1 ? AudioFormat.CHANNEL_IN_MONO
                                               : AudioFormat.CHANNEL_IN_STEREO);
        event.audioConfig = new BluetoothAudioConfig(sampleRate, channelConfig,
                AudioFormat.ENCODING_PCM_16BIT);
        event.device = getDevice(address);
        sendMessage(STACK_EVENT, event);
    }

    private void requestAudioFocus(boolean enable, BluetoothDevice device, int delay) {
        logw(" RequestAudioFocus for  " + device + " enable " + enable);
        if (enable) {
            // send a request for audio_focus
            Message posMsg = obtainMessage(EVENT_TYPE_REQUEST_AUDIO_FOCUS, device);
            sendMessageDelayed(posMsg, delay);
        }
    }

    private void setRenderingVolAfterStart() {
       log(" SetRenderingVolAfterStart ");
       sendMessageDelayed(SET_RENDERING_VOL, 500);
    }
    private BluetoothDevice getDevice(byte[] address) {
        return mAdapter.getRemoteDevice(Utils.getAddressStringFromByte(address));
    }

    private class StackEvent {
        int type = EVENT_TYPE_NONE;
        int valueInt = 0;
        BluetoothDevice device = null;
        BluetoothAudioConfig audioConfig = null;

        private StackEvent(int type) {
            this.type = type;
        }
    }
    /** Handles A2DP connection state change intent broadcasts. */
    private class IntentBroadcastHandler extends Handler {

        private void onConnectionStateChanged(BluetoothDevice device, int prevState, int state) {
            Intent intent = new Intent(BluetoothA2dpSink.ACTION_CONNECTION_STATE_CHANGED);
            intent.putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState);
            intent.putExtra(BluetoothProfile.EXTRA_STATE, state);
            intent.putExtra(BluetoothDevice.EXTRA_DEVICE, device);
//FIXME            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendBroadcast(intent, ProfileService.BLUETOOTH_PERM);
            log("Connection state " + device + ": " + prevState + "->" + state);
            mService.notifyProfileConnectionStateChanged(device, BluetoothProfile.A2DP_SINK,
                    state, prevState);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_CONNECTION_STATE_CHANGED:
                    onConnectionStateChanged((BluetoothDevice) msg.obj, msg.arg1, msg.arg2);
                    mWakeLock.release();
                    break;
            }
        }
    }

    public boolean SendPassThruPlay(BluetoothDevice mDevice) {
            log("SendPassThruPlay + ");
            AvrcpControllerService avrcpCtrlService = AvrcpControllerService.getAvrcpControllerService();
            if ((avrcpCtrlService != null) && (mDevice != null) &&
                (avrcpCtrlService.getConnectedDevices().contains(mDevice))){
                avrcpCtrlService.sendPassThroughCmd(mDevice, AVRC_ID_PLAY, KEY_STATE_PRESSED);
                avrcpCtrlService.sendPassThroughCmd(mDevice, AVRC_ID_PLAY, KEY_STATE_RELEASED);
                log(" SendPassThruPlay command sent - ");
                return true;
            } else {
                log("passthru command not sent, connection unavailable");
                return false;
            }
        }

    public boolean SendPassThruPause(BluetoothDevice mDevice) {
        log("SendPassThruPause + ");
        AvrcpControllerService avrcpCtrlService = AvrcpControllerService.getAvrcpControllerService();
        if ((avrcpCtrlService != null) && (mDevice != null) &&
            (avrcpCtrlService.getConnectedDevices().contains(mDevice))){
            avrcpCtrlService.sendPassThroughCmd(mDevice, AVRC_ID_PAUSE, KEY_STATE_PRESSED);
            avrcpCtrlService.sendPassThroughCmd(mDevice, AVRC_ID_PAUSE, KEY_STATE_RELEASED);
            log(" SendPassThruPause command sent - ");
            return true;
        } else {
            log("passthru command not sent, connection unavailable");
            return false;
        }
    }
    public void sendArcpVolUpdate(int streamVal, int streamPrevVal) {
        AvrcpControllerService avrcpCtrlService = AvrcpControllerService.getAvrcpControllerService();
        if (avrcpCtrlService != null){
            log(" volChanged command sent ");
            avrcpCtrlService.volChanged(streamVal, streamPrevVal, MAX_VOL);
        } else {
            log("volChanged not sent, service unavailable");
        }
    }

    public void setStreamingVol(int volIndex, boolean updateStreamVolume, int maxPossibleIndex) {
        log(" SetStreamingVol index = " + volIndex + "maxPossible " + maxPossibleIndex);
        synchronized(mLockForVolUpdate) {
            int prevVolGain = mCurVolGain;
            mCurVolGain = (volIndex*MAX_VOL)/maxPossibleIndex;
            mCurVolGain = (mCurVolGain > MAX_VOL) ? MAX_VOL:mCurVolGain;
            mCurVolGain = (mCurVolGain < 1)? 1:mCurVolGain;
            java.lang.StringBuilder sb = new java.lang.StringBuilder();
            /* if current Index is 0, make it lowest possible gain */
            if (volIndex == 0) {
                mCurVolGain = 1;
                sb.append(volSetMuteParamString);
            } else {
                if(prevVolGain == 1) {
                    /* Vol was muted earlier */
                    mAudioManager.setParameters(volSetUnMuteParamString);
                }
                /* If this is maximum possible index */
                if(volIndex == maxPossibleIndex) {
                    mCurVolGain = MAX_VOL;
                }
                sb.append(volSetParamString);
                sb.append(mCurVolGain);
            }
            /*
             * If Patch is not created and we do setParam
             * Vol is updated as 1. We anyways set vol after
             * doing patchPorts.
             */
            if(mA2dpSinkAudioPatch == null)
                return;
            log("setting Param " + sb.toString());
            mAudioManager.setParameters(sb.toString());
            if(updateStreamVolume)
                mAudioManager.setStreamVolume(mCurrentStreamType,
                                 (mCurVolGain/getGainMultiplier()), 0);
        }
    }
    public int getMaxVolume() {
        return MAX_VOL;
    }
    public int getCurVolume() {
        return mCurVolGain;
    }
    private final BroadcastReceiver mA2dpReceiver = new BroadcastReceiver() {
        @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                log("onReceive  " + action);
                if (action.equals("com.android.music.musicservicecommand")) {
                    String cmd = intent.getStringExtra("command");
                    log("Command Received  " + cmd);
                    if (cmd.equals("pause")) {
                        if (mCurrentDevice != null) {
                            if (SendPassThruPause(mCurrentDevice)) {
                                log(" Sending AVRCP Pause");
                            } else {
                                log(" Sending Disconnect AVRCP Not Up");
                                disconnectA2dpNative(getByteAddress(mCurrentDevice));
                            }
                            if (mAudioFocusAcquired != AUDIO_FOCUS_LOSS) {
                                int status = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                                log("abandonAudioFocus returned" + status);
                                if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                                    mAudioFocusAcquired = AUDIO_FOCUS_LOSS;
                                }
                            }
                        }
                    }
                }
                if (action.equals(AudioManager.VOLUME_CHANGED_ACTION)) {
                    mCurrentStreamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                    log(" stream Type : " + mCurrentStreamType);
                    int streamValue = intent
                             .getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, -1);
                    int streamPrevValue = intent.getIntExtra(
                             AudioManager.EXTRA_PREV_VOLUME_STREAM_VALUE, -1);
                    log(" stream prev vol " + streamPrevValue + " new Val = " + streamValue);
                    int prevVolGain = mCurVolGain;
                    setStreamingVol(streamValue, false,
                                     mAudioManager.getStreamMaxVolume(mCurrentStreamType));
                    sendArcpVolUpdate(mCurVolGain, prevVolGain);
                }
            }
        };

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange){
            log("onAudioFocusChangeListener focuschange " + focusChange);
            switch(focusChange){
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mCurrentDevice != null) {
                        /* First Release the Patch Anyways */
                        if (SendPassThruPause(mCurrentDevice)) {
                            log(" Sending AVRCP Pause");
                        } else {
                            log(" Sending Disconnect AVRCP Not Up");
                            disconnectA2dpNative(getByteAddress(mCurrentDevice));
                        }
                        int status = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                        log("abandonAudioFocus returned" + status);
                        if (status == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                            mAudioFocusAcquired = AUDIO_FOCUS_LOSS;
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if ((mCurrentDevice != null) && (getCurrentState() == mConnected)) {
                    /* don't abandon focus, but fake focus loss */
                       mAudioFocusAcquired = AUDIO_FOCUS_LOSS_TRANSIENT;
                       if (SendPassThruPause(mCurrentDevice)) {
                            log(" Sending AVRCP Pause");
                        } else {
                            log(" AVRCP Connection not UP");
                            disconnectA2dpNative(getByteAddress(mCurrentDevice));
                        }
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    log(" Received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK ");
                    mAudioFocusAcquired = AUDIO_FOCUS_LOSS_CAN_DUCK;
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    // we got focus gain
                    if ((mCurrentDevice != null) && (getCurrentState() == mConnected)) {
                        if (mAudioFocusAcquired == AUDIO_FOCUS_LOSS_CAN_DUCK) {
                            log(" Received Can_Duck earlier, Ignore Now ");
                            mAudioFocusAcquired = AUDIO_FOCUS_GAIN;
                            break;
                        }
                        mAudioFocusAcquired = AUDIO_FOCUS_GAIN;
                        if (SendPassThruPlay(mCurrentDevice)) {
                            log(" Sending AVRCP Play");
                        } else {
                            log(" AVRCP Connection not up");
                            /* TODO what shld we do in case AVRCP connection is not there */
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    // Event types for STACK_EVENT message
    final private static int EVENT_TYPE_NONE = 0;
    final private static int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    final private static int EVENT_TYPE_AUDIO_STATE_CHANGED = 2;
    final private static int EVENT_TYPE_AUDIO_CONFIG_CHANGED = 3;


   // Do not modify without updating the HAL bt_av.h files.

    // match up with btav_connection_state_t enum of bt_av.h
    final static int CONNECTION_STATE_DISCONNECTED = 0;
    final static int CONNECTION_STATE_CONNECTING = 1;
    final static int CONNECTION_STATE_CONNECTED = 2;
    final static int CONNECTION_STATE_DISCONNECTING = 3;

    // match up with btav_audio_state_t enum of bt_av.h
    final static int AUDIO_STATE_REMOTE_SUSPEND = 0;
    final static int AUDIO_STATE_STOPPED = 1;
    final static int AUDIO_STATE_STARTED = 2;

    private native static void classInitNative();
    private native void initNative();
    private native void cleanupNative();
    private native boolean connectA2dpNative(byte[] address);
    private native boolean disconnectA2dpNative(byte[] address);
}
