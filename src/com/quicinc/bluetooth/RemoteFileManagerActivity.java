/*
 * Copyright (C) 2008 OpenIntents.org
 * Copyright (c) 2009, Code Aurora Forum. All rights reserved.
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

/*
 * Based on AndDev.org's file browser V 2.0.
 */

package com.quicinc.bluetooth;

import com.quicinc.bluetooth.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParserException;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;

import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class RemoteFileManagerActivity extends ListActivity  implements BluetoothFTPClient.Callback {
    private static final String TAG = "RemoteFileManagerActivity";

    public static final int MAX_SERVER_NAME_DISPLAY = 20;

    // public static final int REQUEST_CODE_MOVE = 1;
    public static final int SUBACTIVITY_PICK_BT_DEVICE = 2;

    public static final int MENU_SELECT_SERVER = Menu.FIRST + 2;
    public static final int MENU_DISCONNECT = Menu.FIRST + 3;
    public static final int MENU_NEW_FOLDER = Menu.FIRST + 4;
    public static final int MENU_DELETE = Menu.FIRST + 5;
    public static final int MENU_DOWNLOAD = Menu.FIRST + 7;

    private static final int DIALOG_NEW_FOLDER = 1;
    private static final int DIALOG_DELETE = 2;
    private static final int DIALOG_RENAME = 3;

    private static final String BUNDLE_CURRENT_DIRECTORY = "current_directory";
    private static final String BUNDLE_CONTEXT_FILE = "context_file";
    private static final String BUNDLE_CONTEXT_TEXT = "context_text";
    private static final String BUNDLE_STEPS_BACK = "steps_back";

    /** Contains directories and files together */
    private List<IconifiedText> directoryEntries = new ArrayList<IconifiedText>();

    /** Dir separate for sorting */
    List<IconifiedText> mListDir = new ArrayList<IconifiedText>();

    /** Files separate for sorting */
    List<IconifiedText> mListFile = new ArrayList<IconifiedText>();

    /** Files List of current directory */
    List<RemoteFile> mCurrentListFolder = new ArrayList<RemoteFile>();

    private String currentDirectory = "/";
    private String parentDirectory = "/";

    private MimeTypes mMimeTypes;

    private String mContextText;
    private Drawable mContextIcon;

    private FrameLayout mFileListLayout;
    private LinearLayout mDirectoryButtons;
    private LinearLayout mServerConnectButtonLayout;
    private Button mServerStatusButton;
    private TextView mServerConnectHelp;
    private TextView mFolderEmpty;

    /* Work in progress for asynchronous processing */
    private ProgressDialog mProgressDlg;

    /** Connected or not */
    private static BluetoothBrowserActivity mContext;
    public static BluetoothFTPClient mFTPClient;
    public static boolean mSessionCreated;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.v(TAG, "onCreate");

        setContentView(R.layout.server_filelist);

        Activity parent = getParent();
        if (parent != null && parent instanceof BluetoothBrowserActivity) {
            mContext = (BluetoothBrowserActivity) parent;
        } else {
            return;
        }
        mFTPClient = new BluetoothFTPClient(RemoteFileManagerActivity.this, "", "");
        mContext.setRemoteManagerActivity(this);
        mSessionCreated = false;
        mFileListLayout = (FrameLayout) findViewById(R.id.display_files);

        mDirectoryButtons = (LinearLayout) findViewById(R.id.directory_buttons);
        mServerConnectButtonLayout = (LinearLayout) findViewById(R.id.button_server_layout);
        mServerStatusButton = (Button) findViewById(R.id.button_server);
        mServerConnectHelp = (TextView) findViewById(R.id.serverconnecthelp);
        mFolderEmpty = (TextView) findViewById(R.id.empty);
        getListView().setOnCreateContextMenuListener(this);
        getListView().setEmptyView(mFolderEmpty);

        mServerStatusButton.setOnClickListener(new View.OnClickListener() {
           public void onClick(View view) {
              if (mFTPClient != null) {
                 if (!mFTPClient.isConnectionActive()) {
                    handleServerSelect();
                 }
              }
           }
        });

        /* Create map of extensions */
        getMimeTypes();

        File browseto = null;

        if (icicle != null) {
           mContextText = icicle.getString(BUNDLE_CONTEXT_TEXT);
        }

        updateServerStatus();
        refreshList();
    }

   @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    /**
      *
      */
    private void refreshDirectoryPanel() {
        setDirectoryButtons();
    }

    /*
     * @Override protected void onResume() { // TODO Auto-generated method stub
     * super.onResume(); }
     */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // TODO Auto-generated method stub
        super.onSaveInstanceState(outState);
        Log.v(TAG, "onSaveInstanceState");

        outState.putString(BUNDLE_CONTEXT_TEXT, mContextText);
    }

    /**
     *
     */
    private void getMimeTypes() {
        MimeTypeParser mtp = new MimeTypeParser();
        XmlResourceParser in = getResources().getXml(R.xml.mimetypes);

        try {
            mMimeTypes = mtp.fromXmlResource(in);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "PreselectedChannelsActivity: XmlPullParserException", e);
            throw new RuntimeException(
                    "PreselectedChannelsActivity: XmlPullParserException");
        } catch (IOException e) {
            Log.e(TAG, "PreselectedChannelsActivity: IOException", e);
            throw new RuntimeException(
                    "PreselectedChannelsActivity: IOException");
        }
    }

    /**
     * This function browses up one level according to the field:
     * currentDirectory
     */
    private void upOneLevel() {
        goParentDir();
    }

    /** Go to Home Folder */
    private void goHomeDir() {
        if (mFTPClient != null) {
            boolean isChangeFolderInitiated = false;
            isChangeFolderInitiated = mFTPClient.changeFolder("");
            if (isChangeFolderInitiated == false) {
                Toast.makeText(this, R.string.ftp_change_folder_failed, Toast.LENGTH_SHORT).show();
         } else {
            String szStr = getResources().getString(R.string.ftp_changing_folder);
            showBusy(getServerName(), szStr);
            }
        }
    }

    /** Go to Parent Folder */
    private void goParentDir() {
        if (mFTPClient != null) {
            boolean isChangeFolderInitiated = false;
            isChangeFolderInitiated = mFTPClient.changeFolder(getString(R.string.up_one_level));
            if (isChangeFolderInitiated == false) {
                Toast.makeText(this, R.string.ftp_change_folder_failed, Toast.LENGTH_SHORT).show();
         } else {
            String szStr = getResources().getString(R.string.ftp_changing_folder);
            showBusy(getServerName(), szStr);
            }
        }
    }

    /** Initiate the refresh of the folder */
    private void refreshList() {
        if (mFTPClient != null) {
            if (mFTPClient.isConnectionActive()) {
                boolean isListFolderInitiated = false;
                isListFolderInitiated = mFTPClient.listFolder();
                if (isListFolderInitiated == false) {
                    Toast.makeText(this, R.string.ftp_refresh_folder_failed, Toast.LENGTH_SHORT).show();
            } else {
               String szStr = getResources().getString(R.string.ftp_get_folder_list);
               showBusy(getServerName(), szStr);
                }
            }
        }
    }

    public void onListFolder(List<Map> fileList) {

        /* Empty the list */
        directoryEntries.clear();
        mListDir.clear();
        mListFile.clear();
        mCurrentListFolder.clear();

        for (Map item : fileList) {
            RemoteFile remotefile = new RemoteFile(item);
            mCurrentListFolder.add(remotefile);
        }
        Drawable currentIcon = null;
        if (mCurrentListFolder.size() > 0) {
            for (RemoteFile currentFile : mCurrentListFolder) {
                if (currentFile.isDirectory()) {
                    currentIcon = getResources().getDrawable(
                            R.drawable.ic_launcher_folder);
                        mListDir.add(new IconifiedText(currentFile.getName(),
                                currentIcon));
                } else {
                    String fileName = currentFile.getName();
                    String mimetype = mMimeTypes.getMimeType(fileName);

                    currentIcon = getDrawableForMimetype(mimetype);
                    if (currentIcon == null) {
                        currentIcon = getResources().getDrawable(
                                R.drawable.icon_file);
                    }
                    mListFile.add(new IconifiedText(currentFile.getName(),
                            currentIcon));
                }
            }
        }
        Collections.sort(mListDir);
        Collections.sort(mListFile);

        addAllElements(directoryEntries, mListDir);
        addAllElements(directoryEntries, mListFile);

        IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this);
        itla.setListItems(directoryEntries);
        setListAdapter(itla);
        updateServerStatus();
        refreshDirectoryPanel();
    }

    private void selectInList(RemoteFile selectFile) {
        String filename = selectFile.getName();
        IconifiedTextListAdapter la = (IconifiedTextListAdapter) getListAdapter();
        int count = la.getCount();
        for (int i = 0; i < count; i++) {
            IconifiedText it = (IconifiedText) la.getItem(i);
            if (it.getText().equals(filename)) {
                getListView().setSelection(i);
                break;
            }
        }
    }

    private void addAllElements(List<IconifiedText> addTo,
            List<IconifiedText> addFrom) {
        int size = addFrom.size();
        for (int i = 0; i < size; i++) {
            addTo.add(addFrom.get(i));
        }
    }

    private void setDirectoryButtons() {
        mDirectoryButtons.removeAllViews();

        int WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT;

        // Add home button separately
        Button homeButton = new Button(this);
        homeButton.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT,
                WRAP_CONTENT));
        String displayName;
        String name = getServerName();
        if (name.length() > MAX_SERVER_NAME_DISPLAY) {
            displayName = name.substring(0, (MAX_SERVER_NAME_DISPLAY - 3)) + "..";
        } else {
            displayName = name;
        }
        homeButton.setText(displayName);
        homeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                goHomeDir();
            }
        });
        mDirectoryButtons.addView(homeButton);
        //checkButtonLayout();
    }

    private void checkButtonLayout() {

        // Let's measure how much space we need:
        int spec = View.MeasureSpec.UNSPECIFIED;
        mDirectoryButtons.measure(spec, spec);

        int requiredwidth = mDirectoryButtons.getMeasuredWidth();
        int width = getWindowManager().getDefaultDisplay().getWidth();

        if (requiredwidth > width) {
            int WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT;

            // Create a new button that shows that there is more to the left:
            ImageButton ib = new ImageButton(this);
            ib.setImageResource(R.drawable.ic_menu_back_small);
            ib.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT,
                    WRAP_CONTENT));

            ib.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) {
                    // Up one directory.
                    upOneLevel();
                }
            });
            mDirectoryButtons.addView(ib, 0);

            // New button needs even more space
            ib.measure(spec, spec);
            requiredwidth += ib.getMeasuredWidth();

            // Need to take away some buttons
            // but leave at least "back" button and one directory button.
            while (requiredwidth > width
                    && mDirectoryButtons.getChildCount() > 2) {
                View view = mDirectoryButtons.getChildAt(1);
                requiredwidth -= view.getMeasuredWidth();

                mDirectoryButtons.removeViewAt(1);
            }
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        Log.i(TAG, "onListItemClick : " + position);

        String selectedFileString = directoryEntries.get(position).getText();
        RemoteFile remoteFile = getRemoteFileObject(selectedFileString);

        if(remoteFile == null) {
            return;
        }

        if (selectedFileString.equals(getString(R.string.up_one_level))) {
            upOneLevel();
        } else {
            if (remoteFile.isDirectory()) {
                // If we click on folders, we can return later by the "back"
                // key.
                if (mFTPClient != null) {
                    boolean isChangeFolderInitiated = false;
                    isChangeFolderInitiated = mFTPClient.changeFolder(selectedFileString);

                    if (isChangeFolderInitiated == false) {
                       Toast.makeText(this, R.string.ftp_change_folder_failed, Toast.LENGTH_SHORT).show();
                    } else {
                        String szStr = getResources().getString(R.string.ftp_changing_folder);
                        showBusy(getServerName(), szStr);
                    }
                }
            } else {
                downloadFile(selectedFileString);
            }
        }
    }

    /**
     * Return the Drawable that is associated with a specific Mime type for the
     * VIEW action.
     *
     * @param mimetype
     * @return
     */
    Drawable getDrawableForMimetype(String mimetype) {
        PackageManager pm = getPackageManager();

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setType(mimetype);

        final List<ResolveInfo> lri = pm.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);

        if (lri != null && lri.size() > 0) {
            // return first element
            final ResolveInfo ri = lri.get(0);
            return ri.loadIcon(pm);
        }

        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (mFTPClient != null) {
           if (mFTPClient.isConnectionActive()) {
               menu.add(0, MENU_NEW_FOLDER, 0, R.string.menu_new_folder).setIcon(
                       android.R.drawable.ic_menu_add).setShortcut('0', 'f');
               menu.add(0, MENU_DISCONNECT, 0,
                       getString(R.string.menu_disconnect, ""))
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setShortcut('0', 'd');
           } else {
               menu.add(0, MENU_SELECT_SERVER, 0, getString(R.string.menu_select))
                       .setIcon(android.R.drawable.ic_menu_search)
                  .setShortcut('0', 'c');
           }
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mFTPClient != null) {
           if (mFTPClient.isConnectionActive()) {
               if (mSessionCreated == true) {
                   if (menu.findItem(MENU_NEW_FOLDER) == null) {
                       menu.add(0, MENU_NEW_FOLDER, 0, R.string.menu_new_folder)
                               .setIcon(android.R.drawable.ic_menu_add)
                               .setShortcut('0', 'f');
                   }
                   if (menu.findItem(MENU_DISCONNECT) == null) {
                       menu.add(0, MENU_DISCONNECT, 0,
                               getString(R.string.menu_disconnect, "")).setIcon(
                               android.R.drawable.ic_menu_close_clear_cancel)
                               .setShortcut('0', 'd');
                   }
                   menu.removeItem(MENU_SELECT_SERVER);
               } else {
                   if (menu.findItem(MENU_SELECT_SERVER) == null) {
                       menu.add(0, MENU_SELECT_SERVER, 0,
                               getString(R.string.menu_select)).setIcon(
                               android.R.drawable.ic_menu_search)
                               .setShortcut('0', 'c');
                   }
                   menu.removeItem(MENU_NEW_FOLDER);
                   menu.removeItem(MENU_DISCONNECT);
               }
           } else {
              if (Environment.getExternalStorageState().equals(
                      Environment.MEDIA_MOUNTED)) {
                  if (menu.findItem(MENU_SELECT_SERVER) == null) {
                      menu.add(0, MENU_SELECT_SERVER, 0,
                              getString(R.string.menu_select)).setIcon(
                              android.R.drawable.ic_menu_search)
                              .setShortcut('0', 'c');
                  }
              }
              else
              {
                 menu.removeItem(MENU_SELECT_SERVER);
              }
              menu.removeItem(MENU_NEW_FOLDER);
              menu.removeItem(MENU_DISCONNECT);
           }
      }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Intent intent;
        switch (item.getItemId()) {
        case MENU_NEW_FOLDER:
            showDialog(DIALOG_NEW_FOLDER);
            return true;

        case MENU_SELECT_SERVER:
             if (mFTPClient.isConnectionActive()) {
                 Toast.makeText(this, R.string.error_ftp_connect_timeout, Toast.LENGTH_LONG).show();
             } else {
                /* Connect to Server */
                 handleServerSelect();
             }
            return true;

        case MENU_DISCONNECT:
            /* Disconnect to Server */
            mSessionCreated = false;
            handleServerDisconnect();
            return true;
        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
      IconifiedText it = directoryEntries.get(info.position);

      if(it != null) {
         menu.setHeaderTitle(it.getText());
         menu.setHeaderIcon(it.getIcon());

         String selectedFileString = it.getText();
         RemoteFile remoteFile = getRemoteFileObject(selectedFileString);

         if(remoteFile != null) {
            if (!remoteFile.isDirectory()) {
               menu.add(0, MENU_DOWNLOAD, 0, R.string.menu_download);
            }
         }
         if (!selectedFileString.equals(getString(R.string.up_one_level))) {
            menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
         }
      }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        super.onContextItemSelected(item);
        AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item
                .getMenuInfo();

        // Remember current selection
        IconifiedText ic = directoryEntries.get(menuInfo.position);
        mContextText = ic.getText();
        mContextIcon = ic.getIcon();

        switch (item.getItemId()) {
        case MENU_DELETE:
            showDialog(DIALOG_DELETE);
            return true;

        case MENU_DOWNLOAD:
            downloadFile(mContextText);
            return true;
        }

        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id) {

        switch (id) {
        case DIALOG_NEW_FOLDER:
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.dialog_new_folder, null);
            final EditText et = (EditText) view.findViewById(R.id.foldername);
            et.setText("");
            return new AlertDialog.Builder(this).setIcon(
                    android.R.drawable.ic_dialog_alert).setTitle(
                    R.string.create_new_folder).setView(view)
                    .setPositiveButton(android.R.string.ok,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    createNewFolder(et.getText().toString());
                                }

                            }).setNegativeButton(android.R.string.cancel,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
                                    // Cancel should not do anything.
                                }

                            }).create();

        case DIALOG_DELETE:
            return new AlertDialog.Builder(this).setTitle(
                    getString(R.string.really_delete, mContextText)).setIcon(
                    android.R.drawable.ic_dialog_alert).setPositiveButton(
                    android.R.string.ok, new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteFileOrFolder(mContextText);
                        }
                    }).setNegativeButton(android.R.string.cancel,
                    new OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // Cancel should not do anything.
                        }
                    }).create();

        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);

        switch (id) {
        case DIALOG_NEW_FOLDER:
            EditText et = (EditText) dialog.findViewById(R.id.foldername);
            et.setText("");
            break;

        case DIALOG_DELETE:
            ((AlertDialog) dialog).setTitle(getString(R.string.really_delete,
                    mContextText));
            break;

        case DIALOG_RENAME:
            et = (EditText) dialog.findViewById(R.id.foldername);
            et.setText(mContextText);
            TextView tv = (TextView) dialog.findViewById(R.id.foldernametext);
            tv.setText(R.string.file_name);
            ((AlertDialog) dialog).setIcon(mContextIcon);
            break;
        }
    }

    private void createNewFolder(String foldername) {
        if (!TextUtils.isEmpty(foldername)) {
            if (mFTPClient != null) {
                boolean createFolderInitiated = false;
                createFolderInitiated = mFTPClient.createFolder(foldername);
                if (false == createFolderInitiated) {
                    Toast.makeText(this, R.string.ftp_create_folder_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                String szStr = getResources().getString(R.string.ftp_create_new_folder, foldername);
                showBusy(getServerName(), szStr);
            }
        }
    }

    private void deleteFileOrFolder(String name) {
        if (mFTPClient != null) {
            boolean deleteInitiated = false;
            deleteInitiated = mFTPClient.delete(name);
            if (false == deleteInitiated) {
                Toast.makeText(this, R.string.ftp_error_deleting_file_folder, Toast.LENGTH_LONG).show();
                return;
            }
            String szStr = getResources().getString(R.string.ftp_delete_file_folder, name);
            showBusy(getServerName(), szStr);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mFTPClient != null) {
                if (mFTPClient.isConnectionActive()) {
                    if (doesParentFolderExist()) {
                        upOneLevel();
                        return true;
                    } else {
                        return mContext.onKeyDown(keyCode, event);
                    }
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    } /* onKeyDown */

    /**
     * This is called after the activity is finished.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
        case SUBACTIVITY_PICK_BT_DEVICE:
            if (resultCode == RESULT_OK && data != null) {
                /* Obtain the Server name and Address */
                String serverAddress = data.getStringExtra(BluetoothDevicePicker.ADDRESS);
                String serverName = data.getStringExtra(BluetoothDevicePicker.NAME);

                if (mFTPClient != null) {
                    mFTPClient.setAddress(serverAddress);
                    mFTPClient.setName(serverName);
                        mServerConnectButtonLayout.setVisibility(View.GONE);
                    if(mFTPClient.createSession() != true) {
                         String szStr = getResources().getString(R.string.ftp_connect_failed, mFTPClient.getName());
                         Toast.makeText(this, szStr, Toast.LENGTH_LONG).show();
                         updateServerStatus();
                    } else {
                         String szStr = getResources().getString(R.string.ftp_connect_device, mFTPClient.getName());
                         showBusy(mFTPClient.getName(), szStr);
                    }
                }
            }
            break;
        }
    } /* onActivityResult */

    private static final int SHOWBUSY = 1;
    private static final int SHOWBUSY_TIMEOUT = 60000;
    private Handler mTimeoutHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == SHOWBUSY) {
                timeoutToast();
            }
        }
    };

    private void timeoutToast() {
        Toast.makeText(this, R.string.error_ftp_connect_timeout, Toast.LENGTH_LONG).show();
        hideBusy();
    }

    public void showBusy(String title, String szStr) {
        mProgressDlg = ProgressDialog.show(this, title, szStr, true, false);

        Message msg = new Message();
        msg.what = SHOWBUSY;
        mTimeoutHandler.sendMessageDelayed(msg, SHOWBUSY_TIMEOUT);
    }

    public void hideBusy() {
        if(mProgressDlg != null) {
            mProgressDlg.dismiss();
            mProgressDlg = null;
        }
        mTimeoutHandler.removeMessages(SHOWBUSY);
    }

    /**
     * This is called to update the status information in the text and status.
     */
    public void handleServerSelect() {
        mDirectoryButtons.removeAllViews();
        currentDirectory = "/";

        if (mContext.isBluetoothEnabled()) {
            Intent intent = new Intent(getApplicationContext(), BluetoothDevicePicker.class);
            intent.setAction(BluetoothAppIntent.ACTION_SELECT_BLUETOOTH_DEVICE);
            intent.putExtra(BluetoothAppIntent.PROFILE, BluetoothAppIntent.PROFILE_FTP);

            intent.setData(Uri.parse("file://" + currentDirectory));

            try {
                startActivityForResult(intent, SUBACTIVITY_PICK_BT_DEVICE);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "No Activity for : " + BluetoothAppIntent.ACTION_SELECT_BLUETOOTH_DEVICE, e);
            }
        }
    }

    public void handleServerDisconnect() {
        /* Initiate the server Disconnect */
        mContext.initiateServerDisConnect();
        if (mFTPClient != null) {
            mFTPClient.closeSession();
        }

        updateServerStatus();
        refreshList();
    }

    /**
     * This is called to update the status information in the text and status.
     */
    private void updateServerStatus() {

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            mFolderEmpty.setText(R.string.this_folder_is_empty);

            boolean connectionActive = false;
            if (mFTPClient != null) {
                if (mFTPClient.isConnectionActive()) {
                    connectionActive = true;
                }
            }

            /* If FTP Connection is active */
            if (connectionActive) {
                mServerConnectButtonLayout.setVisibility(View.GONE);
                mFileListLayout.setVisibility(View.VISIBLE);
                mDirectoryButtons.setVisibility(View.VISIBLE);
                mServerConnectHelp.setVisibility(View.GONE);
            } else {
                /* Is Bluetooth enabled */
                if (mContext.isBluetoothEnabled()) {
                    mServerConnectHelp.setText(R.string.ServerDisconnectedHelpText);
                    mServerConnectHelp.setVisibility(View.GONE);
                    mServerConnectButtonLayout.setVisibility(View.VISIBLE);
                } else {
                    mServerConnectHelp.setVisibility(View.VISIBLE);
                    mServerConnectHelp.setText(R.string.BluetoothDisabledHelpText);
                }
                mFileListLayout.setVisibility(View.GONE);
                mDirectoryButtons.setVisibility(View.GONE);
            }
        } else {
            mFolderEmpty.setText(R.string.no_sdcard_text);
            mFileListLayout.setVisibility(View.VISIBLE);

            mServerConnectButtonLayout.setVisibility(View.GONE);
            mDirectoryButtons.setVisibility(View.GONE);
            mServerConnectHelp.setVisibility(View.GONE);
        }
    }

   public RemoteFile getRemoteFileObject(String filename) {

      if (mCurrentListFolder.size() > 0) {
         for (RemoteFile currentFile : mCurrentListFolder) {
            if (filename.equalsIgnoreCase(currentFile.getName())) {
               return currentFile;
            }
         }
      }
      return null;
   }

   public boolean doesParentFolderExist() {

      if (mCurrentListFolder.size() > 0) {
         for (RemoteFile currentFile : mCurrentListFolder) {
            if (currentFile.isParentFolder()) {
               return true;
            }
         }
      }
      return false;
   }

   private String getServerName() {

      if (mFTPClient != null) {
         if (mFTPClient.isConnectionActive()) {
            String string = mFTPClient.getName();
            if (!TextUtils.isEmpty(string)) {
               return string;
            }
            return "Bluetooth Device";
         }
      }
      return "";
   }

   private void downloadFile(String filename) {
      Log.i(TAG, "downloadFile : " + filename);
      if (mFTPClient != null) {
         if (mFTPClient.isConnectionActive()) {
            //mContext.initiateGetFile(filename);
            String localName = mContext.getFTPClientRecieveFolder() + filename;
            RemoteFile file = getRemoteFileObject(filename);
            if (file != null) {
               if (true != mFTPClient.getFile(localName, filename)) {
                  String szStr = getResources().getString(R.string.ftp_download_failed, filename, mFTPClient.getName());
                  Toast.makeText(this, szStr, Toast.LENGTH_LONG).show();
               }
            }
         }
      }
   }

   /**
    * @param isError true if error executing createSession, false otherwise
   */
   public void onCreateSessionComplete(boolean isError) {
      if (mContext.V) {
         Log.i(TAG, "onCreateSessionComplete : isError - " + isError);
      }
      mSessionCreated = !isError;
      hideBusy();
      if (mFTPClient != null) {
          if (isError == false) {
              mContext.onServerConnected();
              goHomeDir();
          } else {
              String szStr = getResources().getString(R.string.ftp_connect_failed, mFTPClient.getName());
              Toast.makeText(this, szStr, Toast.LENGTH_LONG).show();
          }
      }
      updateServerStatus();
      refreshDirectoryPanel();
   }

   /**
    * Notification that this session has been closed.  The client will
    * need to reconnect if future FTP operations are required.
    */
   public void onObexSessionClosed() {
      if (mContext.V) {
         Log.i(TAG, "onObexSessionClosed : ");
      }
      hideBusy();
      updateServerStatus();
      refreshDirectoryPanel();
      mContext.cleanupResource();
   }

   /**
    * @param folder name of folder to create
    * @param isError true if error executing createFolder, false otherwise
   */
   public void onCreateFolderComplete(String folder, boolean isError) {
      if (mContext.V) {
         Log.i(TAG, "onCreateFolderComplete : Folder: "+folder+ "isError : " + isError);
      }
      hideBusy();
      if (isError == false) {
          /* Create Folder done, initiate the ListFolders */
          refreshList();
      } else {
          Toast.makeText(this, R.string.ftp_create_folder_failed, Toast.LENGTH_LONG).show();
      }
   }

   /**
    * @param folder name of folder to change to
    * @param isError true if error executing changeFolder, false otherwise
   */
   public void onChangeFolderComplete(String folder, boolean isError) {
      if (mContext.V) {
         Log.i(TAG, "onChangeFolderComplete : Folder: "+folder+ "isError : " + isError);
      }
      hideBusy();
      if (isError == false) {
          /* Change Folder done, initiate the ListFolders */
          refreshList();
      } else {
          Toast.makeText(this, R.string.ftp_change_folder_failed, Toast.LENGTH_LONG).show();
      }
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
      if (mContext.V) {
         Log.i(TAG, "onListFolderComplete : isError : " + isError);
      }
      hideBusy();
      if(isError == false) {
         onListFolder(result);
      }
   }

   /**
    * @param localFilename Filename on local device
    * @param remoteFilename Filename on remote device
    * @param isError true if error executing getFile, false otherwise
   */
   public void onGetFileComplete(String localFilename, String remoteFilename, boolean isError) {
      if (mContext.V) {
         Log.i(TAG, "onGetFileComplete : local: "+localFilename+ "Remote: "+remoteFilename+ "isError : " + isError);
      }
      if (mFTPClient != null) {
         if(isError == false) {
             RemoteFile remoteFile = getRemoteFileObject(remoteFilename);
             if(remoteFile != null) {
                mFTPClient.getFileStarted(localFilename, remoteFile);
                mContext.getFileStarted();
             }
         } else {
             String szStr = getResources().getString(R.string.ftp_download_failed, remoteFilename, mFTPClient.getName());
             Toast.makeText(this, szStr, Toast.LENGTH_LONG).show();
         }
      }
   }

   /**
    * @param localFilename Filename on local device
    * @param remoteFilename Filename on remote device
    * @param isError true if error executing putFile, false otherwise
   */
   public void onPutFileComplete(String localFilename, String remoteFilename, boolean isError) {
      if (mContext.V) {
         Log.i(TAG, "onPutFileComplete : local: "+localFilename+ "Remote: "+remoteFilename+ "isError : " + isError);
      }
      if (mFTPClient != null) {
         if (isError == false) {
             mFTPClient.putFileStarted(localFilename);
             mContext.putFileStarted();
         } else {
             String szStr = getResources().getString(R.string.ftp_upload_failed, remoteFilename, mFTPClient.getName());
             Toast.makeText(this, szStr, Toast.LENGTH_LONG).show();
         }
      }
   }

   /**
    * @param name name of file/folder to delete
    * @param isError true if error executing delete, false otherwise
   */
   public void onDeleteComplete(String name, boolean isError) {
      if (mContext.V) {
         Log.i(TAG, "onDeleteComplete : name: " + name + " isError : " + isError);
      }
      hideBusy();
      boolean temp = false;
      if (isError == false) {
         // Delete was successful.
         refreshList();
         if (temp) {
            File file = new File (name);
            if (file.isDirectory()) {
               Toast.makeText(this, R.string.folder_deleted, Toast.LENGTH_SHORT).show();
            } else {
               Toast.makeText(this, R.string.file_deleted, Toast.LENGTH_SHORT).show();
            }
         } else {
            String szStr = getResources().getString(R.string.ftp_file_folder_deleted, name);
            Toast.makeText(this, szStr, Toast.LENGTH_SHORT).show();
         }
      } else {
         if (temp) {
            File file = new File (name);
            if (file.isDirectory()) {
               Toast.makeText(this, R.string.error_folder_not_empty, Toast.LENGTH_LONG).show();
            } else {
               Toast.makeText(this, R.string.error_deleting_file, Toast.LENGTH_LONG).show();
            }
         } else {
            String szStr = getResources().getString(R.string.ftp_error_deleting_file_folder, name);
            Toast.makeText(this, szStr, Toast.LENGTH_LONG).show();
         }
      }
   }
}