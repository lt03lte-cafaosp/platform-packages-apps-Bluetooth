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
 * Provides helper classes used by other AvrcpControllerClasses.
 * Don't change this file without changing HAL Constants in bt_rc.h
 */

final class AvrcpControllerConstants {

    /*
     * Debug flags
     */
    public static final boolean DBG = true;
    public static final boolean VDBG = true;
    /*
     * Scopes of operation
     */
    public static final int AVRCP_SCOPE_MEDIA_PLAYLIST = 0;
    public static final int AVRCP_SCOPE_VFS = 1;
    public static final int AVRCP_SCOPE_SEARCH = 2;
    public static final int AVRCP_SCOPE_NOW_PLAYING = 3;
    public static final int AVRCP_SCOPE_NONE = 4;
    /*
     * Remote features
     */
    public static final byte BTRC_FEAT_NONE = 0;
    public static final byte BTRC_FEAT_METADATA = 1;
    public static final byte BTRC_FEAT_ABSOLUTE_VOLUME = 2;
    public static final byte BTRC_FEAT_BROWSE = 4;
    public static final byte BTRC_FEAT_COVER_ART = 8;

    /*
     *Element Id Values for GetMetaData
    */
    public static final int MEDIA_ATTRIBUTE_TITLE = 0x01;
    public static final int MEDIA_ATTRIBUTE_ARTIST_NAME = 0x02;
    public static final int MEDIA_ATTRIBUTE_ALBUM_NAME = 0x03;
    public static final int MEDIA_ATTRIBUTE_TRACK_NUMBER = 0x04;
    public static final int MEDIA_ATTRIBUTE_TOTAL_TRACK_NUMBER = 0x05;
    public static final int MEDIA_ATTRIBUTE_GENRE = 0x06;
    public static final int MEDIA_ATTRIBUTE_PLAYING_TIME = 0x07;
    public static final int MEDIA_ATTRIBUTE_COVER_ART_HANDLE = 0x08;

    /*
     * Default values for each of the items
    */
    public static final int TRACK_NUM_INVALID = 0xFF;
    public static final int STATUS_INVALID = 0xFF;
    public static final String TITLE_INVALID = "NOT_SUPPORTED";
    public static final String ARTIST_NAME_INVALID = "NOT_SUPPORTED";
    public static final String COVER_ART_HANDLE_INVALID = "NOT_SUPPORTED";
    public static final String COVER_ART_LOCATION_INVALID = "EMPTY";
    public static final String ALBUM_NAME_INVALID = "NOT_SUPPORTED";
    public static final int TOTAL_TRACKS_INVALID = 0xFFFFFFFF;
    public static final String GENRE_INVALID = "NOT_SUPPORTED";
    public static final int PLAYING_TIME_INVALID = 0xFF;
    public static final int TOTAL_TRACK_TIME_INVALID = 0xFF;
    public static final String REPEAT_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String SHUFFLE_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String SCAN_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String EQUALIZER_STATUS_INVALID = "NOT_SUPPORTED";
    public static final String DEFAULT_NAME = "EMPTY";

    /*
     * Values for SetPlayerApplicationSettings
    */
    public static final byte ATTRIB_EQUALIZER_STATUS = 0x01;
    public static final byte ATTRIB_REPEAT_STATUS = 0x02;
    public static final byte ATTRIB_SHUFFLE_STATUS = 0x03;
    public static final byte ATTRIB_SCAN_STATUS = 0x04;

    public static final byte EQUALIZER_STATUS_OFF = 0x01;
    public static final byte EQUALIZER_STATUS_ON = 0x02;

    public static final byte REPEAT_STATUS_OFF = 0x01;
    public static final byte REPEAT_STATUS_SINGLE_TRACK_REPEAT = 0x02;
    public static final byte REPEAT_STATUS_ALL_TRACK_REPEAT = 0x03;
    public static final byte REPEAT_STATUS_GROUP_REPEAT = 0x04;

    public static final byte SHUFFLE_STATUS_OFF = 0x01;
    public static final byte SHUFFLE_STATUS_ALL_TRACK_SHUFFLE = 0x02;
    public static final byte SHUFFLE_STATUS_GROUP_SHUFFLE = 0x03;

