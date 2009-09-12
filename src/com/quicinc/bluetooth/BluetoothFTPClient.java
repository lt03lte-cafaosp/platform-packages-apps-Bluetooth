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

import android.bluetooth.obex.BluetoothObexIntent;
import android.bluetooth.obex.BluetoothOpp;
import android.bluetooth.obex.BluetoothFtp;
import android.bluetooth.obex.IBluetoothFtpCallback;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.lang.Object;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

public class BluetoothFTPClient {
   private static final String TAG = "BluetoothFTPClient";
   private static final boolean V = true;
   BluetoothFtp mBluetoothFtp;

   private String mAddress = "";
   private String mName = "";

   private String mCurrentFolderName = "";
   private String mCurrentFolderPath = "";

   protected CallbackHandler mCallbackHandler = null;
   protected Callback mCallback = null;

   private List<BluetoothObexTransferFileInfo> mSendingFileList = new ArrayList<BluetoothObexTransferFileInfo>();
   private List<BluetoothObexTransferFileInfo> mReceivingFileList = new ArrayList<BluetoothObexTransferFileInfo>();
   private long mDoneBytes = 0;
   private long mTotalBytes = 0;

   public BluetoothFTPClient(Callback callback, String address, String name) {
      mCallback = callback;
      mAddress = address;
      mName = name;
      mDoneBytes = 0;
      mTotalBytes = 0;

      try {
         mBluetoothFtp = new BluetoothFtp();
         mCallbackHandler = new CallbackHandler();
      } catch (RuntimeException e) {
         Log.e(TAG, "", e);
         mBluetoothFtp = null;
      }
   }

   public String getAddress() {
      return mAddress;
   }

   public void setAddress(String address) {
      mAddress = address;
   }

   public String getName() {
      return mName;
   }

   public String toString() {
      return mName;
   }

   public void setName(String name) {
      mName = name;
   }

   /* createSession: This routine will initiate the FTP connection.
   *
   * @param bluetoothDeviceAddress: The Bluetooth address of the remote Device to
   * which the connection has be created
   *
   * @return None.
   */
   public boolean createSession () {
      boolean initatedConnection = false;
      try {
         if (null != mBluetoothFtp) {
            initatedConnection = mBluetoothFtp.connect(mAddress, mCallbackHandler);
         }
      } catch (RuntimeException e) {
         Log.e(TAG, "", e);
      }
      Log.i(TAG, "initiateConnect("+ mAddress + ") = "+ initatedConnection);
      return initatedConnection;
   }

   /* closeSession: This routine will initiate the FTP close connection.
   *
   * @param bluetoothDeviceAddress: The Bluetooth address of the remote Device to
   * which the connection has be created
   *
   * @return None.
   */
   public void closeSession () {
      if (null != mBluetoothFtp) {
         Log.i(TAG, "closeSession");
         mBluetoothFtp.close();
      }
      mName = "";
      mAddress = "";
      Cleanup();
   }//closeSession

   /**
    * Determine if a given connection is active.
    *
    * @param address Address of connection to query
    *
    * @return true indicates an existing connection with address
    *         false indicates unknown/closed connection.
    */
   public boolean isConnectionActive() {
      boolean serverConnected = false;

      if (null != mBluetoothFtp) {
         serverConnected = mBluetoothFtp.isConnectionActive(mAddress);
      }
      Log.i(TAG, "isConnectionActive("+ mAddress + ") = "+ serverConnected);
      return serverConnected;
   }

   /**
    * Request a list of the current folder contents on the remote device.
    * This is an asynchronous call.
    *
    * @return false indicates immediate error
    */
   public boolean listFolder () {
      boolean listFolderInitiated = false;
      if (null != mBluetoothFtp) {
         listFolderInitiated = mBluetoothFtp.listFolder();
      }
      Log.i(TAG, "listFolderInitiated returned " + listFolderInitiated);
      return listFolderInitiated;
   }//listFolder

   /**
    * Change the current folder on the remote device
    * This is an asynchronous call.
    *
    * @param folder Name of folder to change to
    *
    * @return false indicates immediate error
    */
   public boolean changeFolder (String folder) {
      boolean changeFolderInitiated = false;
      if (null != mBluetoothFtp) {
         changeFolderInitiated = mBluetoothFtp.changeFolder(folder);
      }
      Log.i(TAG, "changeFolderInitiated returned " + changeFolderInitiated);
      return changeFolderInitiated;
   }//changeFolder

