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
import android.os.Bundle;
/**
 * Provides Bluetooth AVRCP Controller profile, as a service in the Bluetooth application.
 * @hide
 */
public class NowPlaying {
    private static final boolean DBG = true;
    private static final String TAG = "NowPlaying";

    RemoteDevice mDevice;
    private TrackInfo mCurrTrack;

    public ArrayList<TrackInfo> mNowPlayingList;

    public NowPlaying(RemoteDevice mRemoteDevice) {
        mDevice = mRemoteDevice;
        mNowPlayingList = new ArrayList<TrackInfo>();
        mCurrTrack = null;
    }

    public void cleanup() {
        mDevice = null;
        if(mNowPlayingList != null) {
            mNowPlayingList.clear();
        }
        mCurrTrack = null;
    }


    public void addTrack (TrackInfo mTrack) {
        if(mNowPlayingList != null) {
            mNowPlayingList.add(mTrack);
        }
    }

    public void setCurrTrack (TrackInfo mTrack) {
        mCurrTrack = mTrack;
    }

    public TrackInfo getCurrentTrack() {
        return mCurrTrack;
    }

    public void updateCurrentTrack(TrackInfo mTrack) {
        mCurrTrack.mAlbumTitle = mTrack.mAlbumTitle;
        mCurrTrack.mArtistName = mTrack.mArtistName;
        mCurrTrack.mGenre = mTrack.mGenre;
        mCurrTrack.mTotalTracks = mTrack.mTotalTracks;
        mCurrTrack.mTrackLen = mTrack.mTrackLen;
        mCurrTrack.mTrackTitle = mTrack.mTrackTitle;
        mCurrTrack.mTrackNum = mTrack.mTrackNum;
        if (mCurrTrack.mCoverArtHandle != mTrack.mCoverArtHandle) {
            mCurrTrack.mCoverArtHandle = mTrack.mCoverArtHandle;
            mCurrTrack.mThumbNailLocation = AvrcpControllerConstants.COVER_ART_LOCATION_INVALID;
            mCurrTrack.mImageLocation = AvrcpControllerConstants.COVER_ART_LOCATION_INVALID;
        }
    }

