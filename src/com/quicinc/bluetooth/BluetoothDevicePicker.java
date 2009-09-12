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

package com.quicinc.bluetooth;

import com.quicinc.bluetooth.LocalBluetoothManager.Callback;

import java.util.List;
import java.util.WeakHashMap;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothIntent;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.util.Log;

/**
 * BluetoothDevicePicker allows the application to pick up a
 * device.
 */
public class BluetoothDevicePicker extends PreferenceActivity
        implements LocalBluetoothManager.Callback {
    public static final String NAME = "NAME";
    public static final String ADDRESS = "ADDRESS";

    private static final String TAG = "BluetoothDevicePicker";

    private static final int MENU_SCAN = Menu.FIRST;

    private static final String KEY_BT_DEVICE_LIST = "bt_device_list";
    private LocalBluetoothManager mLocalManager;

    private ProgressCategory mDeviceList;

    private WeakHashMap<LocalBluetoothDevice, BluetoothDevicePreference> mDevicePreferenceMap =
            new WeakHashMap<LocalBluetoothDevice, BluetoothDevicePreference>();

    /* Return with the result */
    private Intent mIntent;

    private Uri mUri;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO: put this in callback instead of receiving
            onBluetoothStateChanged(mLocalManager.getBluetoothState());
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mLocalManager = LocalBluetoothManager.getInstance(this);
        if (mLocalManager == null) finish();

        mIntent = getIntent();
        mUri = mIntent.getData();
        String action = mIntent.getAction();
        String profile = mIntent.getStringExtra(BluetoothAppIntent.PROFILE);

        if (mUri != null) {
           Log.d(TAG, "onCreate : mUri - "+ mUri.toString());
        }
        Log.d(TAG, "onCreate : action - "+ action);
        Log.d(TAG, "onCreate : profile - "+ profile);

        boolean parametersOk=true;
        if (!profile.equals(BluetoothAppIntent.PROFILE_OPP)
         && !profile.equals(BluetoothAppIntent.PROFILE_FTP))
        {
            Log.e(TAG,
                  "Error: this activity may be started only for Profiles " +
                  BluetoothAppIntent.PROFILE_FTP +
                  " or "+ BluetoothAppIntent.PROFILE_FTP);
            parametersOk=false;
        }

        if (profile.equals(BluetoothAppIntent.PROFILE_OPP))
        {
           /* For OPP Push operations URI is mandatory parameter */
           if ( (mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_BUSINESS_CARD)
              || mIntent.getAction().equals(BluetoothAppIntent.ACTION_PUSH_FILE))
              && (mUri == null))
           {
              Log.e(TAG,
                  "Error: Contact or Media Uri has to be specified to send it to the Remote device");
              parametersOk=false;
           }
        }

        if(parametersOk==false)
        {
           finish();
           return;
        }

        addPreferencesFromResource(R.xml.bluetooth_devices);
        mDeviceList = (ProgressCategory) findPreference(KEY_BT_DEVICE_LIST);

        registerForContextMenu(getListView());
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Re-populate (which isn't too bad since it's cached in the settings
        // Bluetooth manager
        mDevicePreferenceMap.clear();
        mDeviceList.removeAll();
        addDevices();

        mLocalManager.registerCallback(this);
        mLocalManager.startScanning(false);

        registerReceiver(mReceiver,
                new IntentFilter(BluetoothIntent.BLUETOOTH_STATE_CHANGED_ACTION));

        mLocalManager.setForegroundActivity(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        mLocalManager.setForegroundActivity(null);

        unregisterReceiver(mReceiver);

        mLocalManager.unregisterCallback(this);
    }

    private void addDevices() {
        List<LocalBluetoothDevice> devices = mLocalManager.getLocalDeviceManager().getDevicesCopy();
        for (LocalBluetoothDevice device : devices) {
            onDeviceAdded(device);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_SCAN, 0, R.string.bluetooth_scan_for_devices)
                .setIcon(com.android.internal.R.drawable.ic_menu_refresh)
                .setAlphabeticShortcut('r');
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(MENU_SCAN).setEnabled(mLocalManager.getBluetoothManager().isEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            case MENU_SCAN:
                mLocalManager.startScanning(true);
                return true;

            default:
                return false;
        }
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {

        if (preference instanceof BluetoothDevicePreference) {
           BluetoothDevicePreference btPreference = (BluetoothDevicePreference) preference;
           LocalBluetoothDevice localDevice = btPreference.getDevice();
           /* Stop the scanning */
           mLocalManager.stopScanning();

           String profile = mIntent.getStringExtra(BluetoothAppIntent.PROFILE);

           if (profile.equals(BluetoothAppIntent.PROFILE_OPP))
           {
              mIntent.putExtra(NAME, localDevice.getName());
              mIntent.putExtra(ADDRESS, localDevice.getAddress());
              mIntent.setClass(this, BluetoothOppActivity.class);
              try {
                 startActivity(mIntent);
              } catch (ActivityNotFoundException e) {
                  Log.e(TAG, "No Activity for : " + mIntent.getAction(), e);
              }
           }
           else if (profile.equals(BluetoothAppIntent.PROFILE_FTP))
           {
              Intent intent = new Intent(null, mUri);
              intent.putExtra(NAME, localDevice.getName());
              intent.putExtra(ADDRESS, localDevice.getAddress());
              setResult(Activity.RESULT_OK, intent);
           }
           finish();
           return true;
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public void onDeviceAdded(LocalBluetoothDevice device) {

        if (mDevicePreferenceMap.get(device) != null) {
            throw new IllegalStateException("Got onDeviceAdded, but device already exists");
        }
        /* Only Add OBEX devices */
        if(device.doesClassMatchObjectTransfer())
        {
           Log.d(TAG, "Found Object Transfer device : "+ device.getName());
           createDevicePreference(device);
        }
        else
        {
           Log.d(TAG, "Found non - Object Transfer device : "+ device.getName());
        }
    }

    private void createDevicePreference(LocalBluetoothDevice device) {
        BluetoothDevicePreference preference = new BluetoothDevicePreference(this, device);
        mDeviceList.addPreference(preference);
        mDevicePreferenceMap.put(device, preference);
    }

    public void onDeviceDeleted(LocalBluetoothDevice device) {
        BluetoothDevicePreference preference = mDevicePreferenceMap.remove(device);
        if (preference != null) {
            mDeviceList.removePreference(preference);
        }
    }

    public void onScanningStateChanged(boolean started) {
        mDeviceList.setProgress(started);
    }

    private void onBluetoothStateChanged(int bluetoothState) {
       // When bluetooth is enabled (and we are in the activity, which we are),
       // we should start a scan
       if (bluetoothState == BluetoothDevice.BLUETOOTH_STATE_ON) {
           mLocalManager.startScanning(false);
       } else if (bluetoothState == BluetoothDevice.BLUETOOTH_STATE_OFF) {
           mDeviceList.setProgress(false);
       }
    }
}