   /**
     * Create a folder on the remote device
     * This is an asynchronous call.
     *
     * @param folder Name of folder to create
     *
     * @return false indicates immediate error
     */
   public boolean createFolder(String folder) {
      boolean createFolderInitiated = false;
      if (null != mBluetoothFtp) {
         createFolderInitiated = mBluetoothFtp.createFolder(folder);
      }
      Log.i(TAG, "createFolderInitiated returned " + createFolderInitiated);
      return createFolderInitiated;
   }//createFolder

   /**
    * Request a list of the current folder contents on the remote device.
    * This is an asynchronous call.
    *
    * @return false indicates immediate error
    */
   public boolean delete (String name) {
      boolean deleteInitiated = false;
      if (null != mBluetoothFtp) {
         deleteInitiated = mBluetoothFtp.delete(name);
      }
      Log.i(TAG, "deleteInitiated returned " + deleteInitiated);
      return deleteInitiated;
   }//delete


   /************* Transfer and Progress related ********************/
   public void onConnect() {
      mCurrentFolderName = "";
      mCurrentFolderPath = "/";
      mSendingFileList.clear();
      mReceivingFileList.clear();
      mDoneBytes = 0;
      mTotalBytes = 0;
   }

   public void onDisconnect() {
      mCurrentFolderName = "";
      mCurrentFolderPath = "/";
      mSendingFileList.clear();
      mReceivingFileList.clear();
      mDoneBytes = 0;
      mTotalBytes = 0;
   }

   /**
    * Send File
    */
   public boolean putFile(String localPathName, String remoteFileName) {
      boolean putStarted = false;
      if (isConnectionActive() && (!TextUtils.isEmpty(localPathName) )  && (!TextUtils.isEmpty(remoteFileName) )) {
         String msg = "Send File : " + localPathName + " to Server : " + mName;
         Log.i(TAG, (String) msg);
         putStarted = mBluetoothFtp.putFile(localPathName, remoteFileName);
      }
      return putStarted;
   }

   public void putFileStarted(String fileName) {
      if (isConnectionActive()) {
         String msg = "putFileStarted File : " + fileName + " to Server : " + mName;
         Log.i(TAG, (String) msg);
         File f = new File(fileName);

         BluetoothObexTransferFileInfo txfrFile = new BluetoothObexTransferFileInfo(fileName);
         synchronized (mSendingFileList) {
               mSendingFileList.add(txfrFile);
         }
         updateProgressPercent();
      }
   }

   /**
    * Send File
    */
   public boolean getFile(String localPathName, String remoteFileName) {
      boolean getStarted = false;
      if (isConnectionActive() && (!TextUtils.isEmpty(localPathName) )  && (!TextUtils.isEmpty(remoteFileName) )) {
         String msg = "Get File : " + remoteFileName + " from Server : " + mName + " as : " + localPathName;
         Log.i(TAG, msg);
         getStarted = mBluetoothFtp.getFile(localPathName, remoteFileName);
      }
      return getStarted;
   }

   public void getFileStarted(String localPathName, RemoteFile remoteFile) {
      boolean getStarted = false;
      if (isConnectionActive() && (remoteFile != null) && (!TextUtils.isEmpty(localPathName) )) {
         String msg = "Get File Started : " + remoteFile.getName() + " from Server : " + mName + " as : " + localPathName;
         Log.i(TAG, msg);
         BluetoothObexTransferFileInfo txfrFile = new BluetoothObexTransferFileInfo(localPathName);
         txfrFile.setDisplayName(remoteFile.getName());
         txfrFile.setTotal(remoteFile.getSize());
         synchronized (mReceivingFileList) {
            mReceivingFileList.add(txfrFile);
         }
         updateProgressPercent();
      }
   }

   public void CancelTransfer() {
      boolean txCancelled = false;
      boolean rxCancelled = false;
      if (mBluetoothFtp != null) {
         synchronized (mReceivingFileList) {
            Iterator<BluetoothObexTransferFileInfo> iter = mReceivingFileList.iterator();
            while (iter.hasNext()) {
               BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
               if (fileInfo != null) {
                  mBluetoothFtp.cancelTransfer(fileInfo.getName());
                  fileInfo.setDone(fileInfo.getTotal());
                  rxCancelled = true;
               }//if fileInfo
            }//while iter.hasNext
         }//synchronized (mReceivingFileList)
         synchronized (mSendingFileList) {
            Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
            while (iter.hasNext()) {
               BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
               if (fileInfo != null) {
                  /* Send a Cancel */
                  mBluetoothFtp.cancelTransfer(fileInfo.getName());
                  fileInfo.setDone(fileInfo.getTotal());
                  txCancelled  = true;
               }//if fileInfo
            }//while iter.hasNext
         }//synchronized (mSendingFileList)
      }
      Cleanup();
   }

