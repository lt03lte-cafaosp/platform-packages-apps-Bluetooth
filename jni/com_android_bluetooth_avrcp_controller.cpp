/*
 * Copyright (c) 2015, The Linux Foundation. All rights reserved.
 * Not a Contribution
 * Copyright (C) 2012 The Android Open Source Project
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

#define LOG_TAG "BluetoothAvrcpControllerJni"

#define LOG_NDEBUG 0

#include "com_android_bluetooth.h"
#include "hardware/bt_rc.h"
#include "utils/Log.h"
#include "android_runtime/AndroidRuntime.h"

#include <string.h>

namespace android {
static jmethodID method_handlePassthroughRsp;
static jmethodID method_onConnectionStateChanged;
static jmethodID method_getRcFeatures;
static jmethodID method_setplayerappsettingrsp;
static jmethodID method_handleplayerappsetting;
static jmethodID method_handleplayerappsettingchanged;
static jmethodID method_handleSetAbsVolume;
static jmethodID method_handleRegisterNotificationAbsVol;
static jmethodID method_handletrackchanged;
static jmethodID method_handleelementattrupdate;
static jmethodID method_handleplaypositionchanged;
static jmethodID method_handleplaystatuschanged;
static jmethodID method_handleGroupNavigationRsp;
static jmethodID method_handleavailableplayerslist;
static jmethodID method_handleGetTotalNumItems;
static jmethodID method_handleBrowseFolderResponse;
static jmethodID method_onAddressedPlayerChanged;
static jmethodID method_handleSetBrowsedPlayerRsp;
static jmethodID method_handleSetAddressedPlayerRsp;
static jmethodID method_handleChangePathRsp;
static jmethodID method_onNowPlayingListUpdate;
static jmethodID method_handleAddToNPLRsp;
static jmethodID method_handlePlayItemRsp;
static jmethodID method_handleSearchRsp;
static jmethodID method_OnUidsChanged;

static const btrc_ctrl_interface_t *sBluetoothAvrcpInterface = NULL;
static jobject mCallbacksObj = NULL;
static JNIEnv *sCallbackEnv = NULL;

static bool checkCallbackThread() {
    // Always fetch the latest callbackEnv from AdapterService.
    // Caching this could cause this sCallbackEnv to go out-of-sync
    // with the AdapterService's ENV if an ASSOCIATE/DISASSOCIATE event
    // is received
    sCallbackEnv = getCallbackEnv();

    JNIEnv* env = AndroidRuntime::getJNIEnv();
    if (sCallbackEnv != env || sCallbackEnv == NULL) return false;
    return true;
}

static void btavrcp_passthrough_response_callback(int id, int pressed) {
    ALOGI("%s", __FUNCTION__);
    ALOGI("id: %d, pressed: %d", id, pressed);

    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
        return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handlePassthroughRsp, (jint)id,
                                                                             (jint)pressed);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void btavrcp_groupnavigation_response_callback(int id, int pressed) {
    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__);
        return;
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleGroupNavigationRsp, (jint)id,
                                                                             (jint)pressed);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
}

static void btavrcp_connection_state_callback(bool state, bt_bdaddr_t* bd_addr) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);
    ALOGI("conn state: %d", state);

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr for connection state");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onConnectionStateChanged, (jboolean) state,
                                 addr);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_get_rcfeatures_callback(bt_bdaddr_t *bd_addr, int features, uint16_t ca_psm) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_getRcFeatures, addr, (jint)features,
           (jint) ca_psm);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_setplayerapplicationsetting_rsp_callback(bt_bdaddr_t *bd_addr,
                                                                    uint8_t accepted) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_setplayerappsettingrsp, addr, (jint)accepted);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void  btavrcp_get_mediaelementattribute_rsp_callback(bt_bdaddr_t *bd_addr, uint8_t num_attr,
                                                     btrc_element_attr_val_t *p_attrs) {

    /*
     * byteArray will be formatted like this: id,len,string
     * Assuming text feild to be null terminated.
     */
    jbyteArray addr;
    jintArray attribIds;
    jobjectArray stringArray;
    jstring str;
    jclass strclazz;
    jint i;
    ALOGI("%s", __FUNCTION__);
    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    attribIds = sCallbackEnv->NewIntArray(num_attr);
    if(!attribIds) {
        ALOGE(" failed to set new array for attribIds");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(addr);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*)bd_addr);

    strclazz = sCallbackEnv->FindClass("java/lang/String");
    stringArray = sCallbackEnv->NewObjectArray((jint)num_attr, strclazz, 0);
    if(!stringArray) {
        ALOGE(" failed to get String array");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(addr);
        sCallbackEnv->DeleteLocalRef(attribIds);
        return;
    }
    for(i = 0; i < num_attr; i++)
    {
        str = sCallbackEnv->NewStringUTF((char*)(p_attrs[i].text));
        if(!str) {
            ALOGE(" Unable to get str ");
            checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
            sCallbackEnv->DeleteLocalRef(addr);
            sCallbackEnv->DeleteLocalRef(attribIds);
            sCallbackEnv->DeleteLocalRef(stringArray);
            return;
        }
        sCallbackEnv->SetIntArrayRegion(attribIds, i, 1, (jint*)&(p_attrs[i].attr_id));
        sCallbackEnv->SetObjectArrayElement(stringArray, i,str);
        sCallbackEnv->DeleteLocalRef(str);
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handletrackchanged, addr,
         (jbyte)(num_attr), attribIds, stringArray);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(attribIds);
    /* TODO check do we need to delete str seperately or not */
    sCallbackEnv->DeleteLocalRef(stringArray);
    sCallbackEnv->DeleteLocalRef(strclazz);
}