    public static final byte SCAN_STATUS_OFF = 0x01;
    public static final byte SCAN_STATUS_ALL_TRACK_SCAN = 0x02;
    public static final byte SCAN_STATUS_GROUP_SCAN = 0x03;

    /*
     *  Play State Values
     */
    public static final int PLAY_STATUS_STOPPED = 0x00;
    public static final int PLAY_STATUS_PLAYING = 0x01;
    public static final int PLAY_STATUS_PAUSED = 0x02;
    public static final int PLAY_STATUS_FWD_SEEK = 0x03;
    public static final int PLAY_STATUS_REV_SEEK = 0x04;
    public static final int PLAY_STATUS_ERROR = 0xFF;
    /*
     * System Status
     */
    public static final int SYSTEM_POWER_ON = 0x00;
    public static final int SYSTEM_POWER_OFF = 0x01;
    public static final int SYSTEM_UNPLUGGED = 0x02;
    public static final int SYSTEM_STATUS_UNDEFINED = 0xFF;
    /*
     * Battery Status
     */
    public static final int BATT_POWER_NORMAL = 0x00;
    public static final int BATT_POWER_WARNING = 0x01;
    public static final int BATT_POWER_CRITICAL = 0x02;
    public static final int BATT_POWER_EXTERNAL = 0x03;
    public static final int BATT_POWER_FULL_CHARGE = 0x04;
    public static final int BATT_POWER_UNDEFINED = 0xFF;

    public static final int NOTIFICATION_RSP_TYPE_INTERIM = 0x00;
    public static final int NOTIFICATION_RSP_TYPE_CHANGED = 0x01;
    /*
     * Base value for absolute volume
     */
    static final int ABS_VOL_BASE = 127;

    public static final int MESSAGE_SEND_PASS_THROUGH_CMD = 1;
    public static final int MESSAGE_SEND_SET_CURRENT_PLAYER_APPLICATION_SETTINGS = 2;
    public static final int MESSAGE_SEND_GROUP_NAVIGATION_CMD = 3;
    public static final int MESSAGE_CONNECT_BIP = 4;
    public static final int MESSAGE_SEND_PLAY_ITEM = 5;
    public static final int MESSAGE_ADD_TO_NPL = 6;
    /* Browsing Commands, where scope is relevant */
    public static final int MESSAGE_GET_TOTAL_NUM_ITEMS = 50;
    public static final int MESSAGE_FETCH_NOW_PLAYING_LIST = 51;
    public static final int MESSAGE_FETCH_VFS_LIST = 52;
    public static final int MESSAGE_FETCH_SEARCH_LIST = 53;
    public static final int MESSAGE_SET_BROWSED_PLAYER = 54;
    public static final int MESSAGE_SET_ADDRESSED_PLAYER = 55;
    public static final int MESSAGE_SEND_CHANGE_PATH = 56;
    public static final int MESSAGE_GET_NUM_ITEMS_SEARCH = 57;
    public static final int MESSAGE_BROWSE_DISCONNECT_OBEX = 58;
    public static final int MESSAGE_BROWSE_CONNECT_OBEX = 59;


    public static final int MESSAGE_PROCESS_SUPPORTED_PLAYER_APP_SETTING = 101;
    public static final int MESSAGE_PROCESS_PLAYER_APP_SETTING_CHANGED = 102;
    public static final int MESSAGE_PROCESS_SET_ABS_VOL_CMD = 103;
    public static final int MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION = 104;
    public static final int MESSAGE_PROCESS_TRACK_CHANGED = 105;
    public static final int MESSAGE_PROCESS_PLAY_POS_CHANGED = 106;
    public static final int MESSAGE_PROCESS_PLAY_STATUS_CHANGED = 107;
    public static final int MESSAGE_PROCESS_BIP_CONNECTED = 108;
    public static final int MESSAGE_PROCESS_BIP_DISCONNECTED = 109;
    public static final int MESSAGE_PROCESS_THUMB_NAIL_FETCHED = 110;
    public static final int MESSAGE_PROCESS_IMAGE_FETCHED = 111;
    public static final int MESSAGE_PROCESS_SUPPORTED_PLAYER = 112;

