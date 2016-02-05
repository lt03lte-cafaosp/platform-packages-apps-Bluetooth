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
import java.util.Stack;

import android.os.Bundle;
/**
 * Provides Bluetooth AVRCP Controller profile, as a service in the Bluetooth application.
 * @hide
 */
public class RemoteFileSystem {
    private static final boolean DBG = true;
    private static final String TAG = "RemoteFileSystem";
    public ArrayList<TrackInfo> mMediaItemList;
    public ArrayList<TrackInfo> mSearchList;
    public ArrayList<FolderItems> mFolderItemList;
    public ArrayList<FolderStackInfo> mFolderStack;
    private RemoteDevice mDevice;

    public void cleanup() {
        clearSearchList();
        clearVFSList();
        clearFolderStack();
        mDevice = null;
    }
    public RemoteFileSystem (RemoteDevice mRemoteDevice) {
        mMediaItemList = new ArrayList<TrackInfo>();
        mFolderItemList = new ArrayList<FolderItems>();
        mSearchList = new ArrayList<TrackInfo>();
        mFolderStack = new ArrayList<FolderStackInfo>();
        mDevice = mRemoteDevice;
    }
    public void clearVFSList() {
        if(mMediaItemList != null) {
            mMediaItemList.clear();
        }
        if (mFolderItemList != null) {
            mFolderItemList.clear();
        }
    }
    public void clearFolderStack() {
        if(mFolderStack != null)
            mFolderStack.clear();
    }
    public void clearSearchList() {
        if (mSearchList != null) {
            mSearchList.clear();
        }
    }
    public int getVFSListSize() {
        return (mFolderItemList.size() + mMediaItemList.size());
    }
    public int getSearchListSize() {
        return mSearchList.size();
    }
    public int updateVFSList(Bundle data) {
        int numItems = data.getInt("numItems");
        byte[] itemType = data.getByteArray("itemType");
        long[] uids = data.getLongArray("uids");
        byte[] type = data.getByteArray("type");
        byte[] playable = data.getByteArray("playable");
        String[] itemName = data.getStringArray("itemName");
        byte[] numAttrs = data.getByteArray("numAttrs");
        int[] attributIds = data.getIntArray("attrs");
        String[] attributes = data.getStringArray("attributes");
        int index = 0;
        int cumulativeAttribIndex = 0;
        int[] currentAttribIds; String[] currentAttribVals;
        for (index = 0; index < numItems; index++) {
            if (itemType[index] == AvrcpControllerConstants.BTRC_TYPE_MEDIA_ELEMENT) {
                currentAttribIds = Arrays.copyOfRange(attributIds, cumulativeAttribIndex,
                        cumulativeAttribIndex + numAttrs[index]);
                currentAttribVals = Arrays.copyOfRange(attributes, cumulativeAttribIndex,
                        cumulativeAttribIndex + numAttrs[index]);
                TrackInfo mTrack = new TrackInfo(uids[index], numAttrs[index], currentAttribIds,
                        currentAttribVals);
                mTrack.mediaType = type[index];
                mMediaItemList.add(mTrack);
                cumulativeAttribIndex += numAttrs[index];
            }
            else if (itemType[index] == AvrcpControllerConstants.BTRC_TYPE_FOLDER) {
                FolderItems mFolderItem = new FolderItems(uids[index], itemName[index],
                        playable[index], type[index]);
                mFolderItemList.add(mFolderItem);
            }
        }
        return (mFolderItemList.size() + mMediaItemList.size());
    }
    public int updateSearchList(Bundle data) {
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
            mSearchList.add(mTrack);
            cumulativeAttribIndex += numAttrs[index];
        }
        return mSearchList.size();
    }
    public long isIdInVFSFolderList(String id) {
        if ((mFolderItemList == null) || (mFolderItemList.isEmpty())) {
            Log.e(TAG," FolderItemList empty ");
            return AvrcpControllerConstants.DEFAULT_FOLDER_ID;
        }
        for (FolderItems mItem: mFolderItemList) {
            if (Long.toString(mItem.mItemUid).equals(id)) {
                Log.d(TAG," isIdInVFSFolderList item found ");
                return mItem.mItemUid;
            }
        }
        return AvrcpControllerConstants.DEFAULT_FOLDER_ID;
    }
    public long isIdInVFSMediaList(String id) {
        if ((mMediaItemList == null) || (mMediaItemList.isEmpty())) {
            Log.e(TAG," MediaList empty ");
            return AvrcpControllerConstants.DEFAULT_FOLDER_ID;
        }
        for (TrackInfo mTrackInfo: mMediaItemList) {
            if (Long.toString(mTrackInfo.mItemUid).equals(id)) {
                Log.d(TAG," isIdInVFSMediaList item found ");
                return mTrackInfo.mItemUid;
            }
        }
        return AvrcpControllerConstants.DEFAULT_FOLDER_ID;
    }
    public boolean isIdInSearchList(long id) {
        if ((mSearchList == null) || (mSearchList.isEmpty())) return false;
        for (TrackInfo mTrackInfo: mSearchList) {
            if (id == mTrackInfo.mItemUid)
                return true;
        }
        return false;
    }
    /*
     * This api is called only from folderUp. We will check one less from top
     */
    public int isIdInFolderStack(String id) {
        if ((mFolderStack == null) || (mFolderStack.size() < 2))
            return AvrcpControllerConstants.DEFAULT_LIST_INDEX;
        int index = mFolderStack.size() - 2;
        for (;index >= 0; index --) {
            if (mFolderStack.get(index).folderUid.equals(id))
                return (mFolderStack.size() - 1) - index;
        }
        return AvrcpControllerConstants.DEFAULT_LIST_INDEX;
    }
    public int fetchVFSThumbNail() {
        /*
         * Is BIP Fetch is in progress
         */
        if (!mDevice.isBipConnected())
            return AvrcpControllerConstants.ERROR_BIP_NOT_CONNECTED;
        if (mDevice.isBipFetchInProgress())
            return AvrcpControllerConstants.ERROR_BIP_FETCH_IN_PROGRESS;
        if ((mMediaItemList == null) || (mMediaItemList.isEmpty()))
            return AvrcpControllerConstants.ERROR_BIP_FETCH_LIST_EMPTY;
        /*
         * We will check which track has valid coverArtHandle but imageLcoation is empty
         */
        for (TrackInfo mTrackInfo: mMediaItemList) {
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
    public void updateVFSThumbNail(String mCoverArtHandle, String mImageLocation) {
        Log.d(TAG," updateVFSThumbNail HNDL" + mCoverArtHandle + " Loc: "+ mImageLocation);
        if ((mMediaItemList == null) || (mMediaItemList.isEmpty())) return;
        for (TrackInfo mTrackInfo: mMediaItemList) {
            if((mTrackInfo.mCoverArtHandle.equals(mCoverArtHandle) && (mImageLocation != null)))
                mTrackInfo.mThumbNailLocation = mImageLocation;
        }
    }
    public int fetchSearchThumbNail() {
        /*
         * Is BIP Fetch is in progress
         */
        if (!mDevice.isBipConnected())
            return AvrcpControllerConstants.ERROR_BIP_NOT_CONNECTED;
        if (mDevice.isBipFetchInProgress())
            return AvrcpControllerConstants.ERROR_BIP_FETCH_IN_PROGRESS;
        if ((mSearchList == null) || (mSearchList.isEmpty()))
            return AvrcpControllerConstants.ERROR_BIP_FETCH_LIST_EMPTY;
        /*
         * We will check which track has valid coverArtHandle but imageLcoation is empty
         */
        for (TrackInfo mTrackInfo: mSearchList) {
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
    public void updateSearchListThumbNail(String mCoverArtHandle, String mImageLocation) {
        Log.d(TAG," updateSearchListThumbNail HNDL" + mCoverArtHandle + " Loc: "+ mImageLocation);
        if ((mSearchList == null) || (mSearchList.isEmpty())) return;
        for (TrackInfo mTrackInfo: mSearchList) {
            if((mTrackInfo.mCoverArtHandle.equals(mCoverArtHandle) && (mImageLocation != null)))
                mTrackInfo.mThumbNailLocation = mImageLocation;
        }
    }
}
