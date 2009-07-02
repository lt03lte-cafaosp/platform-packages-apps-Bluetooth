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

package com.quic.bluetooth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import android.util.Log;
public class RemoteFile {
   /** TAG for log messages. */
   private static final String TAG = "RemoteFile";
   public static final String TYPE_FILE_NAME = "Name";
   public static final String TYPE_FILE_TYPE = "Type";
   public static final String TYPE_FILE_SIZE = "Size";
   public static final String TYPE_FILE_PERMISSION = "Permission";
   public static final String TYPE_FILE_MODIFIED = "Modified";
   public static final String TYPE_FILE_ACCESSED = "Accessed";
   public static final String TYPE_FILE_CREATED  = "Created";

   static final String TYPE_FILE = "file";
   static final String TYPE_FOLDER = "folder";
   static final String TYPE_PARENT_FOLDER = "parent-folder";

   // Name (String): object name
   private String mName="";
   // Type (String): object type
   private String mType="";
   // Size (int): object size, or number of folder items
   private int mSize=0;
   // Permission (String): group, owner, or other permission
   private String mPermission="";
   // Modified (int): Last change
   private int mModified=0;
   // Accessed (int): Last access
   private int mAccessed=0;
   // Created (int): Created date
   private int mCreated=0;

   private String mParentFolder="";

   public RemoteFile(Map fileInfo) {
      int mapsize = fileInfo.size();
      Object[] FileInfokeyValue = fileInfo.entrySet().toArray();
      for (int mapItem = 0; mapItem < mapsize; mapItem++)
      {
          Map.Entry entry = (Map.Entry) FileInfokeyValue[mapItem];
          Object key = entry.getKey();
          Object value = entry.getValue();
          if(TYPE_FILE_NAME.equalsIgnoreCase(key.toString())) {
              mName = (String) value;
          } else if(TYPE_FILE_TYPE.equalsIgnoreCase(key.toString())) {
              mType = (String) value;
          } else if(TYPE_FILE_SIZE.equalsIgnoreCase(key.toString())) {
              mSize = ((Integer) value);
          } else if(TYPE_FILE_PERMISSION.equalsIgnoreCase(key.toString())) {
              mPermission = (String) value;
          } else if(TYPE_FILE_MODIFIED.equalsIgnoreCase(key.toString())) {
              mModified = ((Integer) value);
          } else if(TYPE_FILE_ACCESSED.equalsIgnoreCase(key.toString())) {
              mAccessed = ((Integer) value);
          } else if(TYPE_FILE_CREATED.equalsIgnoreCase(key.toString())) {
              mCreated = ((Integer) value);
          }
      }

     if (TYPE_PARENT_FOLDER.equalsIgnoreCase(mType)) {
        mName = "..";
     }

     if(mName == null) {
        mName = "No Name";
     }
     Log.i(TAG, "------- Adding File ----- ");
      Log.i(TAG, TYPE_FILE_NAME + " : " + mName);
      Log.i(TAG, TYPE_FILE_TYPE + " : " + mType);
      Log.i(TAG, TYPE_FILE_SIZE + " : " + mSize);
      Log.i(TAG, TYPE_FILE_PERMISSION + " : " + mPermission);
      Log.i(TAG, TYPE_FILE_MODIFIED + " : " + mModified);
      Log.i(TAG, TYPE_FILE_ACCESSED + " : " + mAccessed);
      Log.i(TAG, TYPE_FILE_CREATED + " : " + mCreated);
   }

    public String getName() {
      return (mName);
   }

    public int getSize() {
        return (mSize);
    }

    public String getParent() {
      return (mParentFolder);
   }

   public boolean isDirectory() {
      boolean is_directory = false;
      if(TYPE_FOLDER.equalsIgnoreCase(mType)) {
          is_directory = true;
      }else if (TYPE_PARENT_FOLDER.equalsIgnoreCase(mType)) {
         is_directory = true;
      }
      return (is_directory);
   }

   public boolean isParentFolder() {
      boolean isParentdirectory = false;
      if (TYPE_PARENT_FOLDER.equalsIgnoreCase(mType)) {
         isParentdirectory = true;
      }
      return (isParentdirectory);
   }

}
