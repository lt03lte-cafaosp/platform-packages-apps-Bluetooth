/*
 * Copyright (c) 2016, The Linux Foundation. All rights reserved.
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

import android.bluetooth.BluetoothAvrcpController;
import android.bluetooth.BluetoothAvrcpPlayerSettings;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;


import android.media.AudioAttributes;
import android.media.MediaDescription;
import android.os.Handler;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.os.ResultReceiver;
import android.service.media.MediaBrowserService;
import android.media.session.PlaybackState;
import com.android.bluetooth.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import android.util.Log;
import java.nio.charset.Charset;
import java.nio.ByteBuffer;
import java.util.Stack;

/**
 * Provides Bluetooth AVRCP Controller profile, as a service in the Bluetooth application.
 * @hide
 */
public class AvrcpControllerBrowseService extends MediaBrowserService {
    private static final boolean DBG = AvrcpControllerConstants.DBG;
    private static final boolean VDBG = AvrcpControllerConstants.VDBG;
    private static final String TAG = "AvrcpControllerBrowseService";
    private static AvrcpControllerBrowseService sAvrcpControllerBrowseService;
    /* ID to handle Virtual File System Root */
    private MediaSession  mSession;
    private Result mPendingResult = null;
    Object resetRoot = null;
    boolean waitForResetRoot = false;

