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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.quicinc.bluetooth.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.content.ActivityNotFoundException; //import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment; //import android.provider.Contacts.Intents;
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
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;

public class LocalFileManagerActivity extends ListActivity {
    private static final String TAG = "LocalFileManagerActivity";

    private static final int MENU_NEW_FOLDER = Menu.FIRST + 4;
    private static final int MENU_DELETE = Menu.FIRST + 5;
    private static final int MENU_RENAME = Menu.FIRST + 6;
    private static final int MENU_SEND = Menu.FIRST + 7;
    private static final int MENU_OPEN = Menu.FIRST + 8;

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

    /** SD card separate for sorting */
    List<IconifiedText> mListSdCard = new ArrayList<IconifiedText>();

    private File currentDirectory = new File("");

    private String mSdCardPath = "";

    private MimeTypes mMimeTypes;

    private String mContextText;
    private File mContextFile = new File("");
    private Drawable mContextIcon;

    /** How many steps one can make back using the back key. */
    private int mStepsBack;

    private LinearLayout mDirectoryButtons;
    private TextView mFolderEmpty;
    private static BluetoothBrowserActivity mContext;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        Log.v(TAG, "onCreate");

        setContentView(R.layout.local_filelist);
        Activity parent = getParent();
        if (parent != null && parent instanceof BluetoothBrowserActivity) {
            mContext = (BluetoothBrowserActivity) parent;
        } else {
            return;
        }
        mContext.setLocalManagerActivity(this);
        mDirectoryButtons = (LinearLayout) findViewById(R.id.local_directory_buttons);
        mFolderEmpty = (TextView) findViewById(R.id.local_empty);

        getListView().setOnCreateContextMenuListener(this);
        getListView().setEmptyView(mFolderEmpty);
        // registerForContextMenu(getListView());
        // Create map of extensions:
        getMimeTypes();

        getSdCardPath();

        Intent intent = getIntent();

        File browseto = new File("/");

        if (!TextUtils.isEmpty(mSdCardPath)) {
            browseto = new File(mSdCardPath);
            mFolderEmpty.setText(R.string.this_folder_is_empty);
        } else {
            mFolderEmpty.setText(R.string.no_sdcard_text);
            return;
        }

        // Set current directory and file based on intent data.
        File file = FileUtils.getFile(intent.getData());
        if (file != null) {
            File dir = FileUtils.getPathWithoutFilename(file);
            if (dir.isDirectory()) {
                browseto = dir;
            }
        }
        mStepsBack = 0;

        if (icicle != null) {
            browseto = new File(icicle.getString(BUNDLE_CURRENT_DIRECTORY));
            mContextFile = new File(icicle.getString(BUNDLE_CONTEXT_FILE));
            mContextText = icicle.getString(BUNDLE_CONTEXT_TEXT);

            mStepsBack = icicle.getInt(BUNDLE_STEPS_BACK);
        }

        browseTo(browseto);
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

