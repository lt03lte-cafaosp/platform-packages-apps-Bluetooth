/*
 * Copyright (c) 2010, Code Aurora Forum. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in the
 *          documentation and/or other materials provided with the distribution.
 *        * Neither the name of Code Aurora nor
 *          the names of its contributors may be used to endorse or promote
 *          products derived from this software without specific prior written
 *          permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NON-INFRINGEMENT ARE DISCLAIMED.    IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.android.bluetooth.map;
import com.android.bluetooth.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.telephony.SmsManager;
import android.text.format.Time;
import android.util.Log;
import android.util.TimeFormatException;

import com.android.bluetooth.map.MapUtils.BmessageConsts;
import com.android.bluetooth.map.MapUtils.MapUtils;
import com.android.bluetooth.map.MapUtils.MsgListingConsts;

import javax.obex.ResponseCodes;
/**
 * This class provides the application interface for MAS Server
 * It interacts with the SMS repository using Sms Content Provider
 * to service the MAS requests. It also initializes BluetoothMns
 * thread which is used for MNS connection.
 */

public class BluetoothMasAppIf {

    public Context context;
    public final String TAG = "BluetoothMasAppIf";

    public static final int BIT_SUBJECT = 0x1;
    public static final int BIT_DATETIME = 0x2;
    public static final int BIT_SENDER_NAME = 0x4;
    public static final int BIT_SENDER_ADDRESSING = 0x8;

    public static final int BIT_RECIPIENT_NAME = 0x10;
    public static final int BIT_RECIPIENT_ADDRESSING = 0x20;
    public static final int BIT_TYPE = 0x40;
    public static final int BIT_SIZE = 0x80;

    public static final int BIT_RECEPTION_STATUS = 0x100;
    public static final int BIT_TEXT = 0x200;
    public static final int BIT_ATTACHMENT_SIZE = 0x400;
    public static final int BIT_PRIORITY = 0x800;

    public static final int BIT_READ = 0x1000;
    public static final int BIT_SENT = 0x2000;
    public static final int BIT_PROTECTED = 0x4000;
    public static final int BIT_REPLYTO_ADDRESSING = 0x8000;

    private String RootPath = "root";

    private String CurrentPath = null;

    private String Telecom = "telecom";
    private String Msg = "msg";

    private String Inbox = "inbox";
    private String Outbox = "outbox";
    private String Sent = "sent";
    private String Deleted = "deleted";
    private String Draft = "draft";
    private String Undelivered = "undelivered";
    private String Failed = "failed";
    private String Queued = "queued";

    private boolean mnsServiceEnabled = false;

    // root -> telecom -> msg -> (FolderList) includes inbox, outbox, sent, etc
    private String FolderList[] = { Inbox, Outbox, Sent, Deleted, Draft };

    private MapUtils mu;

    private BluetoothMns mnsClient;

    public BluetoothMasAppIf(Context context) {
        this.context = context;
        mu = new MapUtils();
        mnsClient = new BluetoothMns(context);

        Log.d(TAG, "Constructor called");
    }

    private String getWhereIsQueryForType(String folder) {

        String query = null;

        if (folder.equalsIgnoreCase(Inbox)) {
            query = "type = 1";
        }
        if (folder.equalsIgnoreCase(Outbox)) {
            query = "(type = 4 OR type = 5 OR type = 6)";
        }
        if (folder.equalsIgnoreCase(Sent)) {
            query = "type = 2";
        }
        if (folder.equalsIgnoreCase(Draft)) {
            query = "type = 3";
        }
        if (folder.equalsIgnoreCase(Deleted)) {
            query = "type = -1";
        }
        return query;

    }


    /**
     * Set the path to a given folder
     * @return true if the path exists, and could be accessed.
     */
    public boolean setPath(boolean up, String name) {
        Log.d(TAG, "setPath called");
        /* Up and empty string – cd .. Up and name - cd ../name Down and name -
         * cd name Down and empty string – cd to root
         */

        if ((up == false) && (name == null)) {
            CurrentPath = null;
            return true;
        }

        if (up == true) {
            if (CurrentPath == null) {
                // Can't go above root
                return false;
            } else {
                int LastIndex = CurrentPath.lastIndexOf('/');
                if (LastIndex < 0) {
                    // Reaches root
                    CurrentPath = null;
                } else {
                    CurrentPath = CurrentPath.substring(0, LastIndex);
                }
            }
            if (name == null) {
                // Only going up by one
                return true;
            }
        }

        if (CurrentPath == null) {
            if (name.equals(Telecom)) {
                CurrentPath = Telecom;
                return true;
            } else {
                return false;
            }
        }

        String splitStrings[] = CurrentPath.split("/");

        boolean Result = false;
        switch (splitStrings.length) {
        case 1:
            if (name.equals(Msg)) {
                CurrentPath += ("/" + name);
                Result = true;
            }
            break;
        case 2:
            for (String FolderName : FolderList) {
                if (FolderName.equals(name)) {
                    CurrentPath += ("/" + name);
                    Result = true;
                    break;
                }
            }
            break;
        // TODO SUBFOLDERS: Add check for sub-folders (add more cases)

        default:
            Result = false;
            break;
        }
        return Result;
    }