static void btavrcp_playerapplicationsetting_callback(bt_bdaddr_t *bd_addr, uint8_t num_attr,
        btrc_player_app_attr_t *app_attrs, uint8_t num_ext_attr,
        btrc_player_app_ext_attr_t *ext_attrs) {
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;
    jbyteArray playerattribs;
    jint arraylen = 0;
    int i,k;

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if (!addr) {
        ALOGE("Fail to new jbyteArray bd addr ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*)bd_addr);
    /* TODO ext attrs
     * Flattening defined attributes: <id,num_values,values[]>
     */
    for (i = 0; i < num_attr; i++)
    {
        /*2 bytes for id and num */
        arraylen += 2 + app_attrs[i].num_val;
    }
    ALOGI(" arraylen %d", arraylen);
    playerattribs = sCallbackEnv->NewByteArray(arraylen);
    if(!playerattribs)
    {
        ALOGE("Fail to new jbyteArray playerattribs ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(addr);
        return;
    }
    k= 0;
    for (i = 0; (i < num_attr)&&(k < arraylen); i++)
    {
        sCallbackEnv->SetByteArrayRegion(playerattribs, k, 1, (jbyte*)&(app_attrs[i].attr_id));
        k++;
        sCallbackEnv->SetByteArrayRegion(playerattribs, k, 1, (jbyte*)&(app_attrs[i].num_val));
        k++;
        sCallbackEnv->SetByteArrayRegion(playerattribs, k, app_attrs[i].num_val,
                (jbyte*)(app_attrs[i].attr_val));
        k = k + app_attrs[i].num_val;
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleplayerappsetting, addr,
            playerattribs, (jint)arraylen);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(playerattribs);
}

static void btavrcp_playerapplicationsetting_changed_callback(bt_bdaddr_t *bd_addr,
                         btrc_player_settings_t *p_vals) {

    jbyteArray addr;
    jbyteArray playerattribs;
    int i, k, arraylen;
    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*)bd_addr);
    arraylen = p_vals->num_attr*2;
    playerattribs = sCallbackEnv->NewByteArray(arraylen);
    if(!playerattribs)
    {
        ALOGE("Fail to new jbyteArray playerattribs ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(addr);
        return;
    }
    /*
     * Flatening format: <id,val>
     */
    k = 0;
    for (i = 0; (i < p_vals->num_attr)&&( k < arraylen);i++)
    {
        sCallbackEnv->SetByteArrayRegion(playerattribs, k, 1, (jbyte*)&(p_vals->attr_ids[i]));
        k++;
        sCallbackEnv->SetByteArrayRegion(playerattribs, k, 1, (jbyte*)&(p_vals->attr_values[i]));
        k++;
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleplayerappsettingchanged, addr,
            playerattribs, (jint)arraylen);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(playerattribs);
}

static void btavrcp_set_abs_vol_cmd_callback(bt_bdaddr_t *bd_addr, uint8_t abs_vol,
        uint8_t label) {

    jbyteArray addr;
    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*)bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleSetAbsVolume, addr, (jbyte)abs_vol,
                                 (jbyte)label);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_register_notification_absvol_callback(bt_bdaddr_t *bd_addr, uint8_t label) {
    jbyteArray addr;

    ALOGI("%s", __FUNCTION__);

    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }

    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*)bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleRegisterNotificationAbsVol, addr,
                                 (jbyte)label);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_track_changed_callback(bt_bdaddr_t *bd_addr, uint8_t num_attr,
                           btrc_element_attr_val_t *p_attrs) {
    /*
     * byteArray will be formatted like this: id,len,string
     * Assuming text feild to be null terminated.
     */
    jbyteArray addr;
    jintArray attribIds;
    jobjectArray stringArray;
    jstring str;
    jclass strclazz;
    jint i;
    ALOGI("%s", __FUNCTION__);
    if (!checkCallbackThread()) {                                       \
        ALOGE("Callback: '%s' is not called on the correct thread", __FUNCTION__); \
        return;                                                         \
    }

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    attribIds = sCallbackEnv->NewIntArray(num_attr);
    if(!attribIds) {
        ALOGE(" failed to set new array for attribIds");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(addr);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*)bd_addr);

    strclazz = sCallbackEnv->FindClass("java/lang/String");
    stringArray = sCallbackEnv->NewObjectArray((jint)num_attr, strclazz, 0);
    if(!stringArray) {
        ALOGE(" failed to get String array");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        sCallbackEnv->DeleteLocalRef(addr);
        sCallbackEnv->DeleteLocalRef(attribIds);
        return;
    }
    for(i = 0; i < num_attr; i++)
    {
        str = sCallbackEnv->NewStringUTF((char*)(p_attrs[i].text));
        if(!str) {
            ALOGE(" Unable to get str ");
            checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
            sCallbackEnv->DeleteLocalRef(addr);
            sCallbackEnv->DeleteLocalRef(attribIds);
            sCallbackEnv->DeleteLocalRef(stringArray);
            return;
        }
        sCallbackEnv->SetIntArrayRegion(attribIds, i, 1, (jint*)&(p_attrs[i].attr_id));
        sCallbackEnv->SetObjectArrayElement(stringArray, i,str);
        sCallbackEnv->DeleteLocalRef(str);
    }

    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handletrackchanged, addr,
         (jbyte)(num_attr), attribIds, stringArray);
    checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(attribIds);
    /* TODO check do we need to delete str seperately or not */
    sCallbackEnv->DeleteLocalRef(stringArray);
    sCallbackEnv->DeleteLocalRef(strclazz);
}

