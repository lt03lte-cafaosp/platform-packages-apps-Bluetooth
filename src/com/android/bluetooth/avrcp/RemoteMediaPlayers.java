/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *   * Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.avrcp;

import com.android.bluetooth.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import android.util.Log;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
/**
 * Provides Bluetooth AVRCP Controller profile, as a service in the Bluetooth application.
 * @hide
 */
public class RemoteMediaPlayers {
    private static final boolean DBG = true;
    private static final String TAG = "RemoteMediaPlayers";

    RemoteDevice mDevice;
    private PlayerInfo mAddressedPlayer;
    private PlayerInfo mBrowsedPlayer;
    ArrayList<PlayerInfo> mMediaPlayerList;

    public RemoteMediaPlayers (RemoteDevice mRemoteDevice) {
        mDevice = mRemoteDevice;
        mAddressedPlayer = null;
        mBrowsedPlayer = null;
        mMediaPlayerList = new ArrayList<PlayerInfo>();
    }

    public void cleanup() {
        mDevice = null;
        mAddressedPlayer = null;
        mBrowsedPlayer = null;
        if(mMediaPlayerList != null)
            mMediaPlayerList.clear();
    }
    /*
     * add a Player
     */
    public void addPlayer (PlayerInfo mPlayer) {
        if(mMediaPlayerList != null)
            mMediaPlayerList.add(mPlayer);
    }
    /*
     * add players and Set AddressedPlayer and BrowsePlayer
     */
    public void setAddressedPlayer(PlayerInfo mPlayer) {
        mAddressedPlayer = mPlayer;
    }

    public void setBrowsedPlayer(PlayerInfo mPlayer) {
        mBrowsedPlayer = mPlayer;
    }
    /*
     * Returns the currently addressed, browsed player
     */
    public PlayerInfo getAddressedPlayer() {
        return mAddressedPlayer;
    }

    /*
     * getPlayStatus of addressed player
     */
    public byte getPlayStatus() {
        if(getAddressedPlayer() != null)
            return getAddressedPlayer().mPlayStatus;
        else
            return AvrcpControllerConstants.PLAY_STATUS_STOPPED;
    }

    /*
     * getPlayStatus of addressed player
     */
    public long getPlayPosition() {
        if(getAddressedPlayer() != null)
            return getAddressedPlayer().mPlayTime;
        else
            return AvrcpControllerConstants.PLAYING_TIME_INVALID;
    }

}
