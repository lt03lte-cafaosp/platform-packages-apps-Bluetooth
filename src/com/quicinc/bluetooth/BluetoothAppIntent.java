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

/**
 * Intents used by Bluetooth Applications:
 *
 * @hide
 */
public interface BluetoothAppIntent {

    /**
     *  Extras related to the Intent Actions
     */
    public static final String PROFILE     =
       "com.quicinc.bluetooth.intent.PROFILE";
    public static final String PROFILE_OPP =
       "com.quicinc.bluetooth.intent.PROFILE.OPP";
    public static final String PROFILE_FTP =
       "com.quicinc.bluetooth.intent.PROFILE.FTP";

    /**
     * Intent posted by non-Bluetooth Apps (Contacts) to discover
     * Bluetooth devices and send Contact to a remote Bluetooth
     * device.
     * <p>
     * <ul>
     *   <li>Uri (Uri): Contact to send)
     * </ul>
     *
     */
    public static final String ACTION_PUSH_BUSINESS_CARD =
        "com.quicinc.bluetooth.ACTION_PUSH_BUSINESS_CARD";

    /**
     * Intent posted by non-Bluetooth Apps (Contacts) to discover
     * Bluetooth devices from which a Business Card needs to be
     * pulled.
     */
    public static final String ACTION_PULL_BUSINESS_CARD =
        "com.quicinc.bluetooth.ACTION_PULL_BUSINESS_CARD";

    /**
     * Intent posted by non-Bluetooth Apps (Contacts, Music, Camera)
     * to discover Bluetooth devices and send a VCard or Media File
     * to a remote Bluetooth device.
     * <p>
     * <ul>
     *   <li>Uri (Uri): Contact/Media File to send
     *   <li>FILE_NAME (string): Uri of the Contact/Media File to
     *   send)
     * </ul>
     *
     */
    public static final String ACTION_PUSH_FILE =
        "com.quicinc.bluetooth.ACTION_PUSH_FILE";

    /**
     * Intent posted by FTP Client to pick a Remote
     * device to pass it back to continue with connecting.
     * <p>
     */
    public static final String ACTION_SELECT_BLUETOOTH_DEVICE =
        "com.quicinc.bluetooth.ACTION_SELECT_BLUETOOTH_DEVICE";
}