    public TrackInfo getTrackFromId(long mTrackId) {
        if(mTrackId == 0)
            return getCurrentTrack();
        else {
            for(TrackInfo mTrackInfo: mNowPlayingList) {
                if(mTrackInfo.mItemUid == mTrackId)
                    return mTrackInfo;
            }
            return null;
        }
    }
    public int fetchThumbNail() {
        /*
         * Is BIP Fetch is in progress
         */
        if (!mDevice.isBipConnected())
            return AvrcpControllerConstants.ERROR_BIP_NOT_CONNECTED;
        if (mDevice.isBipFetchInProgress())
            return AvrcpControllerConstants.ERROR_BIP_FETCH_IN_PROGRESS;
        /*
         * We will check which track has valid coverArtHandle but imageLcoation is empty
         */
        for (TrackInfo mTrackInfo: mNowPlayingList) {
            Log.d(TAG,"HNDL: " + mTrackInfo.mCoverArtHandle + " Loc: " +
                                                              mTrackInfo.mThumbNailLocation);
            if ((!mTrackInfo.mCoverArtHandle.equals(AvrcpControllerConstants.
                 COVER_ART_HANDLE_INVALID)) && (mTrackInfo.mThumbNailLocation.equals
                 (AvrcpControllerConstants.COVER_ART_LOCATION_INVALID))) {
                    mDevice.GetLinkedThumbnail(mTrackInfo.mCoverArtHandle);
                    return AvrcpControllerConstants.FETCHING_THUMBNAIL;
            }
        }
        return AvrcpControllerConstants.ALL_THUMBNAILS_FETCHED;
    }
    /*
     * Returns the error for which cover art is fetched, can be used to check if valid
     * handle exists or not.
     */
    public int fetchCoverArtImage(String encoding, int height, int width, long maxSize) {
        /*
         * Is BIP Fetch is in progress
         */
        if (!mDevice.isBipConnected())
            return AvrcpControllerConstants.ERROR_BIP_NOT_CONNECTED;
        if (mDevice.isBipFetchInProgress())
            return AvrcpControllerConstants.ERROR_BIP_FETCH_IN_PROGRESS;
        /*
         * We will check which track has valid coverArtHandle but imageLcoation is empty
         */
        TrackInfo mTrackInfo = getCurrentTrack();
        if (mTrackInfo.mCoverArtHandle.equals(AvrcpControllerConstants.COVER_ART_HANDLE_INVALID))
            return AvrcpControllerConstants.ERROR_BIP_HANDLE_NOT_VALID;
        if (mTrackInfo.mImageLocation.equals
                (AvrcpControllerConstants.COVER_ART_LOCATION_INVALID)) {
                   StringBuffer pixel = new StringBuffer();
                   pixel.append(height); pixel.append("*"); pixel.append(width);
                   mDevice.GetImage(mTrackInfo.mCoverArtHandle, encoding, pixel.toString(), 
                           maxSize);
            return AvrcpControllerConstants.FETCHING_IMAGE;
        }
        return AvrcpControllerConstants.IMAGE_FETCHED;
    }
    public void updateThumbNail(String mCoverArtHandle, String mImageLocation) {
        Log.d(TAG," updateCoverArt HNDL" + mCoverArtHandle + " Loc: "+ mImageLocation);
        for (TrackInfo mTrackInfo: mNowPlayingList) {
            if((mTrackInfo.mCoverArtHandle.equals(mCoverArtHandle) && (mImageLocation != null)))
                mTrackInfo.mThumbNailLocation = mImageLocation;
        }
    }
    public void updateImage(String mCoverArtHandle, String mImageLocation) {
        Log.d(TAG," updateImage HNDL" + mCoverArtHandle + " Loc: "+ mImageLocation);
        TrackInfo mTrackInfo = getCurrentTrack();
        if((mTrackInfo.mCoverArtHandle.equals(mCoverArtHandle) && (mImageLocation != null)))
            mTrackInfo.mImageLocation = mImageLocation;
    }
    public void clearCoverArtData() {
        Log.d(TAG," clearCoverArtData");
        if (mNowPlayingList == null) return;
        for (TrackInfo mTrackInfo: mNowPlayingList) {
            mTrackInfo.mCoverArtHandle = AvrcpControllerConstants.COVER_ART_HANDLE_INVALID;
            mTrackInfo.mThumbNailLocation = AvrcpControllerConstants.COVER_ART_LOCATION_INVALID;
            mTrackInfo.mImageLocation = AvrcpControllerConstants.COVER_ART_LOCATION_INVALID;
        }
    }
    public void clearNowPlayingList() {
        Log.d(TAG," clearNowPlayingList");
        if (mNowPlayingList == null) return;
        mNowPlayingList.clear();
    }
    public int updateNowPlayingList(Bundle data) {
        Log.d(TAG, "udateNowPlayingList ");

        int numItems = data.getInt("numItems");
        byte[] itemType = data.getByteArray("itemType");
        long[] uids = data.getLongArray("uids");
        byte[] type = data.getByteArray("type");
        byte[] numAttrs = data.getByteArray("numAttrs");
        int[] attributIds = data.getIntArray("attrs");
        String[] attributes = data.getStringArray("attributes");
        int index = 0;
        int cumulativeAttribIndex = 0;
        int[] currentAttribIds; String[] currentAttribVals;
        for (index = 0; index < numItems; index++) {
            if (itemType[index] != AvrcpControllerConstants.BTRC_TYPE_MEDIA_ELEMENT)
                continue;
            currentAttribIds = Arrays.copyOfRange(attributIds, cumulativeAttribIndex,
                    cumulativeAttribIndex + numAttrs[index]);
            currentAttribVals = Arrays.copyOfRange(attributes, cumulativeAttribIndex,
                    cumulativeAttribIndex + numAttrs[index]);
            TrackInfo mTrack = new TrackInfo(uids[index], numAttrs[index], currentAttribIds,
                    currentAttribVals);
            mTrack.mediaType = type[index];
            mNowPlayingList.add(mTrack);
            cumulativeAttribIndex += numAttrs[index];
        }
        return mNowPlayingList.size();
    }
}
