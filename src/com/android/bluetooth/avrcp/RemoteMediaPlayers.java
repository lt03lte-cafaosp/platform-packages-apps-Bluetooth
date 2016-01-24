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

import android.os.Bundle;
import android.util.Log;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import android.bluetooth.BluetoothAvrcpRemoteMediaPlayers;
import android.bluetooth.BluetoothAvrcpController;
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
     * Return true if we are able to set a player as browsed from available
     * media player list
     */
    public boolean setBrowsedPlayerFromList(int playerId) {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for (PlayerInfo mPlayerInfo : mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId != playerId)
                continue;
            setBrowsedPlayer(mPlayerInfo);
            return true;
        }
        return false;
    }
    public boolean setAddressedPlayerFromList(int playerId) {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for (PlayerInfo mPlayerInfo : mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId != playerId)
                continue;
            setAddressedPlayer(mPlayerInfo);
            return true;
        }
        return false;
    }
    /*
     * Returns the currently addressed, browsed player
     */
    public PlayerInfo getAddressedPlayer() {
        return mAddressedPlayer;
    }
    public PlayerInfo getBrowsedPlayer() {
        return mBrowsedPlayer;
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
    public  void updatePlayerList (byte numPlayers, Bundle data) {
        Log.d(TAG," updatePlayerList numPlayers = " + numPlayers);

        byte []featureMask = data.getByteArray("featureMask");
        for (int i = 0; i < numPlayers; i++) {
            int playerId = data.getIntArray("playerId")[i];
            if (isPlayerInList(playerId)) {
                Log.d(TAG," Player Already Present " + playerId);
                continue;
            }
            Log.d(TAG," adding player id = " + playerId);
            PlayerInfo mPlayerInfo = new PlayerInfo();
            mPlayerInfo.subType = data.getIntArray("subtype")[i];
            mPlayerInfo.mPlayerId = data.getIntArray("playerId")[i];
            mPlayerInfo.majorType = data.getByteArray("majorType")[i];
            mPlayerInfo.mPlayStatus = data.getByteArray("playStatus")[i];
            mPlayerInfo.mPlayerName = data.getStringArray("name")[i];
            for (int k = 0; k < AvrcpControllerConstants.PLAYER_FEATURE_MASK_SIZE; k++)
                mPlayerInfo.mFeatureMask[k] = featureMask[(i*AvrcpControllerConstants.
                        PLAYER_FEATURE_MASK_SIZE) + k];
            /* Add current addressed player setting and playtime */
            mPlayerInfo.copyPlayerAppSetting(mAddressedPlayer.mPlayerAppSetting);
            mPlayerInfo.mPlayTime = mAddressedPlayer.mPlayTime;
            mMediaPlayerList.add(mPlayerInfo);
        }
    }
    /*
     * API to get remote media player list
     */
    public BluetoothAvrcpRemoteMediaPlayers getTotalMediaPlayerList() {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return null;
        BluetoothAvrcpRemoteMediaPlayers mRemoteMediaPlayers = new BluetoothAvrcpRemoteMediaPlayers();
        for (PlayerInfo mPlayerInfo: mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId == AvrcpControllerConstants.DEFAULT_PLAYER_ID) continue;
            mRemoteMediaPlayers.addPlayer(mPlayerInfo.mPlayStatus, mPlayerInfo.subType,
                    mPlayerInfo.mPlayerId, mPlayerInfo.majorType, mPlayerInfo.mFeatureMask,
                    mPlayerInfo.mPlayerName);
        }
        return mRemoteMediaPlayers;
    }
    public BluetoothAvrcpRemoteMediaPlayers getBrowsedMediaPlayerList() {
        BluetoothAvrcpRemoteMediaPlayers mRemoteMediaPlayers = new BluetoothAvrcpRemoteMediaPlayers();
        PlayerInfo mPlayerInfo = getBrowsedPlayer();
        if (mPlayerInfo == null) return null;
        mRemoteMediaPlayers.addPlayer(mPlayerInfo.mPlayStatus, mPlayerInfo.subType,
                mPlayerInfo.mPlayerId, mPlayerInfo.majorType, mPlayerInfo.mFeatureMask,
                mPlayerInfo.mPlayerName);
        return mRemoteMediaPlayers;
    }
    public BluetoothAvrcpRemoteMediaPlayers getAddressedMediaPlayerList() {
        BluetoothAvrcpRemoteMediaPlayers mRemoteMediaPlayers = new BluetoothAvrcpRemoteMediaPlayers();
        PlayerInfo mPlayerInfo = getAddressedPlayer();
        if (mPlayerInfo == null) return null;
        mRemoteMediaPlayers.addPlayer(mPlayerInfo.mPlayStatus, mPlayerInfo.subType,
                mPlayerInfo.mPlayerId, mPlayerInfo.majorType, mPlayerInfo.mFeatureMask,
                mPlayerInfo.mPlayerName);
        return mRemoteMediaPlayers;
    }
    public boolean isPlayerInList(int playerId) {
        if((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for ( PlayerInfo mPlayerInfo :mMediaPlayerList) {
            if(mPlayerInfo.mPlayerId == playerId)
                return true;
        }
        return false;
    }
    public boolean isPlayerBrowsable (int playerId) {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for ( PlayerInfo mPlayerInfo :mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId == playerId) {
                if (!mPlayerInfo.isFeatureMaskBitSet(BluetoothAvrcpController.
                        PLAYER_FEATURE_BITMASK_BROWSING_BIT))
                    return false;
                if (mPlayerInfo.isFeatureMaskBitSet(BluetoothAvrcpController.
                        PLAYER_FEATURE_BITMASK_ONLY_BROWSABLE_WHEN_ADDRESSED) &&
                        (getAddressedPlayer().mPlayerId != playerId)) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }
    public boolean isPlayerSearchable(int playerId) {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for ( PlayerInfo mPlayerInfo :mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId == playerId) {
                if (!mPlayerInfo.isFeatureMaskBitSet(BluetoothAvrcpController.
                        PLAYER_FEATURE_BITMASK_SEARCH_BIT))
                    return false;
                if (mPlayerInfo.isFeatureMaskBitSet(BluetoothAvrcpController.
                        PLAYER_FEATURE_BITMASK_ONLY_SEARCHABLE_WHEN_ADDRESSED) &&
                        (getAddressedPlayer().mPlayerId != playerId)) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }
    public boolean isPlayerSupportNowPlaying(int playerId) {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for ( PlayerInfo mPlayerInfo :mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId == playerId) {
                if (!mPlayerInfo.isFeatureMaskBitSet(BluetoothAvrcpController.
                        PLAYER_FEATURE_BITMASK_NOW_PLAYING_BIT))
                    return false;

                return true;
            }
        }
        return false;
    }
    public boolean isPlayerSupportAddToNowPlaying(int playerId) {
        if ((mMediaPlayerList == null) || (mMediaPlayerList.isEmpty()))
            return false;
        for ( PlayerInfo mPlayerInfo :mMediaPlayerList) {
            if (mPlayerInfo.mPlayerId == playerId) {
                if (!mPlayerInfo.isFeatureMaskBitSet(BluetoothAvrcpController.
                        PLAYER_FEATURE_BITMASK_ADD_TO_NOW_PLAYING_BIT))
                    return false;

                return true;
            }
        }
        return false;
    }
}