    /**
     * Get the number of messages in the folder
     * @return number of messages; -1 if error
     */
    public int folderListingSize() {
        Log.d(TAG, "folderListingSize called, current path " + CurrentPath);

        if (CurrentPath == null) {
            // at root, only telecom folder should be present
            return 1;
        }

        if (CurrentPath.equals(Telecom)) {
            // at root -> telecom, only msg folder should be present
            return 1;
        }

        if (CurrentPath.equals(Telecom + "/" + Msg)) {
            // at root -> telecom -> msg, FolderList should be present
            return FolderList.length;
        }
        // TODO SUBFOLDERS: Add check for sub-folders

        return 0;
    }

    /**
     * Get the XML listing of the folders at CurrenthPath
     * @return XML listing of the folders
     */
    public String folderListing(BluetoothMasAppParams appParam) {
        Log.d(TAG, "folderListing called, current path " + CurrentPath);

        List<String> list = new ArrayList<String>();

        if (CurrentPath == null) {
            // at root, only telecom folder should be present
            if (appParam.ListStartOffset == 0) {
                list.add(Telecom);
            }
            return mu.folderListingXML(list);
        }

        if (CurrentPath.equals(Telecom)) {
            // at root -> telecom, only msg folder should be present
            if (appParam.ListStartOffset == 0) {
                list.add(Msg);
            }
            return mu.folderListingXML(list);
        }

        if (CurrentPath.equals(Telecom + "/" + Msg)) {
            int offset = 0;
            int added = 0;
            // at root -> telecom -> msg, FolderList should be present
            for (String Folder : FolderList) {
                offset++;
                if ((offset > appParam.ListStartOffset)
                        && (added < appParam.MaxListCount)) {
                    list.add(Folder);
                    added++;
                }
            }
            return mu.folderListingXML(list);
        }

        // TODO SUBFOLDERS: Add check for subfolders

        return null;
    }

    /**
     * Append child folder to the CurrentPath
     */
    private String getFullPath(String child) {

        String tempPath = null;
        if (child != null) {
            if (CurrentPath == null) {
                if (child.equals("telecom")) {
                    // Telecom is fine
                    tempPath = new String("telecom");
                }
            } else if (CurrentPath.equals("telecom")) {
                if (child.equals("msg")) {
                    tempPath = CurrentPath + "/" + child;
                }
            } else if (CurrentPath.equals("telecom/msg")) {
                for (String Folder : FolderList) {
                    if (child.equalsIgnoreCase(Folder)) {
                        tempPath = CurrentPath + "/" + Folder;
                    }
                }
            }
        }
        return tempPath;
    }

    public class BluetoothMasMessageListingRsp {
        public File file = null;
        public int msgListingSize = 0;
        public byte newMessage = 0;
    }

    private class VcardContent {
        public String name = "";
        public String tel = "";
        public String email = "";
    }

    static final int PHONELOOKUP_ID_COLUMN_INDEX = 0;
    static final int PHONELOOKUP_LOOKUP_KEY_COLUMN_INDEX = 1;
    static final int PHONELOOKUP_DISPLAY_NAME_COLUMN_INDEX = 2;

    static final int EMAIL_DATA_COLUMN_INDEX = 0;

    private List<VcardContent> list;