static void btavrcp_play_position_changed_callback(bt_bdaddr_t *bd_addr, uint32_t song_len,
        uint32_t song_pos) {

    jbyteArray addr;
    ALOGI("%s", __FUNCTION__);

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleplaypositionchanged, addr,
         (jint)(song_len), (jint)song_pos);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_play_status_changed_callback(bt_bdaddr_t *bd_addr,
        btrc_play_status_t play_status) {
    jbyteArray addr;
    ALOGI("%s", __FUNCTION__);

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleplaystatuschanged, addr,
             (jbyte)play_status);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_available_players_update_callback (bt_bdaddr_t *bd_addr,
                                         btrc_folder_list_entries_t *p_folder_entries)
{
    ALOGI("%s", __FUNCTION__);
    jint uidCounter;
    jint numPlayers, index;
    jbyteArray addr;
    int i, k;
    jintArray subType;
    jintArray playerId;
    jbyteArray majorType, playStatus, featureMask;
    jobjectArray playerName;
    jclass strclazz;
    jstring str;
    jint id = 0;
    /*
     * If there is error in parsing, return from here itself
     */
    if (p_folder_entries->status != BTRC_STS_NO_ERROR)
        return;
    uidCounter = p_folder_entries->uid_counter;
    numPlayers = p_folder_entries->item_count;
    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    subType = sCallbackEnv->NewIntArray(numPlayers);
    playerId = sCallbackEnv->NewIntArray(numPlayers);
    majorType = sCallbackEnv->NewByteArray(numPlayers);
    playStatus = sCallbackEnv->NewByteArray(numPlayers);
    featureMask = sCallbackEnv->NewByteArray(numPlayers*BTRC_FEATURE_MASK_SIZE);
    strclazz = sCallbackEnv->FindClass("java/lang/String");
    playerName = sCallbackEnv->NewObjectArray((jint)numPlayers, strclazz, 0);
    for (i = 0; i < numPlayers; i++) {
        if (p_folder_entries->p_item_list[i].item_type != BTRC_TYPE_MEDIA_PLAYER)
            continue;
        id =  p_folder_entries->p_item_list[i].u.player.player_id;
        sCallbackEnv->SetIntArrayRegion(subType, i, 1, (jint*)&
                (p_folder_entries->p_item_list[i].u.player.sub_type));
        sCallbackEnv->SetIntArrayRegion(playerId, i, 1,(jint*)&(id));
        sCallbackEnv->SetByteArrayRegion(majorType, i, 1, (jbyte*)&
                (p_folder_entries->p_item_list[i].u.player.major_type));
        sCallbackEnv->SetByteArrayRegion(playStatus, i, 1, (jbyte*)&
                (p_folder_entries->p_item_list[i].u.player.play_status));
        sCallbackEnv->SetByteArrayRegion(featureMask, i, BTRC_FEATURE_MASK_SIZE, (jbyte*)
                (p_folder_entries->p_item_list[i].u.player.features));
        str = sCallbackEnv->NewStringUTF((char*)
                                           (p_folder_entries->p_item_list[i].u.player.name.p_str));
        sCallbackEnv->SetObjectArrayElement(playerName, i,str);
        sCallbackEnv->DeleteLocalRef(str);
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleavailableplayerslist, addr,
                                 uidCounter, numPlayers, subType, playerId, majorType, playStatus,
                                 featureMask, playerName);
    sCallbackEnv->DeleteLocalRef(subType);
    sCallbackEnv->DeleteLocalRef(playerId);
    sCallbackEnv->DeleteLocalRef(majorType);
    sCallbackEnv->DeleteLocalRef(playStatus);
    sCallbackEnv->DeleteLocalRef(featureMask);
    sCallbackEnv->DeleteLocalRef(strclazz);
    sCallbackEnv->DeleteLocalRef(playerName);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_addressed_player_update_callback (bt_bdaddr_t *bd_addr,
                                             uint16_t player_id, uint16_t uid_counter)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onAddressedPlayerChanged, addr,
                                 (jint)(player_id), (jint)uid_counter);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_set_browsed_player_rsp_callback (bt_bdaddr_t *bd_addr,
                                             uint8_t status, uint16_t uid_counter)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleSetBrowsedPlayerRsp, addr,
                                 (jbyte)(status), (jint)uid_counter);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_set_addressed_player_rsp_callback (bt_bdaddr_t *bd_addr, uint8_t status)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleSetAddressedPlayerRsp, addr,
                                 (jbyte)(status));
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_change_path_rsp_callback (bt_bdaddr_t *bd_addr, uint8_t status,
                                              uint32_t num_items)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleChangePathRsp, addr,
                                 (jbyte)(status), (jint)num_items);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_now_playing_list_update_callback (bt_bdaddr_t *bd_addr)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_onNowPlayingListUpdate, addr);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_add_to_now_playing_list_rsp_callback (bt_bdaddr_t *bd_addr,
                                                                    uint8_t status)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleAddToNPLRsp, addr, (jbyte)status);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_browse_folder_rsp_callback (bt_bdaddr_t *bd_addr,
                      uint8_t status, btrc_folder_list_entries_t *p_folder_entries)
{
    ALOGI("%s", __FUNCTION__);
    jint uidCounter;
    jint numItems, index;
    jbyteArray addr;
    jbyteArray itemTtype, type, playable, numAattr;
    jintArray attribIds;
    jlongArray uids;
    jobjectArray itemName, attribVal;
    jclass strclazz;
    jstring str;
    jbyte resultStatus;
    uint16_t folder_count = 0;
    uint16_t media_count = 0;
    uint16_t num_attributes = 0;
    uint8_t attr_count = 0;
    int i, k;
    /*
     * If there is error in parsing, return from here itself
     */

    uidCounter = p_folder_entries->uid_counter;
    numItems = p_folder_entries->item_count;
    resultStatus = p_folder_entries->status;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    if ((resultStatus != BTRC_STS_NO_ERROR) || (numItems == 0)) {
        /* Complete the response with empty values */
        sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleBrowseFolderResponse, addr, resultStatus,
                                     uidCounter, 0, NULL, NULL, NULL, NULL, NULL,
                                     NULL, NULL, NULL);
        sCallbackEnv->DeleteLocalRef(addr);
        return;
    }
    itemTtype = sCallbackEnv->NewByteArray(numItems);
    type = sCallbackEnv->NewByteArray(numItems);
    uids = sCallbackEnv->NewLongArray(numItems);

    // start filling values
    strclazz = sCallbackEnv->FindClass("java/lang/String");
    itemName = sCallbackEnv->NewObjectArray((jint)numItems, strclazz, 0);
    for (int i = 0; i < numItems; i++) {
        sCallbackEnv->SetByteArrayRegion(itemTtype, i, sizeof(uint8_t), (jbyte*)&
                (p_folder_entries->p_item_list[i].item_type));
        if (p_folder_entries->p_item_list[i].item_type == BTRC_TYPE_FOLDER) {
            folder_count++;
            sCallbackEnv->SetByteArrayRegion(type, i, sizeof(uint8_t), (jbyte*)&
                    (p_folder_entries->p_item_list[i].u.folder.type));
            sCallbackEnv->SetLongArrayRegion(uids, i, 1, (jlong*)&
                    (p_folder_entries->p_item_list[i].u.folder.uid));
            str = sCallbackEnv->NewStringUTF((char*)
                                           (p_folder_entries->p_item_list[i].u.folder.name.p_str));
            sCallbackEnv->SetObjectArrayElement(itemName, i,str);
        }
        else if (p_folder_entries->p_item_list[i].item_type == BTRC_TYPE_MEDIA_ELEMENT) {
            media_count++;
            sCallbackEnv->SetByteArrayRegion(type, i, sizeof(uint8_t), (jbyte*)&
                    (p_folder_entries->p_item_list[i].u.media.type));
            sCallbackEnv->SetLongArrayRegion(uids, i, 1, (jlong*)&
                    (p_folder_entries->p_item_list[i].u.media.uid));
            str = sCallbackEnv->NewStringUTF((char*)
                                            (p_folder_entries->p_item_list[i].u.media.name.p_str));
            sCallbackEnv->SetObjectArrayElement(itemName, i,str);
            num_attributes = num_attributes + p_folder_entries->p_item_list[i].u.media.attr_count;
        }
        sCallbackEnv->DeleteLocalRef(str);
    }

    playable = sCallbackEnv->NewByteArray(folder_count);
    numAattr = sCallbackEnv->NewByteArray(media_count);
    attribIds = sCallbackEnv->NewIntArray(num_attributes);
    attribVal = sCallbackEnv->NewObjectArray((jint)num_attributes, strclazz, 0);

    uint16_t attrib_index = 0;
    for (int i = 0; i < numItems; i++) {
        if (p_folder_entries->p_item_list[i].item_type == BTRC_TYPE_FOLDER) {
            sCallbackEnv->SetByteArrayRegion(playable, i, sizeof(uint8_t), (jbyte*)&
                    (p_folder_entries->p_item_list[i].u.folder.playable));
        }
        else if (p_folder_entries->p_item_list[i].item_type == BTRC_TYPE_MEDIA_ELEMENT) {
            attr_count = p_folder_entries->p_item_list[i].u.media.attr_count;
            sCallbackEnv->SetByteArrayRegion(numAattr, i, sizeof(uint8_t), (jbyte*)&(attr_count));
            for (k = 0; k < attr_count; k++) {
                sCallbackEnv->SetIntArrayRegion(attribIds, attrib_index, 1, (jint*)&
                        (p_folder_entries->p_item_list[i].u.media.p_attr_list[k].attr_id));
                str = sCallbackEnv->NewStringUTF((char*)
                             (p_folder_entries->p_item_list[i].u.media.p_attr_list[k].name.p_str));
                sCallbackEnv->SetObjectArrayElement(attribVal, attrib_index, str);
                sCallbackEnv->DeleteLocalRef(str);
                attrib_index++;
            }
        }
    }
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleBrowseFolderResponse, addr, resultStatus,
                                 uidCounter, numItems, itemTtype, uids, type, playable, itemName,
                                 numAattr, attribIds, attribVal);
    sCallbackEnv->DeleteLocalRef(addr);
    sCallbackEnv->DeleteLocalRef(itemTtype);
    sCallbackEnv->DeleteLocalRef(type);
    sCallbackEnv->DeleteLocalRef(uids);
    sCallbackEnv->DeleteLocalRef(itemName);
    sCallbackEnv->DeleteLocalRef(attribVal);
    sCallbackEnv->DeleteLocalRef(attribIds);
    sCallbackEnv->DeleteLocalRef(numAattr);
    sCallbackEnv->DeleteLocalRef(playable);
    sCallbackEnv->DeleteLocalRef(strclazz);
}

