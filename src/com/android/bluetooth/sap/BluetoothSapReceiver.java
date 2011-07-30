/*
 * Copyright (c) 2011, Code Aurora Forum. All rights reserved.
 * Copyright (c) 2008-2009, Motorola, Inc.
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

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.os.Handler;
import com.android.bluetooth.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Message;
import android.os.PowerManager;
import android.app.Service;
import android.content.res.Resources;

public class BluetoothSapReceiver extends BroadcastReceiver {

    private static final String TAG = "BluetoothSapReceiver";

    private static final boolean V = true;

    private static final int NOTIFICATION_ID_ACCESS = -1000009;

    private Context mContext;

    private Notification notification;


    private final Handler mSessionStatusHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            Log.i(TAG, "Handler(): got msg=" + msg.what);
            switch (msg.what) {
                case BluetoothSapActivity.USER_TIMEOUT:
                    Intent intent = new Intent(BluetoothSapActivity.USER_CONFIRM_TIMEOUT_ACTION);
                    mContext.sendBroadcast(intent);
                    removeSapNotification(NOTIFICATION_ID_ACCESS);
                default:
                    break;
            }
        }
    };
    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;
        String action = intent.getAction();
        String name = intent.getStringExtra("name");
        String address = intent.getStringExtra("address");
        Log.i(TAG, "SapReceiver onReceive: " + action + " " + name);
        createSapNotification(action, name, address);
        mSessionStatusHandler.sendMessageDelayed(mSessionStatusHandler
            .obtainMessage(BluetoothSapActivity.USER_TIMEOUT), BluetoothSapActivity.USER_CONFIRM_TIMEOUT_VALUE);
    }

    private void createSapNotification(String action, String name, String address) {

        NotificationManager nm = (NotificationManager)
            mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        // Create an intent triggered by clicking on the status icon.
        Intent clickIntent = new Intent();
        clickIntent.setClass(mContext, BluetoothSapActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        clickIntent.setAction(action);
        clickIntent.putExtra("name", name);
        clickIntent.putExtra("address", address);

        // Create an intent triggered by clicking on the
        // "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setAction(BluetoothSapActivity.ACCESS_DISALLOWED_ACTION);

        notification = null;

        notification = new Notification(android.R.drawable.stat_sys_data_bluetooth,
            mContext.getString(R.string.sap_notif_ticker), System.currentTimeMillis());

        notification.setLatestEventInfo(mContext, mContext.getString(R.string.sap_notif_title),
                mContext.getString(R.string.sap_notif_message, name), PendingIntent
                        .getActivity(mContext, 0, clickIntent, 0));

        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        notification.flags |= Notification.FLAG_ONLY_ALERT_ONCE;
        notification.defaults = Notification.DEFAULT_SOUND;
        notification.deleteIntent = PendingIntent.getBroadcast(mContext, 0, deleteIntent, 0);
        nm.notify(NOTIFICATION_ID_ACCESS, notification);
    }

    private void removeSapNotification(int id) {
        NotificationManager nm = (NotificationManager)
            mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(id);
    }


}