    private VcardContent getVcardContent(String phoneAddress) {

        VcardContent vCard = new VcardContent();
        vCard.tel = phoneAddress;

        Uri uriContacts = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneAddress));
        // Cursor cursorContacts = crContacts.query(uriContacts, new
        // String[]{PhoneLookup.DISPLAY_NAME, PhoneLookup.NUMBER, PhoneLookup.},
        // null, null, null);
        Cursor cursorContacts = context.getContentResolver().query(
                uriContacts,
                new String[] { PhoneLookup._ID, PhoneLookup.LOOKUP_KEY,
                        PhoneLookup.DISPLAY_NAME }, null, null, null);

        cursorContacts.moveToFirst();

        // Log.d( TAG, " CursorContacts.count " + cursorContacts.getCount());

        // long contactId =
        // cursorContacts.getLong(cursorContacts.getColumnIndex(PhoneLookup._ID));
        // String lookupKey =
        // cursorContacts.getString(cursorContacts.getColumnIndex(PhoneLookup.LOOKUP_KEY));

        if (cursorContacts.getCount() > 0) {
            long contactId = cursorContacts
                    .getLong(PHONELOOKUP_ID_COLUMN_INDEX);
            String lookupKey = cursorContacts
                    .getString(PHONELOOKUP_LOOKUP_KEY_COLUMN_INDEX);

            Uri lookUpUri = Contacts.getLookupUri(contactId, lookupKey);
            String Id = lookUpUri.getLastPathSegment();

            Cursor crEm = context.getContentResolver().query(Email.CONTENT_URI,
                    new String[] { Email.DATA }, Email.CONTACT_ID + "=?",
                    new String[] { Id }, null);
            crEm.moveToFirst();

            vCard.name = cursorContacts
                    .getString(PHONELOOKUP_DISPLAY_NAME_COLUMN_INDEX);

            vCard.email = "";
            if (crEm.moveToFirst()) {
                do {
                    // Log.d(TAG, " Email : " +
                    // crEm.getString(EMAIL_DATA_COLUMN_INDEX));
                    vCard.email += crEm.getString(EMAIL_DATA_COLUMN_INDEX)
                            + ";";
                } while (crEm.moveToNext());
            }
        }
        return vCard;
    }

    /**
     * Check if the entry is not to be filtered out (allowed)
     */
    private boolean allowEntry(String phoneAddress, String filterString) {

        boolean found = false;
        VcardContent foundEntry = null;
        for (VcardContent elem : list) {
            if (elem.tel.contains(phoneAddress)) {
                // Log.d(TAG, " ++++contains the string++++++++++++++++" +
                // elem.name + elem.tel
                // + elem.email + phoneAddress);
                found = true;
                foundEntry = elem;
            }
        }
        if (found == false) {
            VcardContent vCard = getVcardContent(phoneAddress);
            if (vCard != null) {
                list.add(vCard);
                found = true;
                foundEntry = vCard;
                Log.d(TAG, " NEW VCARD ADDED " + vCard.tel + vCard.name
                        + vCard.email);
            } else {
                Log.d(TAG, "VCARD NOT FOUND ERROR");
            }
        }

        if (found == true) {
            if ((foundEntry.tel.contains(filterString))
                    || (foundEntry.name.contains(filterString))
                    || (foundEntry.email.contains(filterString))) {
                // Log.d(TAG, foundEntry.tel + foundEntry.name +
                // foundEntry.email);
                return true;
            }
        }
        return false;
    }

    /**
     * Get the contact name for the given phone number
     */
    private String getContactName(String phoneNumber) {
        // TODO Optimize this (get from list)

        // VcardContent vCard = getVcardContent(phoneNumber);
        // return vCard.name;
        boolean found = false;
        VcardContent foundEntry = null;
        for (VcardContent elem : list) {
            if (elem.tel.contains(phoneNumber)) {
                // Log.d(TAG, " ++++contains the string++++++++++++++++" +
                // elem.name + elem.tel
                // + elem.email + phoneAddress);
                found = true;
                foundEntry = elem;
                break;
            }
        }
        if (found == false) {
            foundEntry = getVcardContent(phoneNumber);
            if (foundEntry != null) {
                list.add(foundEntry);
                found = true;
            }
        }
        if (found == true) {
            return foundEntry.name;
        }

        return null;
    }

    /**
     * Get the owners name
     */
    public String getOwnerName() {
        // TODO
        return "QCOM";
        // return null;
    }

    /**
     * Get the owners phone number
     */
    public String getOwnerNumber() {
        // TODO
        return "+11234567890";
        // return null;
    }

    private boolean isOutgoingMessage(String folder) {
        if (folder.equalsIgnoreCase("inbox")) {
            return false;
        }
        return true;
    }

    /**
     * Get the list of messages in the given folder
     * @return Listing of messages in MAP-msg-listing format
     */
    public BluetoothMasMessageListingRsp msgListing(String name,
            BluetoothMasAppParams appParams) {
        // TODO Auto-generated method stub

        BluetoothMasMessageListingRsp rsp = new BluetoothMasMessageListingRsp();

        boolean fileGenerated = false;

        // TODO Do this based on the MasInstance
        final String FILENAME = "msglist";

        int writeCount = 0;
        int processCount = 0;
        int messageListingSize = 0;

        List<MsgListingConsts> msgList = new ArrayList<MsgListingConsts>();

        if (appParams == null)
            return null;

        String tempPath = null;
        if (name != null) {
            tempPath = getFullPath(name);
            if (tempPath == null) {
                // Child folder not present
                return rsp;
            }
        } else {
            tempPath = CurrentPath;
        }

        FileOutputStream bos = null;

        try {
            bos = context.openFileOutput(FILENAME, Context.MODE_PRIVATE);
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // TODO: Take care of subfolders
        // TODO: Check only for SMS_GSM
        if ((tempPath != null) && (tempPath.split("/").length == 3)
                && ((appParams.FilterMessageType & 0x1) == 0x0)) {

            String splitStrings[] = tempPath.split("/");

            // TODO: Take care of subfolders
            // if ( splitStrings.length != 3 )
            // return null;

            Log.d(TAG, "splitString[2] = " + splitStrings[2]);

            // TODO Assuming only for SMS.
            String url = "content://sms/";

            Uri uri = Uri.parse(url);

            ContentResolver cr = context.getContentResolver();

            // getWhereIsQueryForType

            String whereClause = getWhereIsQueryForType(splitStrings[2]);

            /* Filter readstatus: 0 no filtering, 0x01 get unread, 0x10 get read */
            if (appParams.FilterReadStatus != 0) {
                if ((appParams.FilterReadStatus & 0x1) != 0) {
                    if (whereClause != "") {
                        whereClause += " AND ";
                    }
                    whereClause += " read=0 ";
                }
                if ((appParams.FilterReadStatus & 0x10) != 0) {
                    if (whereClause != "") {
                        whereClause += " AND ";
                    }
                    whereClause += " read=1 ";
                }
            }

            // TODO Filter priority?

            /* Filter Period Begin */
            if ((appParams.FilterPeriodBegin != null)
                    && (appParams.FilterPeriodBegin != "")) {
                Time time = new Time();
                try {
                    time.parse(appParams.FilterPeriodBegin);
                    if (whereClause != "") {
                        whereClause += " AND ";
                    }
                    whereClause += "date > " + time.toMillis(false);
                } catch (TimeFormatException e) {
                    Log.d(TAG, "Bad formatted FilterPeriodBegin, Ignore"
                            + appParams.FilterPeriodBegin);
                }
            }

            /* Filter Period End */
            if ((appParams.FilterPeriodEnd != null)
                    && (appParams.FilterPeriodEnd != "")) {
                Time time = new Time();
                try {
                    time.parse(appParams.FilterPeriodEnd);
                    if (whereClause != "") {
                        whereClause += " AND ";
                    }
                    whereClause += "date < " + time.toMillis(false);
                } catch (TimeFormatException e) {
                    Log.d(TAG, "Bad formatted FilterPeriodEnd, Ignore"
                            + appParams.FilterPeriodEnd);
                }
            }

            Cursor cursor = cr.query(uri, null, whereClause, null, "date desc");

            int idInd = cursor.getColumnIndex("_id");
            int addressInd = cursor.getColumnIndex("address");
            int personInd = cursor.getColumnIndex("person");
            int dateInd = cursor.getColumnIndex("date");
            int readInd = cursor.getColumnIndex("read");
            int statusInd = cursor.getColumnIndex("status");
            int subjectInd = cursor.getColumnIndex("subject");
            int bodyInd = cursor.getColumnIndex("body");

            Log.d(TAG, "move to First" + cursor.moveToFirst());
            Log.d(TAG, "move to Liststartoffset"
                    + cursor.moveToPosition(appParams.ListStartOffset));

            this.list = new ArrayList<VcardContent>();

            if (cursor.moveToFirst()) {

                do {
                    /*
                     * Apply remaining filters
                     */

                    /*
                     * For incoming message, originator is the remote contact
                     * For outgoing message, originator is the owner.
                     */
                    String filterString = null;

                    // TODO Filter Recipient
                    if ((appParams.FilterRecipient != null)
                            && (appParams.FilterRecipient.length() != 0)) {
                        // For outgoing message
                        if (isOutgoingMessage(splitStrings[2]) == true) {
                            filterString = appParams.FilterRecipient;
                            Log.d(TAG, "appParams.FilterRece"
                                    + appParams.FilterRecipient);
                        }
                    }

                    // TODO Filter Originator
                    if ((appParams.FilterOriginator != null)
                            && (appParams.FilterOriginator.length() != 0)) {
                        // For incoming message
                        if (isOutgoingMessage(splitStrings[2]) == false) {
                            filterString = appParams.FilterOriginator;
                            Log.d(TAG, "appParams.FilterOrig"
                                    + appParams.FilterOriginator);
                        }
                    }

                    if (filterString != null) {
                        Log.d(TAG, "filterString = " + filterString);
                        if (allowEntry(cursor.getString(addressInd),
                                filterString) == true) {
                            Log.d(TAG, "+++ ALLOWED +++++++++ "
                                    + cursor.getString(addressInd) + " - "
                                    + cursor.getPosition());
                        } else {
                            Log.d(TAG, "+++ DENIED +++++++++ "
                                    + cursor.getString(addressInd) + " - "
                                    + cursor.getPosition());
                            continue;
                        }
                    }
                    Log.d(TAG, " msgListSize " + messageListingSize
                            + "write count " + writeCount);

                    messageListingSize++;

                    /*
                     * Don't want the listing; just send the listing size after
                     * applying all the filters.
                     */
                    if (appParams.MaxListCount == 0) {
                        continue;
                    }

                    processCount++;
                    /*
                     * Skip the first ListStartOffset record(s). Don't write
                     * more than MaxListCount record(s).
                     */
                    if ((appParams.ListStartOffset >= processCount)
                            || (writeCount >= appParams.MaxListCount)) {
                        continue;
                    }

                    MsgListingConsts ml = new MsgListingConsts();
                    ml.setMsg_handle(Integer.valueOf(cursor.getString(idInd)));

                    if ((appParams.ParameterMask & BIT_SUBJECT) != 0) {
                        String subject = cursor.getString(subjectInd);
                        if ((subject != null)
                                && (subject.length() > appParams.SubjectLength)) {
                            subject = subject.substring(0,
                                    appParams.SubjectLength);
                        }
                        ml.setSubject(subject);
                    }

                    if ((appParams.ParameterMask & BIT_DATETIME) != 0) {
                        Time time = new Time();
                        time.set(Long.valueOf(cursor.getString(dateInd)));
                        // TODO Clarify if OFFSET is needed. Remove hardcoded
                        // value and 0700
                        ml.setDatetime(time.toString().substring(0, 15)
                                + "-0700");
                    }

                    if ((appParams.ParameterMask & BIT_SENDER_NAME) != 0) {
                        // TODO Query the Contacts database
                        String senderName = null;
                        if (isOutgoingMessage(splitStrings[2]) == true) {
                            senderName = getOwnerName();
                        } else {
                            senderName = getContactName(cursor
                                    .getString(addressInd));
                        }
                        ml.setSender_name(senderName);
                    }

                    if ((appParams.ParameterMask & BIT_SENDER_ADDRESSING) != 0) {
                        // TODO In case of a SMS this is
                        // the sender's phone number in canonical form (chapter
                        // 2.4.1 of [5]).
                        String senderAddressing = null;
                        if (isOutgoingMessage(splitStrings[2]) == true) {
                            senderAddressing = getOwnerNumber();
                        } else {
                            senderAddressing = cursor.getString(addressInd);
                        }
                        ml.setSender_addressing(senderAddressing);
                    }

                    if ((appParams.ParameterMask & BIT_RECIPIENT_NAME) != 0) {
                        // TODO "recipient_name" is the name of the recipient of
                        // the message, when it is known
                        // by the MSE device.
                        String recipientName = null;
                        if (isOutgoingMessage(splitStrings[2]) == false) {
                            recipientName = getOwnerName();
                        } else {
                            recipientName = getContactName(cursor
                                    .getString(addressInd));
                        }
                        ml.setRecepient_name(recipientName);
                    }

                    if ((appParams.ParameterMask & BIT_RECIPIENT_ADDRESSING) != 0) {
                        // TODO In case of a SMS this is the recipient's phone
                        // number in canonical form (chapter 2.4.1 of [5])
                        String recipientAddressing = null;
                        if (isOutgoingMessage(splitStrings[2]) == false) {
                            recipientAddressing = getOwnerNumber();
                        } else {
                            recipientAddressing = cursor.getString(addressInd);
                        }
                        ml.setRecepient_addressing(recipientAddressing);
                        // TODO Undo this
                        // ml.setRecepient_addressing("777-888-9999");
                    }

                    if ((appParams.ParameterMask & BIT_TYPE) != 0) {
                        // TODO GSM or CDMA SMS?
                        ml.setType("SMS_GSM");
                    }

                    if ((appParams.ParameterMask & BIT_SIZE) != 0) {
                        ml.setSize(cursor.getString(bodyInd).length());
                    }

                    if ((appParams.ParameterMask & BIT_RECEPTION_STATUS) != 0) {
                        ml.setReception_status("complete");
                    }

                    if ((appParams.ParameterMask & BIT_TEXT) != 0) {
                        // TODO Set text to "yes"
                        ml.setContains_text("yes");
                    }

                    if ((appParams.ParameterMask & BIT_ATTACHMENT_SIZE) != 0) {
                        ml.setAttachment_size(0);
                    }

                    if ((appParams.ParameterMask & BIT_PRIORITY) != 0) {
                        // TODO Get correct priority
                        ml.setPriority("no");
                    }

                    if ((appParams.ParameterMask & BIT_READ) != 0) {
                        if (cursor.getString(readInd).equalsIgnoreCase("1")) {
                            ml.setRead("yes");
                        } else {
                            ml.setRead("no");
                        }
                    }

                    if ((appParams.ParameterMask & BIT_SENT) != 0) {
                        // TODO Get sent status?
                        if (splitStrings[2].equalsIgnoreCase("sent")) {
                            ml.setSent("yes");
                        } else {
                            ml.setSent("no");
                        }
                    }

                    if ((appParams.ParameterMask & BIT_PROTECTED) != 0) {
                        ml.setMsg_protected("no");
                    }

                    // TODO replyto_addressing is used only for email

                    // New Message?
                    if ((rsp.newMessage == 0) && (cursor.getInt(readInd) != 0)) {
                        rsp.newMessage = 1;
                    }

                    msgList.add(ml);
                    writeCount++;
                    /*
                     * if ( (writeCount % 50) == 0 ){ String str =
                     * mu.messageListingXML(msgList); try {
                     * fos.write(str.getBytes()); fos.flush(); msgList.clear();
                     * Log.d(TAG, "Cleared the list"); } catch (IOException e) {
                     * // TODO Auto-generated catch block e.printStackTrace(); }
                     * }
                     */
                    // Log.d(TAG, "ENTRY NO: " + cursor.getPosition());

                    // } while ( (cursor.moveToNext()) && (writeCount <
                    // appParams.MaxListCount));
                } while (cursor.moveToNext());

                cursor.close();
            }
        }

        String str = mu.messageListingXML(msgList);
        // TODO Undo this
        // String str =
        // "<?xml version=\"1.0\"?><MAP-msg-listing version=\"1.0\"><msg handle=\"15\" subject=\"Second sms from droidx\" datetime=\"20101101T111200-0700\" sender_name=\"null\" sender_addressing=\"858-354-9879\" replyto_addressing=\"null\" recipient_name=\"null\" recipient_addressing=\"858-232-4205\" type=\"SMS_CDMA\" size=\"22\" text=\"yes\" reception_status=\"complete\" attachment_size=\"0\" priority=\"null\" read=\"yes\" sent=\"no\" protected=\"no\"/></MAP-msg-listing>";
        int pos = str.indexOf(" msg handle=\"");
        while (pos > 0) {
            Log.d(TAG, " Msg listing str modified");
            String str2 = str.substring(0, pos);
            str2 += str.substring(pos + 1);
            str = str2;
            pos = str.indexOf(" msg handle=\"");
        }

        // String str = "this is a test for the data file";
        try {
            bos.write(str.getBytes());
            bos.flush();
            bos.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        msgList.clear();

        Log.d(TAG, "");

        // Log.d(TAG, " FIRST (130) 33 - 166 char " + str.substring(33, 166));

        Log.d(TAG, " MESSAGE LISTING FULL ( total length)" + str.length());
        Log.d(TAG, str);

        byte[] readBytes = new byte[10];
        try {

            FileInputStream fis = new FileInputStream(
                    context.getFilesDir() + "/" + FILENAME);
            fis.close();
            fileGenerated = true;

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        // Log.d(TAG, " READ BYTES LENGTH = " + readBytes.length);
        // String readStr = new String(readBytes);
        // Log.d(TAG, " READ BYTES CONTENT (130) 33 - 166" +
        // readStr.substring(33, 166));
        // Log.d(TAG, " READ BYTES ALL STUFF " + readStr.toString());

        if (fileGenerated == true) {
            File file = new File(context.getFilesDir() + "/" + FILENAME);
            rsp.file = file;
        }
        rsp.msgListingSize = messageListingSize;

        return rsp;
    }

    public class BluetoothMasMessageRsp {
        public byte fractionDeliver = 0;
        public File file = null;
    }

    /**
     * Get the folder name (MAP representation) based on the
     * folder type value in SMS database
     */
    private String getMAPFolder(int type) {
        String folder = null;
        switch (type) {
        case 1:
            folder = Inbox;
            break;
        case 2:
            folder = Sent;
            break;
        case 3:
            folder = Draft;
            break;
        case 4:
        case 5:
        case 6:
            folder = Outbox;
            break;
        default:
            break;
        }
        return folder;

    }

    /**
     * Get the folder name (MAP representation) based on the
     * message Handle
     */
    private String getContainingFolder(String msgHandle) {
        Cursor cr;

        cr = context.getContentResolver().query(
                Uri.parse("content://sms/" + msgHandle),
                new String[] { "_id", "type" }, null, null, null);
        if (cr.getCount() > 0) {
            cr.moveToFirst();
            return getMAPFolder(cr.getInt(cr.getColumnIndex("type")));
        }
        return null;
    }

    /**
     * Get the message for the given message handle
     * @return BMSG object
     */
    public BluetoothMasMessageRsp msg(String msgHandle,
            BluetoothMasAppParams bluetoothMasAppParams) {

        if (msgHandle == null) {
            return null;
        }
        BluetoothMasMessageRsp rsp = new BluetoothMasMessageRsp();

        Cursor cr = null;
        Uri uri = Uri.parse("content://sms/");
        String whereClause = " _id = " + msgHandle;
        cr = context.getContentResolver().query(uri, null, whereClause, null,
                null);

        if (cr.getCount() > 0) {
            cr.moveToFirst();
            String containingFolder = getContainingFolder(msgHandle);
            BmessageConsts bmsg = new BmessageConsts();

            // Create a bMessage

            // TODO Get Current type
            bmsg.setType("SMS_GSM");

            bmsg.setBmsg_version("1.0");
            if (cr.getString(cr.getColumnIndex("read")).equalsIgnoreCase("1")) {
                bmsg.setStatus("READ");
            } else {
                bmsg.setStatus("UNREAD");
            }

            bmsg.setFolder("TELECOM/MSG/" + containingFolder);

            bmsg.setVcard_version("2.1");
            VcardContent vcard = getVcardContent(cr.getString(cr
                    .getColumnIndex("address")));

            String type = cr.getString(cr.getColumnIndex("type"));
            // Inbox is type 1.
            if (type.equalsIgnoreCase("1")) {
                // The address in database is of originator
                bmsg.setOriginatorVcard_name(vcard.name);
                bmsg.setOriginatorVcard_phone_number(vcard.tel);
                bmsg.setRecipientVcard_name(getOwnerName());
                bmsg.setRecipientVcard_phone_number(getOwnerNumber());
            } else {
                bmsg.setRecipientVcard_name(vcard.name);
                bmsg.setRecipientVcard_phone_number(vcard.tel);
                bmsg.setOriginatorVcard_name(getOwnerName());
                bmsg.setOriginatorVcard_phone_number(getOwnerNumber());
            }

            // TODO Set either Encoding or Native
            // bmsg.setBody_encoding("G-7BIT");

            // TODO how to get body for MMS? This is for SMS only
            String smsBody = cr.getString(cr.getColumnIndex("body"));
            bmsg.setBody_length(22 + smsBody.length());
            bmsg.setBody_msg(smsBody);

            // Send a bMessage
            Log.d(TAG, "bMessageSMS test\n");
            Log.d(TAG, "=======================\n\n");
            String str = mu.toBmessageSMS(bmsg);
            Log.d(TAG, str);
            Log.d(TAG, "\n\n");

            if (str != null && (str.length() > 0)) {
                final String FILENAME = "message";
                // BufferedOutputStream bos = null;
                FileOutputStream bos = null;
                File file = new File( context.getFilesDir() + "/" + FILENAME);
                file.delete();

                try {
                    bos = context
                            .openFileOutput(FILENAME, Context.MODE_PRIVATE);
                    // bos = new BufferedOutputStream(new
                    // FileOutputStream(FILENAME));

                    bos.write(str.getBytes());
                    bos.flush();
                    bos.close();
                } catch (FileNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                File fileR = new File( context.getFilesDir() + "/" + FILENAME);
                if (fileR.exists() == true) {
                    rsp.file = fileR;
                    rsp.fractionDeliver = 1;
                }

            }
        }

        return rsp;
    }

    public class BluetoothMasPushMsgRsp {
        public int response;
        public String msgHandle;
    }

    /**
     * Retrieve the conversation thread id
     */
    private int getThreadId(String address) {

        Cursor cr = context.getContentResolver().query(
                Uri.parse("content://sms/"), null,
                "address = '" + address + "'", null, null);
        if (cr.moveToFirst()) {
            int threadId = Integer.valueOf(cr.getString(cr
                    .getColumnIndex("thread_id")));
            Log.d(TAG, " Found the entry, thread id = " + threadId);

            return (threadId);
        }
        return 0;
    }

    /**
     * Adds a SMS to the Sms ContentProvider
     */

    public String addToSmsFolder(String folder, String address, String text) {

        int threadId = getThreadId(address);
        Log.d(TAG, "-------------");
        Log.d(TAG, "address " + address + " TEXT " + text + " Thread ID "
                + threadId);

        ContentValues values = new ContentValues();
        values.put("thread_id", threadId);
        values.put("body", text);
        values.put("address", address);
        values.put("read", 0);
        values.put("seen", 0);
        /*
         * status none -1 complete 0 pending 64 failed 128
         */
        values.put("status", -1);
        /*
         * outbox 4 queued 6
         */
        // values.put("type", 4);
        values.put("locked", 0);
        values.put("error_code", 0);
        Uri uri = context.getContentResolver().insert(
                Uri.parse("content://sms/" + folder), values);
        Log.d(TAG, " NEW URI " + uri.toString());

        String str = uri.toString();
        String[] splitStr = str.split("/");
        Log.d(TAG, " NEW HANDLE " + splitStr[3]);
        return splitStr[3];
    }

    /**
     * Get the type (as in Sms ContentProvider) for the given
     * table name
     */
    private int getSMSFolderType(String folder) {
        int type = 0;
        if (folder.equalsIgnoreCase(Inbox)) {
            type = 1;
        } else if (folder.equalsIgnoreCase(Sent)) {
            type = 2;
        } else if (folder.equalsIgnoreCase(Draft)) {
            type = 3;
        } else if (folder.equalsIgnoreCase(Outbox)) {
            type = 4;
        } else if (folder.equalsIgnoreCase(Failed)) {
            type = 5;
        } else if (folder.equalsIgnoreCase(Queued)) {
            type = 6;
        }
        return type;
    }

    /**
     * Modify the type (as in Sms ContentProvider)
     * For eg: move from outbox to send type
     */
    private void moveToFolder(String handle, String folder) {
        ContentValues values = new ContentValues();
        values.put("type", getSMSFolderType(folder));
        Uri uri = Uri.parse("content://sms/" + handle);
        context.getContentResolver().update(uri, values, null, null);
    }

    private String PhoneAddress;
    private String SmsText;
    private String SmsHandle;


    /**
     * Push a outgoing message from MAS Client to the network
     * @return Response to push command
     */
    public BluetoothMasPushMsgRsp pushMsg(String name, File file,
            BluetoothMasAppParams bluetoothMasAppParams) {
        // TODO Auto-generated method stub

        BluetoothMasPushMsgRsp rsp = new BluetoothMasPushMsgRsp();
        rsp.response = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        rsp.msgHandle = null;

        final String SENT = "Sent";
        final String DELIVERED = "Delivered";

        byte[] readBytes = null;
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
            readBytes = new byte[(int) file.length()];
            fis.read(readBytes);

        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return rsp;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return rsp;
        }

        String readStr = new String(readBytes);
        MapUtils mu = new MapUtils();
        BmessageConsts bMsg = mu.fromBmessageSMS(readStr);
        PhoneAddress = bMsg.getRecipientVcard_phone_number();
        SmsText = bMsg.getBody_msg();

        PendingIntent sentPI = PendingIntent.getBroadcast(this.context, 0,
                new Intent(SENT), 0);

        PendingIntent deliveredPI = PendingIntent.getBroadcast(this.context, 0,
                new Intent(DELIVERED), 0);

        // ---when the SMS has been sent---
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                Log.d(TAG, "Sms SENT STATUS ");

                switch (getResultCode()) {
                case Activity.RESULT_OK:
                    Log.d(TAG, "Sms Sent");
                    // addToSmsFolder("sent", PhoneAddress, SmsText);
                    moveToFolder(SmsHandle, Sent);
                    break;
                case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                    Log.d(TAG, "Generic Failure");
                    // addToSmsFolder("outbox", PhoneAddress, SmsText);
                    moveToFolder(SmsHandle, Failed);
                    break;
                case SmsManager.RESULT_ERROR_NO_SERVICE:
                    Log.d(TAG, "NO SERVICE ERROR");
                    // addToSmsFolder("queued", PhoneAddress, SmsText);
                    moveToFolder(SmsHandle, Queued);
                    break;
                case SmsManager.RESULT_ERROR_NULL_PDU:
                    Log.d(TAG, "Null PDU");
                    // addToSmsFolder("outbox", PhoneAddress, SmsText);
                    moveToFolder(SmsHandle, Failed);
                    break;
                case SmsManager.RESULT_ERROR_RADIO_OFF:
                    Log.d(TAG, "RADIO OFF");
                    // addToSmsFolder("queued", PhoneAddress, SmsText);
                    moveToFolder(SmsHandle, Queued);
                    break;
                }
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(SENT));

        // ---when the SMS has been delivered---
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                Log.d(TAG, "Sms SENT DELIVERED ");

                switch (getResultCode()) {
                case Activity.RESULT_OK:
                    Log.d(TAG, "Sms Delivered");
                    break;
                case Activity.RESULT_CANCELED:
                    Log.d(TAG, "Sms NOT Delivered");
                    break;
                }
                context.unregisterReceiver(this);
            }
        }, new IntentFilter(DELIVERED));

        SmsManager sms = SmsManager.getDefault();

        Log.d(TAG, " Trying to send SMS ");
        Log.d(TAG, " Text " + SmsText + " address " + PhoneAddress);
        sms.sendTextMessage(PhoneAddress, null, SmsText, sentPI, deliveredPI);

        SmsHandle = addToSmsFolder("outbox", PhoneAddress, SmsText);
        rsp.msgHandle = SmsHandle;
        rsp.response = ResponseCodes.OBEX_HTTP_OK;

        return rsp;
    }

    /**
     * Sets the message status (read/unread, delete)
     * @return Obex response code
     */
    public int msgStatus(String name,
            BluetoothMasAppParams bluetoothMasAppParams) {
        // TODO Assuming for SMS only

        Uri uri = Uri.parse("content://sms/" + name);

        if ((bluetoothMasAppParams.StatusIndicator != 0)
                && (bluetoothMasAppParams.StatusIndicator != 1)) {
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        if ((bluetoothMasAppParams.StatusValue != 0)
                && (bluetoothMasAppParams.StatusValue != 1)) {
            return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
        }

        Log.d(TAG, " STATUS INDICATOR = " + bluetoothMasAppParams.StatusValue);
        // ResponseCodes.OBEX_HTTP_INTERNAL_ERROR
        Cursor cr = context.getContentResolver().query(uri, null, null, null,
                null);
        if (cr.moveToFirst()) {

            if (bluetoothMasAppParams.StatusIndicator == 0) {
                /* Read Status */
                ContentValues values = new ContentValues();
                values.put("read", bluetoothMasAppParams.StatusValue);
                context.getContentResolver().update(uri, values, null, null);
            } else {
                if (bluetoothMasAppParams.StatusValue == 1) {
                    context.getContentResolver().delete(uri, null, null);
                }
            }
            return ResponseCodes.OBEX_HTTP_OK;
        }
        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;
    }

    /**
     * Enable/disable notification
     * @return Obex response code
     */
    public int notification(BluetoothDevice remoteDevice,
            BluetoothMasAppParams bluetoothMasAppParams) {
        // TODO Auto-generated method stub

        if (bluetoothMasAppParams.Notification == 1) {
            startMnsSession(remoteDevice);
            return ResponseCodes.OBEX_HTTP_OK;
        } else if (bluetoothMasAppParams.Notification == 0) {
            stopMnsSession(remoteDevice);
            return ResponseCodes.OBEX_HTTP_OK;
        }

        return ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;

    }

    /**
     * Start an MNS obex client session and push notification
     * whenever available
     */
    public void startMnsSession(BluetoothDevice remoteDevice) {
        Log.d(TAG, "Start MNS Client");
        mnsClient.getHandler().obtainMessage(BluetoothMns.MNS_CONNECT, -1, -1,
                remoteDevice).sendToTarget();
    }

    /**
     * Stop pushing notifications and disconnect MNS obex session
     */
    public void stopMnsSession(BluetoothDevice remoteDevice) {
        Log.d(TAG, "Stop MNS Client");
        mnsClient.getHandler().obtainMessage(BluetoothMns.MNS_DISCONNECT, -1,
                -1, remoteDevice).sendToTarget();
    }

    /**
     * Push an event over MNS client to MNS server
     */
    private void sendMnsEvent(String msg, String handle, String folder,
            String old_folder, String msgType) {
        Log.d(TAG, "Send MNS Event");
        mnsClient.sendMnsEvent(msg, handle, folder, old_folder, msgType);
    }
}