static void btavrcp_play_item_rsp_callback (bt_bdaddr_t *bd_addr, uint8_t status)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handlePlayItemRsp, addr, (jbyte)status);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_serach_rsp_callback (bt_bdaddr_t *bd_addr, uint8_t status,
                                            uint16_t uid_counter, uint32_t num_items)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleSearchRsp, addr, (jbyte)status,
                                 (jint)uid_counter, (jint)num_items);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_total_items_rsp_callback (bt_bdaddr_t *bd_addr,
                           uint8_t status, uint16_t uid_counter, uint32_t num_items)
{
    jbyteArray addr;
    ALOGI("%s", __FUNCTION__);

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_handleGetTotalNumItems, addr,
                  (jbyte)status, (jint)uid_counter, (jint)num_items);
    sCallbackEnv->DeleteLocalRef(addr);
}

static void btavrcp_uids_changed_callback (bt_bdaddr_t *bd_addr, uint16_t uid_counter)
{
    ALOGI("%s", __FUNCTION__);
    jbyteArray addr;

    addr = sCallbackEnv->NewByteArray(sizeof(bt_bdaddr_t));
    if ((!addr)) {
        ALOGE("Fail to get new array ");
        checkAndClearExceptionFromCallback(sCallbackEnv, __FUNCTION__);
        return;
    }
    sCallbackEnv->SetByteArrayRegion(addr, 0, sizeof(bt_bdaddr_t), (jbyte*) bd_addr);
    sCallbackEnv->CallVoidMethod(mCallbacksObj, method_OnUidsChanged, addr,(jint)uid_counter);
    sCallbackEnv->DeleteLocalRef(addr);
}