    public static final int MESSAGE_PROCESS_GET_TOTAL_NUM_ITEMS = 550;
    public static final int MESSAGE_PROCESS_BROWSE_FOLDER_RESPONSE = 551;
    public static final int MESSAGE_PROCESS_PENDING_BROWSING_COMMANDS = 552;
    public static final int MESSAGE_PROCESS_SET_BROWSED_PLAYER_RSP = 554;
    public static final int MESSAGE_PROCESS_SET_ADDRESSED_PLAYER_RSP = 555;
    public static final int MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED = 556;
    public static final int MESSAGE_PROCESS_PATH_CHANGED = 557;
    public static final int MESSAGE_PROCESS_SEARCH_ITEM_RSP = 558;
    public static final int MESSAGE_PROCESS_NPL_LIST_UPDATE = 559;
    public static final int MESSAGE_PROCESS_UIDS_CHANGED = 560;

    public static final int MESSAGE_PROCESS_RC_FEATURES = 1100;
    public static final int MESSAGE_PROCESS_CONNECTION_CHANGE = 1200;

    public static String dumpMessageString(int message) {
        String str = "UNKNOWN";
        switch (message) {
            case MESSAGE_SEND_PASS_THROUGH_CMD:str = "REQ_PASS_THROUGH_CMD";break;
            case MESSAGE_SEND_SET_CURRENT_PLAYER_APPLICATION_SETTINGS:
                str="REQ_SET_PLAYER_APP_SETTING";break;
            case MESSAGE_SEND_GROUP_NAVIGATION_CMD:str = "REQ_GRP_NAV_CMD";break;
            case MESSAGE_PROCESS_SUPPORTED_PLAYER_APP_SETTING:
                str = "CB_SUPPORTED_PLAYER_APP_SETTING";break;
            case MESSAGE_PROCESS_PLAYER_APP_SETTING_CHANGED:
                str = "CB_PLAYER_APP_SETTING_CHANGED";break;
            case MESSAGE_PROCESS_SET_ABS_VOL_CMD:str = "CB_SET_ABS_VOL_CMD";break;
            case MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION:str = "CB_REGISTER_ABS_VOL";break;
            case MESSAGE_PROCESS_TRACK_CHANGED:str = "CB_TRACK_CHANGED";break;
            case MESSAGE_PROCESS_PLAY_POS_CHANGED:str = "CB_PLAY_POS_CHANGED";break;
            case MESSAGE_PROCESS_PLAY_STATUS_CHANGED:str = "CB_PLAY_STATUS_CHANGED";break;
            case MESSAGE_PROCESS_RC_FEATURES:str = "CB_RC_FEATURES";break;
            case MESSAGE_PROCESS_CONNECTION_CHANGE:str = "CB_CONN_CHANGED";break;
            case MESSAGE_PROCESS_BIP_CONNECTED:str = "BIP_CONNECTED";break;
            case MESSAGE_PROCESS_THUMB_NAIL_FETCHED:str = "THUMB_NAIL_FETCHED";break;
            case MESSAGE_PROCESS_IMAGE_FETCHED:str = "IMAGE_FETCHED";break;
            case MESSAGE_PROCESS_BIP_DISCONNECTED:str = "BIP_DISCONNECTED";break;
            case MESSAGE_CONNECT_BIP: str = "CONNECT_BIP"; break;
            case MESSAGE_SEND_PLAY_ITEM: str = "REQ_PLAY_ITEM"; break;
            case MESSAGE_ADD_TO_NPL: str = "REQ_ADD_TO_NPL";break;
            case MESSAGE_GET_TOTAL_NUM_ITEMS: str = "REQ_GET_TOTAL_NUM_ITEMS";break;
            case MESSAGE_FETCH_NOW_PLAYING_LIST: str = " FETCH_NPL_LIST"; break;
            case MESSAGE_FETCH_VFS_LIST: str = "FETCH_VFS_LIST"; break;
            case MESSAGE_FETCH_SEARCH_LIST : str = " FETCH_SEARCH_LIST"; break;
            case MESSAGE_SET_BROWSED_PLAYER : str = "REQ_SET_BROWSED_PLAYER";break;
            case MESSAGE_SET_ADDRESSED_PLAYER: str = " REQ_SET_ADDRESSED_PLAYER"; break;
            case MESSAGE_SEND_CHANGE_PATH: str = " REQ_CHANGE_PATH"; break;
            case MESSAGE_GET_NUM_ITEMS_SEARCH: str = " GET_NUM_ITEMS_SEARCH"; break;
            case MESSAGE_BROWSE_DISCONNECT_OBEX: str = " MESSAGE_BROWSE_DISCONNECT_OBEX"; break;
            case MESSAGE_BROWSE_CONNECT_OBEX: str = " MESSAGE_BROWSE_CONNECT_OBEX"; break;
            case MESSAGE_PROCESS_GET_TOTAL_NUM_ITEMS: str = " CB_GET_NUM_ITEMS"; break;
            case MESSAGE_PROCESS_BROWSE_FOLDER_RESPONSE: str = " CB_BROWSE_FOLDER_RSP"; break;
            case MESSAGE_PROCESS_PENDING_BROWSING_COMMANDS: str="PROCESS_PENDING_BROWSE_CMDS"; break;
            case MESSAGE_PROCESS_SET_BROWSED_PLAYER_RSP: str=" CB_SET_BROWSE_PLAYER_RSP"; break;
            case MESSAGE_PROCESS_SET_ADDRESSED_PLAYER_RSP: str=" CB_SET_ADDR_PLAYER_RSP"; break;
            case MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED: str=" CB_ADDR_PLAYER_CHANGED"; break;
            case MESSAGE_PROCESS_PATH_CHANGED: str=" CB_PATH_CHANGED"; break;
            case MESSAGE_PROCESS_SEARCH_ITEM_RSP: str=" CB_SEARCH_ITEM_RSP"; break;
            case MESSAGE_PROCESS_NPL_LIST_UPDATE: str=" CB_NPL_LIST_CHANGED"; break;
            case MESSAGE_PROCESS_UIDS_CHANGED: str=" CB_UIDS_CHANGED"; break;
            case MESSAGE_PROCESS_SUPPORTED_PLAYER: str = " CB_SUPPORTED_PLAYERS"; break;
            default:
                str = Integer.toString(message);
                break;
        }
        return str;
    }