   /**
    * When a Server is disconnected, and Tab = "Remote Files", then 1. Clean up
    * the server listing.
    */
   public void Cleanup() {
      synchronized (mSendingFileList) {
         mSendingFileList.clear();
      }
      synchronized (mReceivingFileList) {
         mReceivingFileList.clear();
      }
      mDoneBytes = 0;
      mTotalBytes = 0;
   }

   public int getTxFilesCount() {
      synchronized (mSendingFileList) {
         return mSendingFileList.size();
      }
   }

   public Object getTxFileItem(int position) {
      synchronized (mSendingFileList) {
         return mSendingFileList.get(position);
      }
   }

   public void addTxFile(BluetoothObexTransferFileInfo fileName) {
      synchronized (mSendingFileList) {
         mSendingFileList.add(fileName);
      }
   }

   public void removeTxFile(String fileName) {
      synchronized (mSendingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               if (fileName.equals(fileInfo.getName())) {
                  /* Set Done = total and let the updateProgress take care of remove the file from the list */
                  fileInfo.setDone(fileInfo.getTotal());
                  iter.remove();
                  Log.i(TAG, "Removed file from mSendingFileList : "+fileName );
                  break;
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mSendingFileList)
   }

   public int getRxFilesCount() {
      synchronized (mReceivingFileList) {
         return mReceivingFileList.size();
      }
   }

   public Object getRxFileItem(int position) {
      return mReceivingFileList.get(position);
   }

   public void addRxFile(BluetoothObexTransferFileInfo fileName) {
      synchronized (mReceivingFileList) {
         mReceivingFileList.add(fileName);
      }
   }

   public void removeRxFile(String fileName) {

      Log.i(TAG, "removeRxFile : ("+fileName+")");
      synchronized (mReceivingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mReceivingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               if (fileName.equals(fileInfo.getName())) {
                  /* Set Done = total and let the updateProgress take care of remove the file from the list */
                  fileInfo.setDone(fileInfo.getTotal());
                  iter.remove();
                  Log.i(TAG, "Removed file from mReceivingFileList : "+fileName );
                  break;
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mReceivingFileList)
   }

   public void TransferComplete() {
      synchronized (mSendingFileList) {
         mSendingFileList.clear();
      }
      synchronized (mReceivingFileList) {
         mReceivingFileList.clear();
      }
      mDoneBytes = 0;
      mTotalBytes = 0;
   }

   public boolean isTransferInProgress() {
      if((getTxFilesCount() > 0) || (getRxFilesCount() > 0)) {
         return true;
      }
      return false;
   }

   public void updateProgressPercent() {
      long currentDone = 0;
      long currentTotal = 0;
      int totalFiles = 0;
      List<BluetoothObexTransferFileInfo> listCopy;
      Iterator<BluetoothObexTransferFileInfo> iter;

      synchronized (mSendingFileList) {
         listCopy = mSendingFileList;
         totalFiles = mSendingFileList.size();
         iter = listCopy.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               long done = fileInfo.getDone();
               long total = fileInfo.getTotal();
               if (total > 0) {
                  // Log.i("TransferProgress",
                  // "Tx File Name::Size::Done --> "
                  // + fileInfo.getName() +":"+ total +":"+ done);
                  if (done > total) {
                     done = total;
                  }
                  currentTotal += total;
                  currentDone += done;
               }
            }// if fileInfo
         }// while iter.hasNext
      }

      synchronized (mReceivingFileList) {
         listCopy = mReceivingFileList;
         totalFiles += mReceivingFileList.size();
         iter = listCopy.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               long done = fileInfo.getDone();
               long total = fileInfo.getTotal();
               if (total > 0) {
                  // Log.i("TransferProgress",
                  // "Rx File Name::Size::Done --> "
                  // + fileInfo.getName() +":"+ total +":"+ done);
                  if (done > total) {
                     done = total;
                  }

                  currentTotal += total;
                  currentDone += done;
               }
            }// if fileInfo
         }// while iter.hasNext
      }

      mDoneBytes = currentDone;
      mTotalBytes = currentTotal;

      Log.i("TransferProgress", "num files : ("+totalFiles+") mDoneBytes::mTotalBytes --> " + mDoneBytes
            + ":" + mTotalBytes);
   }

   public int getCurrentProgressPercent() {
      if ((mTotalBytes > 0) && (mTotalBytes >= mDoneBytes)) {
         return((int) ((mDoneBytes * 100) / mTotalBytes));
      }
      return(100);
   }

   public long getTotalBytes() {
      return(mTotalBytes);
   }

   public long getDoneBytes() {
      return(mDoneBytes);
   }

   /* onProgressIndication
    *  This routine will handle the PROGRESS_ACTION broadcast and
    *  updates the progress if the progress was for one of its
    *  file.
    *
    * @param fileName: The name of the file that is being transfered
    * @param totalBytes:  Total number of bytes of the file
    * @param bytesDone:   Number of bytes sent so far
    *
    * @return None.
    */
   public void onProgressIndication(String fileName, int bytesTotal, int bytesDone) {
      Log.i(TAG, "onProgressIndication : fileName " + fileName +
                  " Total : " + bytesTotal  +
                  " done so far : " + bytesDone);
      synchronized (mSendingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mSendingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               Log.i(TAG, "onProgressIndication : SendList fileName " + fileName + "( "+fileInfo.getDone() + " of " + fileInfo.getTotal());
               if (fileName.equals(fileInfo.getName())) {
                  fileInfo.setDone((long)bytesDone);
                  Log.i(TAG, "onProgressIndication : Updated fileName " + fileName + "( "+fileInfo.getDone() + " of " + fileInfo.getTotal());
                  //TODO: bytesTotal returning 0, so ignore it
                  //fileInfo.setTotal((long)bytesTotal);
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mSendingFileList)
      synchronized (mReceivingFileList) {
         Iterator<BluetoothObexTransferFileInfo> iter = mReceivingFileList.iterator();
         while (iter.hasNext()) {
            BluetoothObexTransferFileInfo fileInfo = (BluetoothObexTransferFileInfo) iter.next();
            if (fileInfo != null) {
               Log.i(TAG, "onProgressIndication : GetList fileName " + fileName + "( "+fileInfo.getDone() + " of " + fileInfo.getTotal());
               if (fileName.equals(fileInfo.getName())) {
                  fileInfo.setDone(bytesDone);
                  Log.i(TAG, "onProgressIndication : Updated fileName " + fileName + "( "+fileInfo.getDone() + " of " + fileInfo.getTotal());
                  //TODO: bytesTotal returning 0, so ignore it
                  //fileInfo.setTotal(bytesTotal);
               }
            }//if fileInfo != null
         }//while iter.hasNext
      }//synchronized (mReceivingFileList)
      updateProgressPercent();
   }

   public void onTransmitCompleteIndication(String fileName, boolean success, String errorString) {
      Log.i(TAG, "onTransmitCompleteIndication : fileName " + fileName +
                  " success : " + success  +
                  " errorString : " + errorString);
      removeTxFile(fileName);
      updateProgressPercent();
   }

   public void onReceiveCompleteIndication(String fileName, boolean success) {
      Log.i(TAG, "onReceiveCompleteIndication : fileName " + fileName + " success : " + success);
      removeRxFile(fileName);
      updateProgressPercent();
   }

   /************* Transfer and Progress related - END ********************/

   private class CallbackHandler extends IBluetoothFtpCallback.Stub {
      private static final String TAG = "BluetoothFTPClient:CallbackHandler";
      private static final boolean V = true;
      private static final int TYPE_CREATE_SESSION_COMPLETE = 1;
      private static final int TYPE_SESSION_CLOSED = 2;
      private static final int TYPE_CREATE_FOLDER_COMPLETE = 3;
      private static final int TYPE_CHANGE_FOLDER_COMPLETE = 4;
      private static final int TYPE_LIST_FOLDER_COMPLETE = 5;
      private static final int TYPE_GETFILE_COMPLETE = 6;
      private static final int TYPE_PUTFILE_COMPLETE = 7;
      private static final int TYPE_DELETE_COMPLETE = 8;

      private final Handler mHandler;
      CallbackHandler() {

         mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
               _handleMessage(msg);
            }
         };
      }

      /**
       * @param isError true if error executing createSession, false otherwise
      */
      public void onCreateSessionComplete(boolean isError) {
         if (V) {
            Log.i(TAG, "onCreateSessionComplete : " + isError);
         }
         Message msg = Message.obtain();
         msg.what = TYPE_CREATE_SESSION_COMPLETE;
         Bundle b = new Bundle();
         b.putBoolean("isError", isError);
         msg.obj = b;
         mHandler.sendMessage(msg);
      }

      /**
       * Notification that this session has been closed.  The client will
       * need to reconnect if future FTP operations are required.
       */
      public void onObexSessionClosed() {
         if (V) {
            Log.i(TAG, "onObexSessionClosed");
         }
         Message msg = Message.obtain();
         msg.what = TYPE_SESSION_CLOSED;
         msg.obj = null;
         mHandler.sendMessage(msg);
      }
      /**
       * @param folder name of folder to create
       * @param isError true if error executing createFolder, false otherwise
      */
      public void onCreateFolderComplete(String folder, boolean isError) {
         if (V) {
            Log.i(TAG, "onCreateFolderComplete : Folder: "+folder+ " isError : " + isError);
         }
         Message msg = Message.obtain();
         msg.what = TYPE_CREATE_FOLDER_COMPLETE;
         Bundle b = new Bundle();
         b.putString("folder", folder);
         b.putBoolean("isError", isError);
         msg.obj = b;
         mHandler.sendMessage(msg);
      }

      /**
       * @param folder name of folder to change to
       * @param isError true if error executing changeFolder, false otherwise
      */
      public void onChangeFolderComplete(String folder, boolean isError) {
         if (V) {
            Log.i(TAG, "onChangeFolderComplete : Folder: "+folder+ " isError : " + isError);
         }
         Message msg = Message.obtain();
         msg.what = TYPE_CHANGE_FOLDER_COMPLETE;
         Bundle b = new Bundle();
         b.putString("folder", folder);
         b.putBoolean("isError", isError);
         msg.obj = b;
         mHandler.sendMessage(msg);
      }

      /**
       * @param result List of Map object containing information about the current folder contents.
       * <p>
       * <ul>
       *    <li>Name (String): object name
       *    <li>Type (String): object type
       *    <li>Size (int): object size, or number of folder items
       *    <li>Permission (String): group, owner, or other permission
       *    <li>Modified (int): Last change
       *    <li>Accessed (int): Last access
       *    <li>Created (int): Created date
       * </ul>
       * @param isError true if error executing listFolder, false otherwise
       */
      public void onListFolderComplete(List<Map> result, boolean isError) {
         if (V) {
            Log.i(TAG, "onListFolderComplete : isError : " + isError);
         }
         int arg1 = ((isError == true)? 1 :0);
         Object obj = result;
         Message msg = mHandler.obtainMessage(TYPE_LIST_FOLDER_COMPLETE, arg1, 0 , obj);
         mHandler.sendMessage(msg);
      }

      /**
       * @param localFilename Filename on local device
       * @param remoteFilename Filename on remote device
       * @param isError true if error executing getFile, false otherwise
      */
      public void onGetFileComplete(String localFilename, String remoteFilename, boolean isError) {
         if (V) {
            Log.i(TAG, "onGetFileComplete : local: "+localFilename+ "Remote: "+remoteFilename+ " isError : " + isError);
         }
         Message msg = Message.obtain();
         msg.what = TYPE_GETFILE_COMPLETE;
         Bundle b = new Bundle();
         b.putString("localFilename", localFilename);
         b.putString("remoteFilename", remoteFilename);
         b.putBoolean("isError", isError);
         msg.obj = b;
         mHandler.sendMessage(msg);
      }

      /**
       * @param localFilename Filename on local device
       * @param remoteFilename Filename on remote device
       * @param isError true if error executing putFile, false otherwise
      */
      public void onPutFileComplete(String localFilename, String remoteFilename, boolean isError) {
         if (V) {
            Log.i(TAG, "onPutFileComplete : local: "+localFilename+ "Remote: "+remoteFilename+ "isError : " + isError);
         }
         Message msg = Message.obtain();
         msg.what = TYPE_PUTFILE_COMPLETE;
         Bundle b = new Bundle();
         b.putString("localFilename", localFilename);
         b.putString("remoteFilename", remoteFilename);
         b.putBoolean("isError", isError);
         msg.obj = b;
         mHandler.sendMessage(msg);
      }

      /**
       * @param name name of file/folder to delete
       * @param isError true if error executing delete, false otherwise
      */
      public void onDeleteComplete(String name, boolean isError) {
         if (V) {
            Log.i(TAG, "onDeleteComplete : name: " + name + " isError : " + isError);
         }
         Message msg = Message.obtain();
         msg.what = TYPE_DELETE_COMPLETE;
         Bundle b = new Bundle();
         b.putString("name", name);
         b.putBoolean("isError", isError);
         msg.obj = b;
         mHandler.sendMessage(msg);
      }

      private void _handleMessage(Message msg) {
         if(mCallback == null) {
            Log.e(TAG, "Callback not installed");
            return;
         }

         switch (msg.what) {
         case TYPE_CREATE_SESSION_COMPLETE: {
            Bundle b = (Bundle) msg.obj;
            boolean isError = b.getBoolean("isError");
            mCallback.onCreateSessionComplete(isError);
            break;
         }
         case TYPE_SESSION_CLOSED: {
            mCallback.onObexSessionClosed();
            break;
         }

         case TYPE_CREATE_FOLDER_COMPLETE:{
            Bundle b = (Bundle) msg.obj;
            String folder = b.getString("folder");
            boolean isError = b.getBoolean("isError");
            mCallback.onCreateFolderComplete(folder, isError);
            break;
         }
         case TYPE_CHANGE_FOLDER_COMPLETE:{
            Bundle b = (Bundle) msg.obj;
            String folder = b.getString("folder");
            boolean isError = b.getBoolean("isError");
            mCallback.onChangeFolderComplete(folder, isError);
            break;
         }
         case TYPE_LIST_FOLDER_COMPLETE:{
            boolean isError = false;
            if(msg.arg1 == 1) {
               isError = true;
            }
            List<Map>folderList = (List<Map>)msg.obj;
            mCallback.onListFolderComplete(folderList, isError);
            break;
         }
         case TYPE_GETFILE_COMPLETE: {
            Bundle b = (Bundle) msg.obj;
            String localFilename = b.getString("localFilename");
            String remoteFilename = b.getString("remoteFilename");
            boolean isError = b.getBoolean("isError");
            mCallback.onGetFileComplete(localFilename, remoteFilename, isError);
            break;
         }
         case TYPE_PUTFILE_COMPLETE:{
            Bundle b = (Bundle) msg.obj;
            String localFilename = b.getString("localFilename");
            String remoteFilename = b.getString("remoteFilename");
            boolean isError = b.getBoolean("isError");
            mCallback.onPutFileComplete(localFilename, remoteFilename, isError);
            break;
         }
         case TYPE_DELETE_COMPLETE:{
            Bundle b = (Bundle) msg.obj;
            String name = b.getString("name");
            boolean isError = b.getBoolean("isError");
            mCallback.onDeleteComplete(name, isError);
            break;
         }
         }
      }
   }

   public interface Callback {
      /**
       * @param isError true if error executing createSession, false otherwise
      */
      void onCreateSessionComplete(boolean isError);

      /**
       * Notification that this session has been closed.  The client will
       * need to reconnect if future FTP operations are required.
       */
      void onObexSessionClosed();

      /**
       * @param folder name of folder to create
       * @param isError true if error executing createFolder, false otherwise
      */
      void onCreateFolderComplete(String folder, boolean isError);

      /**
       * @param folder name of folder to change to
       * @param isError true if error executing changeFolder, false otherwise
      */
      void onChangeFolderComplete(String folder, boolean isError);

      /**
       * @param result List of Map object containing information about the current folder contents.
       * <p>
       * <ul>
       *    <li>Name (String): object name
       *    <li>Type (String): object type
       *    <li>Size (int): object size, or number of folder items
       *    <li>Permission (String): group, owner, or other permission
       *    <li>Modified (int): Last change
       *    <li>Accessed (int): Last access
       *    <li>Created (int): Created date
       * </ul>
       * @param isError true if error executing listFolder, false otherwise
       */
      void onListFolderComplete(List<Map> result, boolean isError);

      /**
       * @param localFilename Filename on local device
       * @param remoteFilename Filename on remote device
       * @param isError true if error executing getFile, false otherwise
      */
      void onGetFileComplete(String localFilename, String remoteFilename, boolean isError);

      /**
       * @param localFilename Filename on local device
       * @param remoteFilename Filename on remote device
       * @param isError true if error executing putFile, false otherwise
      */
      void onPutFileComplete(String localFilename, String remoteFilename, boolean isError);

      /**
       * @param name name of file/folder to delete
       * @param isError true if error executing delete, false otherwise
      */
      void onDeleteComplete(String name, boolean isError);

      /* TODO: update with any new functionality from BM3 obexd */
   }
} /* FileServer */