static btrc_ctrl_callbacks_t sBluetoothAvrcpCallbacks = {
    sizeof(sBluetoothAvrcpCallbacks),
    btavrcp_passthrough_response_callback,
    btavrcp_groupnavigation_response_callback,
    btavrcp_connection_state_callback,
    btavrcp_get_rcfeatures_callback,
    btavrcp_setplayerapplicationsetting_rsp_callback,
    btavrcp_get_mediaelementattribute_rsp_callback,
    btavrcp_playerapplicationsetting_callback,
    btavrcp_playerapplicationsetting_changed_callback,
    btavrcp_set_abs_vol_cmd_callback,
    btavrcp_register_notification_absvol_callback,
    btavrcp_track_changed_callback,
    btavrcp_play_position_changed_callback,
    btavrcp_play_status_changed_callback,
    btavrcp_available_players_update_callback,
    btavrcp_addressed_player_update_callback,
    btavrcp_set_browsed_player_rsp_callback,
    btavrcp_set_addressed_player_rsp_callback,
    btavrcp_change_path_rsp_callback,
    btavrcp_now_playing_list_update_callback,
    btavrcp_add_to_now_playing_list_rsp_callback,
    btavrcp_browse_folder_rsp_callback,
    btavrcp_play_item_rsp_callback,
    btavrcp_serach_rsp_callback,
    btavrcp_uids_changed_callback,
    btavrcp_total_items_rsp_callback
};