    @Override
    public void onCreate() {
        Log.d(TAG, " onCreate Called");
        super.onCreate();
        setAvrcpControllerBrowseService(this);
        /* Create a new MediaSession */
        mSession = new MediaSession(this, "AvrcpControllerBrowseService");
        setSessionToken(mSession.getSessionToken());
        mSession.setCallback(new MediaSessionReceiverCallback());
        mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.
                FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSession.setPlaybackToLocal(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
        /* Set Current Metadata and Playback State */
        if (AvrcpControllerService.getAvrcpControllerService() != null) {
            mSession.setMetadata(getCurrentMetaData());
            mSession.setPlaybackState(getCurrentPlayBackState());
        }
        resetRoot = new Object();
        waitForResetRoot = false;
        mSession.setActive(true);
    }
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        Log.d(TAG," onStartCommand called ");
        /*
         * We don't want service to be automatically restarted
         * It should be explicitly started again.
         */
        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
        Log.d(TAG, " onDestroy ");
        mPendingResult = null;
        clearAvrcpControllerBrowseService();
        if (mSession != null) {
            mSession.release();
        }
        stopSelf();
    }
    @Override
    public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
        Log.d(TAG, " onGetRoot called, clientUid = " + clientUid);
        AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
        if ((ctrlService == null) || (ctrlService.GetBrowsedPlayer() == null)) {
            Log.e(TAG," onGetRoot, Browsed player not set ");
            return null;
        }
        synchronized (resetRoot) {
            int ret = ctrlService.browseToRoot();
            Log.d(TAG," browseToRoot returned "+ ret);
            if (ret == AvrcpControllerConstants.STATUS_INVALID)
                return null;
            if (ret > 0) {
                try {
                    waitForResetRoot = true;
                    Log.d(TAG, " onGetRoot waiting for resetRoot ");
                    resetRoot.wait();
                } catch (InterruptedException e) {
                    Log.e(TAG, " interrrupted in OnGetRoot ");
                }
                waitForResetRoot = false;
            }
        }
        Log.d(TAG, " returning BrowseRoot ");
        /* make current scope as browsing */
        return new BrowserRoot(AvrcpControllerConstants.AVRCP_BROWSE_ROOT_FOLDER, null);
    }
    /* onLoadChildren can only be used
     * */
    @Override
    public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
        Log.d(TAG," onLoadChildren parentId " + parentMediaId);
        mPendingResult = result;
        AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
        if (ctrlService == null) {
            Log.e(TAG," onLoadChildren ctrl service not up, return empty list ");
            result.sendResult(Collections.<MediaItem>emptyList());
            mPendingResult = null;
            return;
        }
        if(parentMediaId.contains(":")) {
            /* check for up or down */
            String[] feilds = parentMediaId.split(":");
            Log.d(TAG,"onLoadChildren id =" + feilds[0] + " dir = " + feilds[1]);
            if(feilds[1].equals(BluetoothAvrcpController.BROWSE_COMMAND_BROWSE_FOLDER_UP)) {
                if (!ctrlService.loadFolderUp(feilds[0])) {
                    Log.e(TAG," loadFolderUp folder_up Item not found, return empty list ");
                    result.sendResult(Collections.<MediaItem>emptyList());
                    mPendingResult = null;
                    return;
                }
            }
            else if (feilds[1].equals(BluetoothAvrcpController.BROWSE_COMMAND_BROWSE_FOLDER_DOWN)) {
                if (!ctrlService.loadFolderDown(feilds[0])) {
                    Log.e(TAG," loadFolderDown folder_up Item not found, return empty list ");
                    result.sendResult(Collections.<MediaItem>emptyList());
                    mPendingResult = null;
                    return;
                }
            }
            else {
                if (!ctrlService.refreshCurrentFolder(feilds[0])) {
                    Log.e(TAG," refreshCurrentFolder Item not found, return empty list ");
                    result.sendResult(Collections.<MediaItem>emptyList());
                    mPendingResult = null;
                    return;
                }
            }
        }
        else {
            /* This is for refresh */
            if (!ctrlService.refreshCurrentFolder(parentMediaId)) {
                Log.e(TAG," onLoadChildren Item not found, return empty list ");
                result.sendResult(Collections.<MediaItem>emptyList());
                mPendingResult = null;
                return;
            }
        }
        result.detach();
    }
    public static synchronized AvrcpControllerBrowseService getAvrcpControllerBrowseService(){
        if (sAvrcpControllerBrowseService != null) {
            return sAvrcpControllerBrowseService;
        }
        Log.e(TAG, "getAvrcpControllerBrowseService(): returning  null");
        return null;
    }

    private static synchronized void setAvrcpControllerBrowseService(AvrcpControllerBrowseService
                                                                             instance) {
        if (instance != null ) {
            Log.d(TAG, "setAvrcpControllerBrowseService(): set to: " + instance);
            sAvrcpControllerBrowseService = instance;
        }
    }
    private static synchronized void clearAvrcpControllerBrowseService() {
        Log.d(TAG, "setAvrcpControllerBrowseService(): set to: " + "null");
        sAvrcpControllerBrowseService = null;
    }
    public void cleanup() {
        Log.d(TAG, " Cleanup");
        if (mSession != null)
            mSession.release();
        clearAvrcpControllerBrowseService();
        stopSelf();
    }
    public boolean sendPassThroughCommand (int keyCode) {
        AvrcpControllerService avrcpCtrlService = AvrcpControllerService.getAvrcpControllerService();
        if (avrcpCtrlService == null) return false;
        BluetoothDevice mDevice = avrcpCtrlService.getConnectedDevices().get(0);
        if (mDevice != null){
            Log.d(TAG," SendPassThruCmd sent - " + keyCode);
            avrcpCtrlService.sendPassThroughCmd(mDevice, keyCode,
                    BluetoothAvrcpController.KEY_STATE_PRESSED);
            avrcpCtrlService.sendPassThroughCmd(mDevice, keyCode,
                    BluetoothAvrcpController.KEY_STATE_RELEASED);
            return true;
        } else {
            Log.e(TAG, " SendPassThruCmd not sent - " + keyCode);
            return false;
        }
    }
    public void updateVFSList(RemoteFileSystem mRemoteFileSystem) {
        Log.d(TAG, " VFS Fetch Complete ");

        List<MediaItem> mediaItems = new ArrayList<MediaItem>();
        for (FolderItems mFolderItem: mRemoteFileSystem.mFolderItemList) {
            String folderName = mFolderItem.folderName;
            Bundle folderData =  new Bundle();
            folderData.putByte(BluetoothAvrcpController.EXTRA_FOLDER_TYPE, mFolderItem.folderType);
            int flag = MediaItem.FLAG_BROWSABLE;
            if (mFolderItem.isPlayable == 1 )
                flag = flag | MediaItem.FLAG_PLAYABLE;
            String folderId = Long.toString(mFolderItem.mItemUid);
            mediaItems.add(new MediaItem(new MediaDescription.Builder().setMediaId(folderId)
                    .setTitle(folderName).setExtras(folderData).build(), flag));
        }
        for (TrackInfo mTrackInfo: mRemoteFileSystem.mMediaItemList) {
            String mediaId = Long.toString(mTrackInfo.mItemUid);
            Bundle mediaData =  new Bundle();
            mediaData.putByte(BluetoothAvrcpController.EXTRA_MEDIA_TYPE, mTrackInfo.mediaType);
            mediaItems.add(new MediaItem(new MediaDescription.Builder().setMediaId(mediaId)
                    .setTitle(mTrackInfo.mTrackTitle).setExtras(mediaData).build(), MediaItem.FLAG_PLAYABLE));
        }
        if (mPendingResult != null) {
            Log.d(TAG," updating VFS Result, size = " + mediaItems.size());
            mPendingResult.sendResult(mediaItems);
            mPendingResult = null;
        }
    }
    public void updateSearchList(RemoteFileSystem mRemoteFileSystem) {
        Log.d(TAG," SearchList Fetch Complete ");
        if ((mSession == null) || (mRemoteFileSystem == null)) return;
        List<MediaSession.QueueItem> mPlayingQueue = new ArrayList<>();
        for (TrackInfo mTrackInfo: mRemoteFileSystem.mSearchList ) {
            String mediaId = Long.toString(mTrackInfo.mItemUid);
            Bundle mediaData =  new Bundle();
            mediaData.putByte(BluetoothAvrcpController.EXTRA_MEDIA_TYPE, mTrackInfo.mediaType);
            mPlayingQueue.add(new MediaSession.QueueItem(new MediaDescription.Builder()
                    .setMediaId(mediaId)
                    .setTitle(mTrackInfo.mTrackTitle).setExtras(mediaData).build(), mTrackInfo.mItemUid));
        }
        if (mSession.isActive()) {
            mSession.setQueue(mPlayingQueue);
            mSession.setQueueTitle("Search List");
        }
    }
    public void updateNowPlayingList(NowPlaying mNowPlayingList) {
        Log.d(TAG," NPL Fetch Complete ");
        if ((mSession == null) || (mNowPlayingList == null)) return;
        List<MediaSession.QueueItem> mPlayingQueue = new ArrayList<>();
        for (TrackInfo mTrackInfo: mNowPlayingList.mNowPlayingList ) {
            if (mTrackInfo.mItemUid == 0) continue;
            String mediaId = Long.toString(mTrackInfo.mItemUid);
            Bundle data = new Bundle();
            data.putString(MediaMetadata.METADATA_KEY_ALBUM, mTrackInfo.mAlbumTitle);
            data.putString(MediaMetadata.METADATA_KEY_ARTIST, mTrackInfo.mArtistName);
            data.putString(MediaMetadata.METADATA_KEY_GENRE, mTrackInfo.mGenre);
            data.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, mTrackInfo.mTrackNum);
            data.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, mTrackInfo.mTotalTracks);
            data.putLong(MediaMetadata.METADATA_KEY_DURATION, mTrackInfo.mTrackLen);
            data.putByte(BluetoothAvrcpController.EXTRA_MEDIA_TYPE, mTrackInfo.mediaType);
            mPlayingQueue.add(new MediaSession.QueueItem(new MediaDescription.Builder()
                    .setMediaId(mediaId)
                    .setExtras(data)
                    .setTitle(mTrackInfo.mTrackTitle).build(), mTrackInfo.mItemUid));
        }
        if (mSession.isActive()) {
            if (mPlayingQueue.isEmpty()) {
                Log.d(TAG," NPL List empty ");
                mSession.setQueue(Collections.<MediaSession.QueueItem>emptyList());
            }
            else {
                mSession.setQueue(mPlayingQueue);
            }
            mSession.setQueueTitle("Now Playing List");
        }
    }
    public void notifyResetRootDone() {
        synchronized (resetRoot) {
            if (waitForResetRoot) {
                Log.d(TAG, " notifyResetRootDone ");
                resetRoot.notify();
            }
        }
    }
    public void updateMetaData(MediaMetadata mediaMetadata) {
        if (mSession == null) return;
        if (mSession.isActive()) {
            mSession.setMetadata(mediaMetadata);
        }
    }
    public void updatePlayBackState(PlaybackState mPlayBackState) {
        if (mSession == null) return;
        if (mSession.isActive()) {
            mSession.setPlaybackState(mPlayBackState);
        }
    }
    public void refreshListCallback() {
        Log.d(TAG," refreshListCallback ");
        if (mSession == null) return;
        if (mSession.isActive()) {
            mSession.sendSessionEvent(BluetoothAvrcpController.SESSION_EVENT_REFRESH_LIST, null);
        }
    }
    private MediaMetadata getCurrentMetaData() {
        if (AvrcpControllerService.getAvrcpControllerService() != null) {
            AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
            return ctrlService.getMetaData(ctrlService.getConnectedDevices().get(0));
        }
        return null;
    }
    private PlaybackState getCurrentPlayBackState() {
        if (AvrcpControllerService.getAvrcpControllerService() != null) {
            AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
            PlaybackState state = new PlaybackState.Builder(ctrlService.getPlaybackState
                    (ctrlService.getConnectedDevices().get(0))).setActions(
                    PlaybackState.ACTION_FAST_FORWARD|PlaybackState.ACTION_PAUSE|
                    PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PLAY_FROM_MEDIA_ID|
                    PlaybackState.ACTION_PLAY_FROM_SEARCH|PlaybackState.ACTION_REWIND|
                    PlaybackState.ACTION_SKIP_TO_NEXT|PlaybackState.ACTION_SKIP_TO_PREVIOUS|
                    PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM|PlaybackState.ACTION_STOP).build();
        }
        return null;
    }
    private final class MediaSessionReceiverCallback extends MediaSession.Callback {
        @Override
        public void onPlay() {
            Log.d(TAG," MediaSession.Callback onPlay");
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_PLAY);
        }
        @Override
        public void onCommand(String command, Bundle args, ResultReceiver cb) {
            Log.d(TAG," MediaSession.Callback onCommand = " + command);
            if (command.equals(BluetoothAvrcpController.BROWSE_COMMAND_GET_NOW_PLAYING_LIST)) {
                AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
                if (ctrlService != null) {
                    ctrlService.fetchNowPlayingList();
                }
            }
            if (command.equals(BluetoothAvrcpController.BROWSE_COMMAND_ADD_TO_NOW_PLAYING_LIST)) {
                AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
                if (ctrlService != null) {
                    ctrlService.addToNowPlayingList(args.
                            getLong(BluetoothAvrcpController.EXTRA_ADD_TO_NOW_PLAYING_LIST));
                }
            }
        }
        @Override
        public void onCustomAction(String action, Bundle extras) {
            Log.d(TAG," MediaSession.Callback onCustomAction");
        }
        @Override
        public void onFastForward() {
            Log.d(TAG," MediaSession.Callback onFastForward");
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_FF);
        }
        @Override
        public boolean onMediaButtonEvent (Intent mediaButtonIntent) {
            Log.d(TAG," MediaSession.Callback  onMediaButtonEvent");
            return false;
        }
        @Override
        public void onPause() {
            Log.d(TAG," MediaSession.Callback onPause ");
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_PAUSE);
        }
        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            Log.d(TAG," MediaSession.Callback onPlayFromMediaid ");
            AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
            if (ctrlService == null) return;
            ctrlService.playFromMediaId(mediaId);
        }
        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            Log.d(TAG," MediaSession.Callback onPlayFromSearch ");
            AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
            if (ctrlService == null) return;
            ctrlService.fetchBySearchString(query);
        }
        @Override
        public void onRewind() {
            Log.d(TAG," MediaSession.Callback onRewind ");
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_REWIND);
        }
        @Override
        public void onSeekTo(long pos) {
            Log.d(TAG," MediaSession.Callback onSeekTo, Not Supported ");
        }
        @Override
        public void onSkipToNext() {
            Log.d(TAG, " MediaSession.Callback onSkipToNext ");
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_FORWARD);
        }
        @Override
        public void onSkipToPrevious() {
            Log.d(TAG," MediaSession.Callback onSkipToPrevious ");
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_BACKWARD);
        }
        @Override
        public void onSkipToQueueItem(long id) {
            Log.d(TAG," MediaSession.Callback onSkipToQueItem id = " + id);
            AvrcpControllerService ctrlService = AvrcpControllerService.getAvrcpControllerService();
            if (ctrlService == null) return;
            ctrlService.skipToQueItem(id);
        }
        @Override
        public void onStop() {
            Log.d(TAG," MediaSession.Callback onStop " );
            sendPassThroughCommand(BluetoothAvrcpController.PASS_THRU_CMD_ID_STOP);
        }
    }

}