    /* Absolute Volume Notification State */
    /* if we are in this state, we would send vol update to remote */
    public static final int SEND_VOLUME_CHANGE_RSP = 0;
    /* if we are in this state, we would not send vol update to remote */
    public static final int DEFER_VOLUME_CHANGE_RSP = 1;
    public static final int VOLUME_LABEL_UNDEFINED = 0xFF;

    /* For PTS certification we have to send few PDUs when PTS requests
     * We implement that by sending pass through command with F1 and F2 Ids.
     * We don't want any new interface for this.
     */

    public static final int PTS_GET_ELEMENT_ATTRIBUTE_ID = 0x71;
    public static final int PTS_GET_PLAY_STATUS_ID = 0x72;
    public static final int PTS_VFS_CA_ID = 0x73;
    public static final int PTS_GET_ITEM_VFS_ID = 0x51;
    //public static final int PTS_GET_IMG    = 0x74;

    /*
     * image Type
     */
    public static final int COVER_ART_THUMBNAIL = 0x00;
    public static final int COVER_ART_IMAGE = 0x01;
    public static final int DEFAULT_PSM = 0xFF;
    /*
     * Error cases
     */
    public static final int NO_ERROR = 0x00;
    public static final int ERROR_BIP_NOT_CONNECTED = 0x01;
    public static final int ERROR_BIP_HANDLE_NOT_VALID = 0x02;
    public static final int IMAGE_FETCHED = 0x03;
    public static final int ALL_THUMBNAILS_FETCHED = 0x04;
    public static final int FETCHING_IMAGE = 0x05;
    public static final int FETCHING_THUMBNAIL = 0x06;
    public static final int ERROR_BIP_FETCH_IN_PROGRESS = 0x07;
    public static final int ERROR_BIP_FETCH_LIST_EMPTY = 0x08;
    /*
     * Default Player ID
     */
    public static final char DEFAULT_PLAYER_ID = 0xFFFF;
    public static final long DEFAULT_FOLDER_ID = 0xFFFFFFFF;
    /*
     * Browsing
     */
    public static final int PLAYER_FEATURE_MASK_SIZE = 16;
    public static final int DEFAULT_BROWSE_END_INDEX = 2000;
    public static final int DEFAULT_LIST_INDEX = 0xFFFF;
    public static final String AVRCP_BROWSE_ROOT_FOLDER = "__VFS_ROOT__";
    public static final byte DIRECTION_UP   = 0x00;
    public static final byte DIRECTION_DOWN = 0x01;
    public static final int UIDS_PERSISTENT = 66;