static void classInitNative(JNIEnv* env, jclass clazz) {
    method_handlePassthroughRsp =
        env->GetMethodID(clazz, "handlePassthroughRsp", "(II)V");

    method_handleGroupNavigationRsp =
        env->GetMethodID(clazz, "handleGroupNavigationRsp", "(II)V");

    method_onConnectionStateChanged =
        env->GetMethodID(clazz, "onConnectionStateChanged", "(Z[B)V");

    method_getRcFeatures =
        env->GetMethodID(clazz, "getRcFeatures", "([BII)V");

    method_setplayerappsettingrsp =
        env->GetMethodID(clazz, "setPlayerAppSettingRsp", "([BB)V");

    method_handleplayerappsetting =
        env->GetMethodID(clazz, "handlePlayerAppSetting", "([B[BI)V");

    method_handleplayerappsettingchanged =
        env->GetMethodID(clazz, "onPlayerAppSettingChanged", "([B[BI)V");

    method_handleSetAbsVolume =
        env->GetMethodID(clazz, "handleSetAbsVolume", "([BBB)V");

    method_handleRegisterNotificationAbsVol =
        env->GetMethodID(clazz, "handleRegisterNotificationAbsVol", "([BB)V");

    method_handletrackchanged =
        env->GetMethodID(clazz, "onTrackChanged", "([BB[I[Ljava/lang/String;)V");

    method_handleelementattrupdate =
        env->GetMethodID(clazz, "onElementAttributeUpdate", "([BB[I[Ljava/lang/String;)V");

    method_handleplaypositionchanged =
        env->GetMethodID(clazz, "onPlayPositionChanged", "([BII)V");

    method_handleplaystatuschanged =
            env->GetMethodID(clazz, "onPlayStatusChanged", "([BB)V");

    method_handleavailableplayerslist =
            env->GetMethodID(clazz, "handleAvailablePlayersList",
                             "([BIB[I[I[B[B[B[Ljava/lang/String;)V");

    method_handleGetTotalNumItems =
            env->GetMethodID(clazz, "onGetTotalNumItems", "([BBII)V");

    method_handleBrowseFolderResponse =
            env->GetMethodID(clazz, "handleBrowseFolderResponse",
                             "([BBII[B[J[B[B[Ljava/lang/String;[B[I[Ljava/lang/String;)V");

    method_onAddressedPlayerChanged =
            env->GetMethodID(clazz, "onAddressedPlayerChanged", "([BII)V");

    method_handleSetBrowsedPlayerRsp =
            env->GetMethodID(clazz, "handleSetBrowsedPlayerRsp", "([BBI)V");

    method_handleSetAddressedPlayerRsp =
            env->GetMethodID(clazz, "handleSetAddressedPlayerRsp", "([BB)V");

    method_handleChangePathRsp =
            env->GetMethodID(clazz, "handleChangePathRsp", "([BBI)V");

    method_onNowPlayingListUpdate =
            env->GetMethodID(clazz, "onNowPlayingListUpdate", "([B)V");

    method_handleAddToNPLRsp =
            env->GetMethodID(clazz, "handleAddToNPLRsp", "([BB)V");

    method_handlePlayItemRsp =
            env->GetMethodID(clazz, "handlePlayItemRsp", "([BB)V");

    method_handleSearchRsp =
            env->GetMethodID(clazz, "handleSearchRsp", "([BBII)V");

    method_OnUidsChanged =
            env->GetMethodID(clazz, "OnUidsChanged", "([BI)V");
    ALOGI("%s: succeeds", __FUNCTION__);
}

static void initNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;
    bt_status_t status;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothAvrcpInterface !=NULL) {
         ALOGW("Cleaning up Avrcp Interface before initializing...");
         sBluetoothAvrcpInterface->cleanup();
         sBluetoothAvrcpInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
         ALOGW("Cleaning up Avrcp callback object");
         env->DeleteGlobalRef(mCallbacksObj);
         mCallbacksObj = NULL;
    }

    if ( (sBluetoothAvrcpInterface = (btrc_ctrl_interface_t *)
          btInf->get_profile_interface(BT_PROFILE_AV_RC_CTRL_ID)) == NULL) {
        ALOGE("Failed to get Bluetooth Avrcp Controller Interface");
        return;
    }

    if ( (status = sBluetoothAvrcpInterface->init(&sBluetoothAvrcpCallbacks)) !=
         BT_STATUS_SUCCESS) {
        ALOGE("Failed to initialize Bluetooth Avrcp Controller, status: %d", status);
        sBluetoothAvrcpInterface = NULL;
        return;
    }

    mCallbacksObj = env->NewGlobalRef(object);
}

static void cleanupNative(JNIEnv *env, jobject object) {
    const bt_interface_t* btInf;

    if ( (btInf = getBluetoothInterface()) == NULL) {
        ALOGE("Bluetooth module is not loaded");
        return;
    }

    if (sBluetoothAvrcpInterface !=NULL) {
        sBluetoothAvrcpInterface->cleanup();
        sBluetoothAvrcpInterface = NULL;
    }

    if (mCallbacksObj != NULL) {
        env->DeleteGlobalRef(mCallbacksObj);
        mCallbacksObj = NULL;
    }
}