        // remember file name
        outState.putString(BUNDLE_CURRENT_DIRECTORY, currentDirectory
                .getAbsolutePath());
        outState.putString(BUNDLE_CONTEXT_FILE, mContextFile.getAbsolutePath());
        outState.putString(BUNDLE_CONTEXT_TEXT, mContextText);
        outState.putInt(BUNDLE_STEPS_BACK, mStepsBack);
    }

    /* Routine that returns the currentDirectory for use by Bluetooth Browser */
    public String getCurrentDirectory()
    {
       return (currentDirectory.getAbsolutePath());
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
            throw new RuntimeException("PreselectedChannelsActivity: XmlPullParserException");
        } catch (IOException e) {
            Log.e(TAG, "PreselectedChannelsActivity: IOException", e);
            throw new RuntimeException("PreselectedChannelsActivity: IOException");
        }
    }

    /**
     * This function browses up one level according to the field:
     * currentDirectory
     */
    private void upOneLevel() {
        if (mStepsBack > 0) {
            mStepsBack--;
        }
        if (currentDirectory.getParent() != null)
            browseTo(currentDirectory.getParentFile());
    }

    /**
     * Jump to some location by clicking on a directory button.
     *
     * This resets the counter for "back" actions.
     *
     * @param aDirectory
     */
    private void jumpTo(final File aDirectory) {
        mStepsBack = 0;
        browseTo(aDirectory);
    }

    /**
     * Browse to some location by clicking on a list item.
     *
     * @param aDirectory
     */
    private void browseTo(final File aDirectory) {
        // setTitle(aDirectory.getAbsolutePath());

        if (aDirectory.isDirectory()) {
            File previousDirectory = currentDirectory;
            currentDirectory = aDirectory;
            refreshList();
            selectInList(previousDirectory);
            refreshDirectoryPanel();
        }
    }

    private void openFile(File aFile) {
        if (!aFile.exists()) {
            Toast.makeText(this, R.string.error_file_does_not_exists,
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(android.content.Intent.ACTION_VIEW);

        Uri data = FileUtils.getUri(aFile);
        String type = mMimeTypes.getMimeType(aFile.getName());
        intent.setDataAndType(data, type);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.application_not_available,
                    Toast.LENGTH_SHORT).show();
        }
        ;
    }

    private void refreshList() {

        File[] files = currentDirectory.listFiles();

        directoryEntries.clear();
        mListDir.clear();
        mListFile.clear();
        mListSdCard.clear();

        // Add the "." == "current directory"
        /*
         * directoryEntries.add(new IconifiedText(
         * getString(R.string.current_dir),
         * getResources().getDrawable(R.drawable.ic_launcher_folder)));
         */
        // and the ".." == 'Up one level'
        /*
         * if(currentDirectory.getParent() != null) directoryEntries.add(new
         * IconifiedText( getString(R.string.up_one_level),
         * getResources().getDrawable(R.drawable.ic_launcher_folder_open)));
         */

        Drawable currentIcon = null;
        for (File currentFile : files) {
            /*
             * if (currentFile.isHidden()) { continue; }
             */
            if (currentFile.isDirectory()) {
                if (currentFile.getAbsolutePath().equals(mSdCardPath)) {
                    currentIcon = getResources().getDrawable(
                            R.drawable.icon_sdcard);

                    mListSdCard.add(new IconifiedText(currentFile.getName(),
                            currentIcon));
                } else {
                    currentIcon = getResources().getDrawable(
                            R.drawable.ic_launcher_folder);

                    mListDir.add(new IconifiedText(currentFile.getName(),
                            currentIcon));
                }
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
        // Collections.sort(mListSdCard);
        Collections.sort(mListDir);
        Collections.sort(mListFile);

        addAllElements(directoryEntries, mListSdCard);
        addAllElements(directoryEntries, mListDir);
        addAllElements(directoryEntries, mListFile);

        IconifiedTextListAdapter itla = new IconifiedTextListAdapter(this);
        itla.setListItems(directoryEntries);
        setListAdapter(itla);

    }

    private void selectInList(File selectFile) {
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
        String[] parts = currentDirectory.getAbsolutePath().split("/");

        mDirectoryButtons.removeAllViews();

        int WRAP_CONTENT = LinearLayout.LayoutParams.WRAP_CONTENT;

        // Add home button separately
        ImageButton ib = new ImageButton(this);
        ib.setImageResource(R.drawable.icon_sdcard_small);
        ib.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT,
                WRAP_CONTENT));
        ib.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                jumpTo(new File(mSdCardPath));
            }
        });
        mDirectoryButtons.addView(ib);

        // Add other buttons

        String dir = "";

        for (int i = 1; i < parts.length; i++) {
            dir += "/" + parts[i];
            if (dir.equals(mSdCardPath)) {
                // nothing
            } else {
                Button b = new Button(this);
                b.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT,
                        WRAP_CONTENT));
                b.setText(parts[i]);
                b.setTag(dir);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View view) {
                        String dir = (String) view.getTag();
                        jumpTo(new File(dir));
                    }
                });
                mDirectoryButtons.addView(b);
            }
        }

        checkButtonLayout();
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
            //
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

        String selectedFileString = directoryEntries.get(position).getText();
        if (selectedFileString.equals(getString(R.string.up_one_level))) {
            upOneLevel();
        } else {
            String curdir = currentDirectory.getAbsolutePath();
            String file = directoryEntries.get(position).getText();
            File clickedFile = FileUtils.getFile(curdir, file);
            if (clickedFile != null) {
                if (clickedFile.isDirectory()) {
                    // If we click on folders, we can return later by the "back"
                    // key.
                    mStepsBack++;
                    browseTo(clickedFile);
                } else {
                    // openContextMenu(getListView());
                }
            }
        }
    }

    /**
     * Return the Drawable that is associated with a specific mime type for the
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

    private void getSdCardPath() {
        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            mSdCardPath = Environment.getExternalStorageDirectory()
                    .getAbsolutePath();
        } else {
            mSdCardPath = "";
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        menu.add(0, MENU_NEW_FOLDER, 0, R.string.menu_new_folder).setIcon(
                android.R.drawable.ic_menu_add).setShortcut('0', 'f');

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Intent intent;
        switch (item.getItemId()) {
        case MENU_NEW_FOLDER:
            showDialog(DIALOG_NEW_FOLDER);
            return true;

        }
        return super.onOptionsItemSelected(item);

    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
            ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }
        if (info == null) {
            return;
        }
        IconifiedText it = directoryEntries.get(info.position);
        menu.setHeaderTitle(it.getText());
        menu.setHeaderIcon(it.getIcon());
        File file = FileUtils.getFile(currentDirectory, it.getText());

        if (!file.isDirectory()) {
            menu.add(0, MENU_OPEN, 0, R.string.menu_open);
            if (mContext.isServerConnected()) {
                String sendString = getString(R.string.menu_send, mContext.getFTPServerName());
                menu.add(0, MENU_SEND, 0, sendString);
            }
        }
        menu.add(0, MENU_RENAME, 0, R.string.menu_rename);
        menu.add(0, MENU_DELETE, 0, R.string.menu_delete);
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
        mContextFile = FileUtils.getFile(currentDirectory, ic.getText());

        switch (item.getItemId()) {
        case MENU_OPEN:
            openFile(mContextFile);
            return true;

        case MENU_DELETE:
            showDialog(DIALOG_DELETE);
            return true;

        case MENU_RENAME:
            showDialog(DIALOG_RENAME);
            return true;

        case MENU_SEND:
            sendFile(mContextFile);
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
                            deleteFileOrFolder(mContextFile);
                        }

                    }).setNegativeButton(android.R.string.cancel,
                    new OnClickListener() {

                        public void onClick(DialogInterface dialog, int which) {
                            // Cancel should not do anything.
                        }

                    }).create();

        case DIALOG_RENAME:
            inflater = LayoutInflater.from(this);
            view = inflater.inflate(R.layout.dialog_new_folder, null);
            final EditText et2 = (EditText) view.findViewById(R.id.foldername);
            return new AlertDialog.Builder(this).setTitle(R.string.menu_rename)
                    .setView(view).setPositiveButton(android.R.string.ok,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {

                                    renameFileOrFolder(mContextFile, et2
                                            .getText().toString());
                                }

                            }).setNegativeButton(android.R.string.cancel,
                            new OnClickListener() {

                                public void onClick(DialogInterface dialog,
                                        int which) {
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
            if (mContextFile.isDirectory()) {
                tv.setText(R.string.file_name);
            } else {
                tv.setText(R.string.file_name);
            }
            ((AlertDialog) dialog).setIcon(mContextIcon);
            break;
        }
    }

    private void createNewFolder(String foldername) {
        if (!TextUtils.isEmpty(foldername)) {
            File file = FileUtils.getFile(currentDirectory, foldername);
            if (file.mkdirs()) {
                /* Change into new directory: */
                browseTo(file);
            } else {
                Toast.makeText(this, R.string.error_creating_new_folder,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * @param file
     */
    private void deleteFileOrFolder(File file) {
        int toast = 0;
        if (file.delete()) {
            /* Delete was successful. */
            refreshList();
            if (file.isDirectory()) {
                toast = R.string.folder_deleted;
            } else {
                toast = R.string.file_deleted;
            }
        } else {
            if (file.isDirectory()) {
                if (file.list().length > 0) {
                    toast = R.string.error_folder_not_empty;
                } else {
                    toast = R.string.error_deleting_folder;
                }
            } else {
                toast = R.string.error_deleting_file;
            }
        }
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    /**
     * @param file
     * @param newFileName
     */
    private void renameFileOrFolder(File file, String newFileName) {

        File newFile = FileUtils.getFile(currentDirectory, newFileName);
        rename(file, newFile);
    }

    /**
     * @param oldFile
     * @param newFile
     */
    private void rename(File oldFile, File newFile) {
        int toast = 0;
        if (oldFile.renameTo(newFile)) {
            /* Rename was successful. */
            refreshList();
            if (newFile.isDirectory()) {
                toast = R.string.folder_renamed;
            } else {
                toast = R.string.file_renamed;
            }
        } else {
            if (oldFile.isDirectory()) {
                toast = R.string.error_renaming_folder;
            } else {
                toast = R.string.error_renaming_file;
            }
        }
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show();
    }

    private void sendFile(File file) {
      mContext.initiateSendFile(file);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mStepsBack > 0) {
                upOneLevel();
                return true;
            } else if (mContext.isServerConnected()) {
                return mContext.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * This is called after the file manager finished.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

}