    /*
     * Media Type. Folder Type, Item Type
     */
    public static final int MEDIA_TYPE_AUDIO   = 0x00;
    public static final int MEDIA_TYPE_VIDEO   = 0x01;
    public static final int MEDIA_TYPE_ERROR   = 0xFF;
    public static final int FOLDER_TYPE_MIXED  = 0x00;
    public static final int FOLDER_TYPE_TITLES = 0x01;
    public static final int FOLDER_TYPE_ALBUMS = 0x02;
    public static final int FOLDER_TYPE_ARTISTS = 0x03;
    public static final int FOLDER_TYPE_GENRES = 0x04;
    public static final int FOLDER_TYPE_PLAYLISTS = 0x05;
    public static final int FOLDER_TYPE_YEARS = 0x06;
    public static final int FOLDER_TYPE_ERROR = 0xFF;
    public static final int BTRC_TYPE_MEDIA_PLAYER = 0x01;
    public static final int BTRC_TYPE_FOLDER = 0x02;
    public static final int BTRC_TYPE_MEDIA_ELEMENT = 0x03;
    public static final int BTRC_TYPE_MEDIA_ERROR = 0xFF;
    public static final int FOLDER_TYPE_PLAYABLE = 0x01;
    public static final int FOLDER_TYPE_NOT_PLAYABLE = 0x00;
    /*
     * AVRCP defined errors
     */
    public static final int  AVRC_RET_BAD_CMD        = 0x00;
    public static final int  AVRC_RET_BAD_PARAM      = 0x01;
    public static final int  AVRC_RET_NOT_FOUND      = 0x02;
    public static final int  AVRC_RET_INTERNAL_ERR   = 0x03;
    public static final int  AVRC_RET_NO_ERROR       = 0x04;
    public static final int  AVRC_RET_UID_CHANGED    = 0x05;
    public static final int  AVRC_RET_BAD_DIR        = 0x07;
    public static final int  AVRC_RET_NOT_DIR        = 0x08;
    public static final int  AVRC_RET_NOT_EXIST      = 0x09;
    public static final int  AVRC_RET_BAD_SCOPE      = 0x0a;
    public static final int  AVRC_RET_BAD_RANGE      = 0x0b;
    public static final int  AVRC_RET_UID_IS_DIR     = 0x0c;
    public static final int  AVRC_RET_IN_USE         = 0x0d;
    public static final int  AVRC_RET_NOW_LIST_FULL  = 0x0e;
    public static final int  AVRC_RET_SEARCH_NOT_SUP = 0x0f;
    public static final int  AVRC_RET_SEARCH_BUSY    = 0x10;
    public static final int  AVRC_RET_BAD_PLAYER_ID  = 0x11;
    public static final int  AVRC_RET_PLAYER_N_BR    = 0x12;
    public static final int  AVRC_RET_PLAYER_N_ADDR  = 0x13;
    public static final int  AVRC_RET_BAD_SEARCH_RES = 0x14;
    public static final int  AVRC_RET_NO_AVAL_PLAYER = 0x15;
    public static final int  AVRC_RET_ADDR_PLAYER_CHG = 0x16;
    public static final int  AVRC_RET_STS_TIMEOUT    = 0x5E;
    public static final int  AVRC_CHARSET_UTF8 = 0x006A;
}