static jboolean sendPassThroughCommandNative(JNIEnv *env, jobject object, jbyteArray address,
                                                    jint key_code, jint key_state) {
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);

    ALOGI("key_code: %d, key_state: %d", key_code, key_state);

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ((status = sBluetoothAvrcpInterface->send_pass_through_cmd((bt_bdaddr_t *)addr,
            (uint8_t)key_code, (uint8_t)key_state))!= BT_STATUS_SUCCESS) {
        ALOGE("Failed sending passthru command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static jboolean sendGroupNavigationCommandNative(JNIEnv *env, jobject object, jbyteArray address,
                                                    jint key_code, jint key_state) {
    jbyte *addr;
    bt_status_t status;

    if (!sBluetoothAvrcpInterface) return JNI_FALSE;

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);

    ALOGI("key_code: %d, key_state: %d", key_code, key_state);

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return JNI_FALSE;
    }

    if ((status = sBluetoothAvrcpInterface->send_group_navigation_cmd((bt_bdaddr_t *)addr,
            (uint8_t)key_code, (uint8_t)key_state))!= BT_STATUS_SUCCESS) {
        ALOGE("Failed sending Grp Navigation command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);

    return (status == BT_STATUS_SUCCESS) ? JNI_TRUE : JNI_FALSE;
}

static void setPlayerApplicationSettingValuesNative(JNIEnv *env, jobject object, jbyteArray address,
                                                    jbyte num_attrib, jbyteArray attrib_ids,
                                                    jbyteArray attrib_val) {
    bt_status_t status;
    jbyte *addr;
    uint8_t *pAttrs = NULL;
    uint8_t *pAttrsVal = NULL;
    int i;
    jbyte *attr;
    jbyte *attr_val;

    if (!sBluetoothAvrcpInterface) return;

    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }

    pAttrs = new uint8_t[num_attrib];
    pAttrsVal = new uint8_t[num_attrib];
    if ((!pAttrs) ||(!pAttrsVal)) {
        ALOGE("setPlayerApplicationSettingValuesNative: not have enough memeory");
        return;
    }
    attr = env->GetByteArrayElements(attrib_ids, NULL);
    attr_val = env->GetByteArrayElements(attrib_val, NULL);
    if ((!attr)||(!attr_val)) {
        delete[] pAttrs;
        delete[] pAttrsVal;
        jniThrowIOException(env, EINVAL);
        return;
    }
    for (i = 0; i < num_attrib; ++i) {
        pAttrs[i] = (uint8_t)attr[i];
        pAttrsVal[i] = (uint8_t)attr_val[i];
    }
    if (i < num_attrib) {
        delete[] pAttrs;
        delete[] pAttrsVal;
        env->ReleaseByteArrayElements(attrib_ids, attr, 0);
        env->ReleaseByteArrayElements(attrib_val, attr_val, 0);
        return;
    }

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if ((status = sBluetoothAvrcpInterface->set_player_app_setting_cmd((bt_bdaddr_t *)addr,
                                    (uint8_t)num_attrib, pAttrs, pAttrsVal))!= BT_STATUS_SUCCESS) {
        ALOGE("Failed sending setPlAppSettValNative command, status: %d", status);
    }
    delete[] pAttrs;
    delete[] pAttrsVal;
    env->ReleaseByteArrayElements(attrib_ids, attr, 0);
    env->ReleaseByteArrayElements(attrib_val, attr_val, 0);
    env->ReleaseByteArrayElements(address, addr, 0);
}

static void sendAbsVolRspNative(JNIEnv *env, jobject object, jbyteArray address,
                                jint abs_vol, jint label) {
    bt_status_t status;
    jbyte *addr;

    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }

    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if ((status = sBluetoothAvrcpInterface->set_volume_rsp((bt_bdaddr_t *)addr,
                  (uint8_t)abs_vol, (uint8_t)label))!= BT_STATUS_SUCCESS) {
        ALOGE("Failed sending sendAbsVolRspNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}

static void sendRegisterAbsVolRspNative(JNIEnv *env, jobject object, jbyteArray address,
                                        jbyte rsp_type, jint abs_vol, jint label) {
    bt_status_t status;
    jbyte *addr;

    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    if ((status = sBluetoothAvrcpInterface->register_abs_vol_rsp((bt_bdaddr_t *)addr,
                  (btrc_notification_type_t)rsp_type,(uint8_t)abs_vol, (uint8_t)label))
                  != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending sendRegisterAbsVolRspNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}

static void getElementAttributesNative(JNIEnv *env, jobject object, jbyteArray address,
                                        jbyte num_attribs, jbyteArray attrib_ids) {
    bt_status_t status;
    jbyte *addr;
    uint32_t *pAttrs = NULL;
    jbyte *attr;
    int i;

    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if (num_attribs == 0)// we have to fetch all element attributes
    {
        sBluetoothAvrcpInterface->get_media_element_attributes((bt_bdaddr_t *)addr,
                    (uint8_t)num_attribs, NULL);
        env->ReleaseByteArrayElements(address, addr, 0);
        return;
    }
    pAttrs = new uint32_t[num_attribs];
    attr = env->GetByteArrayElements(attrib_ids, NULL);
    for (i = 0; i < num_attribs; ++i) {
        pAttrs[i] = (uint32_t)attr[i];
    }
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    status = sBluetoothAvrcpInterface->get_media_element_attributes((bt_bdaddr_t *)addr,
            (uint8_t)num_attribs, pAttrs);
    if (status != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending getElementAttributesNative command, status: %d", status);
    }
    delete[] pAttrs;
    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(attrib_ids, attr, 0);
}
static void getTotalNumberOfItemsNative(JNIEnv *env, jobject object, jbyteArray address, jbyte scope) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if ((status = sBluetoothAvrcpInterface->get_total_number_of_items((bt_bdaddr_t *)addr,
                                         (uint8_t)scope)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending getTotalNumberOfItems command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}
static void browseFolderNative(JNIEnv *env, jobject object, jbyteArray address, jbyte scope,
                        jint startIndex, jint endIndex, jbyte num_attribs, jbyteArray attrib_ids) {
    bt_status_t status;
    jbyte *addr;
    uint32_t *pAttrs = NULL;
    jbyte *attr;
    int i;

    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if (num_attribs == 0)// we have to fetch all element attributes
    {
        sBluetoothAvrcpInterface->browse_folder((bt_bdaddr_t *)addr, (uint8_t)scope,
                (uint32_t)startIndex, (uint32_t)endIndex,(uint8_t)num_attribs, NULL);
        env->ReleaseByteArrayElements(address, addr, 0);
        return;
    }
    pAttrs = new uint32_t[num_attribs];
    attr = env->GetByteArrayElements(attrib_ids, NULL);
    for (i = 0; i < num_attribs; ++i) {
        pAttrs[i] = (uint32_t)attr[i];
    }
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    status = sBluetoothAvrcpInterface->browse_folder((bt_bdaddr_t *)addr, (uint8_t)scope,
                            (uint32_t)startIndex, (uint32_t)endIndex,(uint8_t)num_attribs, pAttrs);
    if (status != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending browseFolderNative command, status: %d", status);
    }
    delete[] pAttrs;
    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseByteArrayElements(attrib_ids, attr, 0);
}
static void setBrowsedPlayerNative(JNIEnv *env, jobject object, jbyteArray address, jint playerId) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if ((status = sBluetoothAvrcpInterface->set_browsed_player((bt_bdaddr_t *)addr,
                                                     (uint16_t)playerId)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending setBrowsedPlayerNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}
static void setAddressedPlayerNative(JNIEnv *env, jobject object, jbyteArray address, jint playerId) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if ((status = sBluetoothAvrcpInterface->set_addressed_player((bt_bdaddr_t *)addr,
                                                       (uint16_t)playerId)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending setAddressedPlayerNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}
static void changePathNative(JNIEnv *env, jobject object, jbyteArray address, jint uidCounter,
                             jbyte direction, jlong uid) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if ((status = sBluetoothAvrcpInterface->change_path((bt_bdaddr_t *)addr, (uint16_t)uidCounter,
                                        (uint8_t)direction, (uint64_t)uid)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending changePathNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}
static void addToNowPlayingListNative(JNIEnv *env, jobject object, jbyteArray address, jbyte scope,
                             jlong uid, jint uidCounter) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if ((status = sBluetoothAvrcpInterface->add_to_now_playing_list((bt_bdaddr_t *)addr,
                (uint8_t)scope, (uint64_t)uid, (uint16_t)uidCounter)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending addToNowPlayingListNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}
static void playItemNative(JNIEnv *env, jobject object, jbyteArray address, jbyte scope,
                                      jint uidCounter, jlong uid) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    if ((status = sBluetoothAvrcpInterface->play_item((bt_bdaddr_t *)addr,
                    (uint8_t)scope, (uint16_t)uidCounter, (uint64_t)uid)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending playItemNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
}
static void searchNative(JNIEnv *env, jobject object, jbyteArray address, jint charset,
                           jint strLen, jstring pattern) {
    ALOGI("%s: sBluetoothAvrcpInterface: %p", __FUNCTION__, sBluetoothAvrcpInterface);
    bt_status_t status;
    jbyte *addr;
    const char* search_pattern = NULL;
    if (!sBluetoothAvrcpInterface) return;
    addr = env->GetByteArrayElements(address, NULL);
    if (!addr) {
        jniThrowIOException(env, EINVAL);
        return;
    }
    search_pattern = env->GetStringUTFChars(pattern, NULL);
    if ((status = sBluetoothAvrcpInterface->search((bt_bdaddr_t *)addr, (uint16_t)charset,
                     (uint16_t)strLen, (uint8_t*)search_pattern)) != BT_STATUS_SUCCESS) {
        ALOGE("Failed sending searchNative command, status: %d", status);
    }
    env->ReleaseByteArrayElements(address, addr, 0);
    env->ReleaseStringUTFChars(pattern, search_pattern);
}
static JNINativeMethod sMethods[] = {
    {"classInitNative", "()V", (void *) classInitNative},
    {"initNative", "()V", (void *) initNative},
    {"cleanupNative", "()V", (void *) cleanupNative},
    {"sendPassThroughCommandNative", "([BII)Z",(void *) sendPassThroughCommandNative},
    {"sendGroupNavigationCommandNative", "([BII)Z",(void *) sendGroupNavigationCommandNative},
    {"setPlayerApplicationSettingValuesNative", "([BB[B[B)V",
                               (void *) setPlayerApplicationSettingValuesNative},
    {"sendAbsVolRspNative", "([BII)V",(void *) sendAbsVolRspNative},
    {"sendRegisterAbsVolRspNative", "([BBII)V",(void *) sendRegisterAbsVolRspNative},
    {"getElementAttributesNative", "([BB[B)V",(void *) getElementAttributesNative},
    {"getTotalNumberOfItemsNative", "([BB)V",(void *) getTotalNumberOfItemsNative},
    {"browseFolderNative", "([BBIIB[B)V",(void *) browseFolderNative},
    {"setBrowsedPlayerNative", "([BI)V",(void *) setBrowsedPlayerNative},
    {"setAddressedPlayerNative", "([BI)V",(void *) setAddressedPlayerNative},
    {"changePathNative", "([BIBJ)V",(void *) changePathNative},
    {"addToNowPlayingListNative", "([BBJI)V",(void *) addToNowPlayingListNative},
    {"playItemNative", "([BBIJ)V",(void *) playItemNative},
    {"searchNative", "([BIILjava/lang/String;)V",(void *) searchNative},
};

int register_com_android_bluetooth_avrcp_controller(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/bluetooth/avrcp/AvrcpControllerService",
                                    sMethods, NELEM(sMethods));
}

}
