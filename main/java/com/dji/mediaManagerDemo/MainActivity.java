package com.dji.mediaManagerDemo;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import dji.common.airlink.PhysicalSource;
import dji.common.camera.SettingsDefinitions;
import dji.common.camera.StorageState;
import dji.common.error.DJICameraError;
import dji.common.error.DJIError;
import dji.common.product.Model;
import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.media.DownloadListener;
import dji.sdk.media.FetchMediaTask;
import dji.sdk.media.FetchMediaTaskContent;
import dji.sdk.media.FetchMediaTaskScheduler;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaManager;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = MainActivity.class.getName();

    private Button mBackBtn, mDeleteBtn, mReloadBtn, mDownloadBtn, mStatusBtn;
    private Button mPlayBtn, mResumeBtn, mPauseBtn, mStopBtn, mMoveToBtn;
    private RecyclerView listView;
    private FileListAdapter mListAdapter;
    private List<MediaFile> mediaFileList = new ArrayList<MediaFile>();
    private MediaManager mMediaManager;
    private MediaManager.FileListState currentFileListState = MediaManager.FileListState.UNKNOWN;
    private FetchMediaTaskScheduler scheduler;
    private ProgressDialog mLoadingDialog;
    private ProgressDialog mDownloadDialog;
    private SlidingDrawer mPushDrawerSd;
    File destDir = new File(Environment.getExternalStorageDirectory().getPath() + "/风机巡检媒体库/");
    private int currentProgress = -1;
    private ImageView mDisplayImageView;
    private int lastClickViewIndex =-1;
    private View lastClickView;
    private TextView mPushTv;
    private SettingsDefinitions.StorageLocation storageLocation;
    private EditText bladeIDEt; //用SIGNAL_RENAME
    private EditText bladePlanEt;
    String bladeIDString = "";
    String bladePlanString = "";
    Integer bitmapIndexGlobal = 0; // 用来防呆 和 lastClickViewIndex
    // SIGNAL_READY22
    private Button mRenameBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.e("页面M oncreate", "准备initUI");
        initUI();
        // SIGNAL_READY17
        showToastLog("onResume","操作请注意：请同时点击右侧缩略图和左侧文本!!!", false);
        DemoApplication.getAircraftInstance().getCamera().setStorageStateCallBack(new StorageState.Callback() {
            @Override
            public void onUpdate(@NonNull @NotNull StorageState storageState) {
                if(storageState.isInserted()) {
                    storageLocation = SettingsDefinitions.StorageLocation.SDCARD;
                    DemoApplication.getAircraftInstance().getCamera().setStorageLocation(SettingsDefinitions.StorageLocation.SDCARD, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                Log.e("页面M oncreate", "设置为SD卡");
                            }
                            else {
                                //Log.e("页面M oncreate", "SD 错误码:" + djiError.getDescription());
                            }
                        }
                    });
                } else {
                    storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE;
                    DemoApplication.getAircraftInstance().getCamera().setStorageLocation(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                Log.e("页面M oncreate", "内部无储存位置，请插入SD卡");
                            }
                            else {
                                //Log.e("页面M oncreate", "内部储存错误码:" + djiError.getDescription());
                            }
                        }
                    });
                }
            }
        });
        Log.e("页面M oncreate", "设置储存位置完成");
    }

    @Override
    protected void onResume() {
        super.onResume();
        // SIGNAL_READY6
        //showToastLog("onResume","操作请注意：请同时点击右侧缩略图和左侧文本!!!", true);
        initMediaManager();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        lastClickView = null;
        if (mMediaManager != null) {
            mMediaManager.stop(null);
            mMediaManager.removeFileListStateCallback(this.updateFileListStateListener);
            mMediaManager.removeMediaUpdatedVideoPlaybackStateListener(updatedVideoPlaybackStateListener);
            mMediaManager.exitMediaDownloading();
            if (scheduler!=null) {
                scheduler.removeAllTasks();
            }
        }

        if (DemoApplication.getCameraInstance() != null) {
            if (isMavicAir2() || isAir2S() || isM300()) {
                DemoApplication.getCameraInstance().exitPlayback(djiError -> {
                    if (djiError != null) {
                        DemoApplication.getCameraInstance().setFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE, djiError1 -> {
                            if (djiError1 != null) {
                                setResultToToast("Set PHOTO_SINGLE Mode Failed. " + djiError1.getDescription());
                            }
                        });
                    }
                });
            } else {
                DemoApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO, djiError -> {
                    if (djiError != null) {
                        setResultToToast("Set SHOOT_PHOTO Mode Failed. " + djiError.getDescription());
                    }
                });
            }
        }

        if (mediaFileList != null) {
            mediaFileList.clear();
        }
        super.onDestroy();
    }

    // 用SIGNAL_RENAME表示
    private void showToastLog(final String tag, final String description, boolean isRunToast) {
        Log.e("页面M: " + tag, description);
        if (isRunToast) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(MainActivity.this, description, Toast.LENGTH_SHORT).show();  // 显示2s  long时3.5s
                }
            });
        }
    }

    void initUI() {

        //Init RecyclerView
        listView = (RecyclerView) findViewById(R.id.filelistView);
        LinearLayoutManager layoutManager = new LinearLayoutManager(MainActivity.this, RecyclerView.VERTICAL,false);
        listView.setLayoutManager(layoutManager);

        //Init FileListAdapter
        mListAdapter = new FileListAdapter();
        listView.setAdapter(mListAdapter);

        //Init Loading Dialog
        mLoadingDialog = new ProgressDialog(MainActivity.this);
        mLoadingDialog.setMessage("请等待");
        // SIGNAL_READY14 测试
        mLoadingDialog.setCanceledOnTouchOutside(true);
        mLoadingDialog.setCancelable(true);

        //Init Download Dialog
        mDownloadDialog = new ProgressDialog(MainActivity.this);
        mDownloadDialog.setTitle("下载文件中");
        mDownloadDialog.setIcon(android.R.drawable.ic_dialog_info);
        mDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mDownloadDialog.setCanceledOnTouchOutside(false);
        mDownloadDialog.setCancelable(false);
        mDownloadDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                if (mMediaManager != null) {
                    mMediaManager.exitMediaDownloading();
                }
            }
        });

        mPushDrawerSd = (SlidingDrawer)findViewById(R.id.pointing_drawer_sd);
        mPushTv = (TextView)findViewById(R.id.pointing_push_tv);
        mBackBtn = (Button) findViewById(R.id.back_btn);
        mDeleteBtn = (Button) findViewById(R.id.delete_btn);
        mDownloadBtn = (Button) findViewById(R.id.download_btn);
        mReloadBtn = (Button) findViewById(R.id.reload_btn);
        mStatusBtn = (Button) findViewById(R.id.status_btn);
        mPlayBtn = (Button) findViewById(R.id.play_btn);
        mResumeBtn = (Button) findViewById(R.id.resume_btn);
        mPauseBtn = (Button) findViewById(R.id.pause_btn);
        mStopBtn = (Button) findViewById(R.id.stop_btn);
        mMoveToBtn = (Button) findViewById(R.id.moveTo_btn);
        mDisplayImageView = (ImageView) findViewById(R.id.imageView);
        mDisplayImageView.setVisibility(View.VISIBLE);

        // SIGNAL_READY22 测试
//        mRenameBtn = (Button) findViewById(R.id.rename_btn);
//        mRenameBtn.setOnClickListener(this);

        mBackBtn.setOnClickListener(this);
        mDeleteBtn.setOnClickListener(this);
        mDownloadBtn.setOnClickListener(this);
        mReloadBtn.setOnClickListener(this);
        mStatusBtn.setOnClickListener(this);
        mPlayBtn.setOnClickListener(this);
        mResumeBtn.setOnClickListener(this);
        mPauseBtn.setOnClickListener(this);
        mStopBtn.setOnClickListener(this);
        mMoveToBtn.setOnClickListener(this);
        //SIGNAL_RENAME
        initRenameEditText();

    }

    /** RENAME_SIGNAL
     * 初始化两个重命名规则的editText
     */
    private void initRenameEditText() {
        //用SIGNAL_RENAME
        // TODO addTextChangedListener  文本改变监视器什么作用？
        bladeIDEt = (EditText) findViewById(R.id.rename_blade_id_et);
        bladeIDEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                bladeIDString = s.toString();
            }
        });

        bladePlanEt = (EditText) findViewById(R.id.rename_blade_plan_et);
        bladePlanEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                bladePlanString = s.toString();
            }
        });
    }

    /**
     * 偶尔会出现堵转问题，堵在UI线程中 需要清理SD卡才行
     */
    private void showProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (mLoadingDialog != null) {
                    Log.e("页面M hideProgressDialog","显示加载列表");
                    mLoadingDialog.show();
                }
            }
        });
    }

    private void hideProgressDialog() {
        runOnUiThread(new Runnable() {
            public void run() {
                if (null != mLoadingDialog && mLoadingDialog.isShowing()) {
                    Log.e("页面M hideProgressDialog","隐藏加载列表");
                    mLoadingDialog.dismiss();
                }
            }
        });
    }

    private void ShowDownloadProgressDialog() {
        if (mDownloadDialog != null) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.incrementProgressBy(-mDownloadDialog.getProgress());
                    mDownloadDialog.show();
                }
            });
        }
    }

    private void HideDownloadProgressDialog() {
        if (null != mDownloadDialog && mDownloadDialog.isShowing()) {
            runOnUiThread(new Runnable() {
                public void run() {
                    mDownloadDialog.dismiss();
                }
            });
        }
    }

    /**
     * 不如用我的showToastLog
     * @param result
     */
    private void setResultToToast(final String result) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setResultToText(final String string) {
        if (mPushTv == null) {
            setResultToToast("信息没有加载好，请稍后...");
        }
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPushTv.setText(string);
            }
        });
    }

    private void initMediaManager() {
        Log.e("页面M initMediaManager", "进入initMediaManager");
        if (DemoApplication.getProductInstance() == null) {
            mediaFileList.clear();
            mListAdapter.notifyDataSetChanged();
            DJILog.e(TAG, "Product disconnected");
            return;
        } else {
            Log.e("页面M initMediaManager", "实例存在");
            if (null != DemoApplication.getCameraInstance() && DemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                Log.e("页面M initMediaManager", "创建mMediaManager");
                mMediaManager = DemoApplication.getCameraInstance().getMediaManager();
                if (null != mMediaManager) {
                    Log.e("页面M initMediaManager", "创建mMediaManager成功");
                    mMediaManager.addUpdateFileListStateListener(this.updateFileListStateListener);
                    mMediaManager.addMediaUpdatedVideoPlaybackStateListener(this.updatedVideoPlaybackStateListener);
                    if (isMavicAir2() || isAir2S() || isM300()) {
                        DemoApplication.getCameraInstance().enterPlayback(djiError -> {
                            // TODO 这里如果出bug，会导致耗时很长一直卡在upload界面
                            // 问题是getFileList执行了 show才执行

                            if (djiError == null) {
                                DJILog.e(TAG, "Set cameraMode success");
                                Log.e("页面M initMediaManager111", "Set cameraMode success");
                                showProgressDialog();
                                // SIGNAL_READY14 加入等到，等待进入播放模式再刷新列表
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                                getFileList();
                            } else {
                                Log.e("页面M initMediaManager222", "Set cameraMode failed");
                                setResultToToast("Set cameraMode failed");
                            }
                        });
                    } else {
                        DemoApplication.getCameraInstance().setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD, error -> {
                            if (error == null) {
                                DJILog.e(TAG, "Set cameraMode success");
                                Log.e("页面M initMediaManager333", "Set cameraMode success");
                                showProgressDialog();
                                getFileList();
                            } else {
                                setResultToToast("Set cameraMode failed");
                            }
                        });
                    }

                    if (mMediaManager.isVideoPlaybackSupported()) {
                        DJILog.e(TAG, "Camera support video playback!");
                    } else {
                        setResultToToast("Camera does not support video playback!");
                    }
                    scheduler = mMediaManager.getScheduler();
                }

            } else if (null != DemoApplication.getCameraInstance()
                    && !DemoApplication.getCameraInstance().isMediaDownloadModeSupported()) {
                Log.e("initMediaManager", "Media Download Mode not Supported");
                setResultToToast("Media Download Mode not Supported");
            }
        }
        return;
    }

    private void getFileList() {
        Log.e("页面M getFileList","进入");
        mMediaManager = DemoApplication.getCameraInstance().getMediaManager();
        if (mMediaManager != null) {
            if ((currentFileListState == MediaManager.FileListState.SYNCING) || (currentFileListState == MediaManager.FileListState.DELETING)){
                Log.e("页面M getFileList","Media Manager is busy.");
                showToastLog("getFileList", "正在同步状态，请缓慢操作...",true);
            } else{
                Log.e("页面M getFileList","状态正确，准备刷新文件列表");
                mMediaManager.refreshFileListOfStorageLocation(storageLocation, djiError -> {
                    if (null == djiError) {
                        Log.e("页面M getFileList","进入刷新文件储存列表");
                        hideProgressDialog();

                        //Reset data
                        if (currentFileListState != MediaManager.FileListState.INCOMPLETE) {
                            mediaFileList.clear();
                            lastClickViewIndex = -1;
                            lastClickView = null;
                        }

                        List<MediaFile> tempList;
                        if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) {
                            tempList = mMediaManager.getSDCardFileListSnapshot();
                        } else {
                            tempList = mMediaManager.getInternalStorageFileListSnapshot();
                        }
                        if (tempList != null) {
                            mediaFileList.addAll(tempList);
                        }
                        if (mediaFileList != null) {
                            Collections.sort(mediaFileList, (lhs, rhs) -> {
                                if (lhs.getTimeCreated() < rhs.getTimeCreated()) {
                                    return 1;
                                } else if (lhs.getTimeCreated() > rhs.getTimeCreated()) {
                                    return -1;
                                }
                                return 0;
                            });
                        }
                        scheduler.resume(error -> {
                            if (error == null) {
                                getThumbnails();
                            }
                        });
                    } else {
                        hideProgressDialog();
                        Log.e("页面M getFileList","文件储存列表刷新失败:" + djiError.getDescription());
                        setResultToToast("Get Media File List Failed:" + djiError.getDescription());
                    }
                });
                Log.e("页面M getFileList","刷新文件列表后面SIGANL");
            }
            return;
        }
        else {
            Log.e("页面M getFileList", "mMediaManager为空，直接跳过了");
        }
    }

    private void getThumbnails() {
        if (mediaFileList.size() <= 0) {
            setResultToToast("当前列表没有文件");
            return;
        }
        for (int i = 0; i < mediaFileList.size(); i++) {
            getThumbnailByIndex(i);
        }
    }

    private FetchMediaTask.Callback taskCallback = new FetchMediaTask.Callback() {
        @Override
        public void onUpdate(MediaFile file, FetchMediaTaskContent option, DJIError error) {
            if (null == error) {
                if (option == FetchMediaTaskContent.PREVIEW) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mListAdapter.notifyDataSetChanged();
                        }
                    });
                }
                if (option == FetchMediaTaskContent.THUMBNAIL) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mListAdapter.notifyDataSetChanged();
                        }
                    });
                }
            } else {
                DJILog.e(TAG, "Fetch Media Task Failed" + error.getDescription());
            }
        }
    };

    private void getThumbnailByIndex(final int index) {
        FetchMediaTask task = new FetchMediaTask(mediaFileList.get(index), FetchMediaTaskContent.THUMBNAIL, taskCallback);
        scheduler.moveTaskToEnd(task);
    }

    private static class ItemHolder extends RecyclerView.ViewHolder {
        ImageView thumbnail_img;
        TextView file_name;
        TextView file_type;
        TextView file_size;
        TextView file_time;

        public ItemHolder(View itemView) {
            super(itemView);
            this.thumbnail_img = (ImageView) itemView.findViewById(R.id.filethumbnail);
            this.file_name = (TextView) itemView.findViewById(R.id.filename);
            this.file_type = (TextView) itemView.findViewById(R.id.filetype);
            this.file_size = (TextView) itemView.findViewById(R.id.fileSize);
            this.file_time = (TextView) itemView.findViewById(R.id.filetime);
        }
    }

    private class FileListAdapter extends RecyclerView.Adapter<ItemHolder> {
        @Override
        public int getItemCount() {
            if (mediaFileList != null) {
                return mediaFileList.size();
            }
            return 0;
        }

        @Override
        public ItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.media_info_item, parent, false);
            return new ItemHolder(view);
        }

        @Override
        public void onBindViewHolder(ItemHolder mItemHolder, final int index) {
            //Log.e("页面M onBindViewHolder", "选择的index:" + index);

            //用SIGNAL_RENAME SIGNAL_READY6 发现这是个bug
            //bitmapIndexGlobal = mItemHolder.getAdapterPosition();

            final MediaFile mediaFile = mediaFileList.get(index);
            if (mediaFile != null) {
                if (mediaFile.getMediaType() != MediaFile.MediaType.MOV && mediaFile.getMediaType() != MediaFile.MediaType.MP4) {
                    mItemHolder.file_time.setVisibility(View.GONE);
                } else {
                    mItemHolder.file_time.setVisibility(View.VISIBLE);
                    mItemHolder.file_time.setText(mediaFile.getDurationInSeconds() + " s");
                }
                mItemHolder.file_name.setText(mediaFile.getFileName());
                mItemHolder.file_type.setText(mediaFile.getMediaType().name());
                mItemHolder.file_size.setText(mediaFile.getFileSize() + " Bytes");
                mItemHolder.thumbnail_img.setImageBitmap(mediaFile.getThumbnail());
                mItemHolder.thumbnail_img.setOnClickListener(ImgOnClickListener);
                mItemHolder.thumbnail_img.setTag(mediaFile);
                mItemHolder.itemView.setTag(index);

                if (lastClickViewIndex == index) {
                    Log.e("页面M onBindViewHolder", "选中index = lastClickViewIndex");
                    mItemHolder.itemView.setSelected(true);
                } else {
                    //Log.e("页面M onBindViewHolder", "lastClickViewIndex:" + lastClickViewIndex);
                    mItemHolder.itemView.setSelected(false);
                }
                mItemHolder.itemView.setOnClickListener(itemViewOnClickListener);

            }
        }
    }

    private View.OnClickListener itemViewOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            lastClickViewIndex = (int) (v.getTag());
            Log.e("页面M 文本点击事件", "关注！：点击文本的index值:" + lastClickViewIndex);

            if (lastClickView != null && lastClickView != v) {
                lastClickView.setSelected(false);
            }
            v.setSelected(true);
            lastClickView = v;
        }
    };

    private View.OnClickListener ImgOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            MediaFile selectedMedia = (MediaFile) v.getTag();
            if (selectedMedia != null && mMediaManager != null) {
                addMediaTask(selectedMedia);
            }
        }
    };

    private void addMediaTask(final MediaFile mediaFile) {
        final FetchMediaTaskScheduler scheduler = mMediaManager.getScheduler();
        final FetchMediaTask task =
                new FetchMediaTask(mediaFile, FetchMediaTaskContent.PREVIEW, new FetchMediaTask.Callback() {
                    @Override
                    public void onUpdate(final MediaFile mediaFile, FetchMediaTaskContent fetchMediaTaskContent, DJIError error) {
                        if (null == error) {
                            if (mediaFile.getPreview() != null) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        final Bitmap previewBitmap = mediaFile.getPreview();
                                        mDisplayImageView.setVisibility(View.VISIBLE);
                                        mDisplayImageView.setImageBitmap(previewBitmap);
                                    }
                                });
                            } else {
                                setResultToToast("null bitmap!");
                            }
                        } else {
                            setResultToToast("fetch preview image failed: " + error.getDescription());
                        }
                    }
                });

        scheduler.resume(error -> {
            if (error == null) {
                scheduler.moveTaskToNext(task);
            } else {
                setResultToToast("resume scheduler failed: " + error.getDescription());
            }
        });
    }

    //Listeners
    private MediaManager.FileListStateListener updateFileListStateListener = state -> currentFileListState = state;

    private MediaManager.VideoPlaybackStateListener updatedVideoPlaybackStateListener =
            new MediaManager.VideoPlaybackStateListener() {
                @Override
                public void onUpdate(MediaManager.VideoPlaybackState videoPlaybackState) {
                    updateStatusTextView(videoPlaybackState);
                }
            };

    /**
     *
     * @param videoPlaybackState
     */
    private void updateStatusTextView(MediaManager.VideoPlaybackState videoPlaybackState) {
        final StringBuffer pushInfo = new StringBuffer();

        addLineToSB(pushInfo, "视频播放状态栏(播放后查看)", null);
        if (videoPlaybackState != null) {
            if (videoPlaybackState.getPlayingMediaFile() != null) {
                addLineToSB(pushInfo, "视频内存序号", videoPlaybackState.getPlayingMediaFile().getIndex());
                addLineToSB(pushInfo, "视频大小(bytes)", videoPlaybackState.getPlayingMediaFile().getFileSize());
                addLineToSB(pushInfo,
                        "视频时长(s)",
                        videoPlaybackState.getPlayingMediaFile().getDurationInSeconds());
                addLineToSB(pushInfo, "视频创建日期", videoPlaybackState.getPlayingMediaFile().getDateCreated());
                addLineToSB(pushInfo,
                        "视频朝向",
                        videoPlaybackState.getPlayingMediaFile().getVideoOrientation());
            } else {
                addLineToSB(pushInfo, "视频序号", "None");
            }
            addLineToSB(pushInfo, "视频当前播放位置", videoPlaybackState.getPlayingPosition());
            addLineToSB(pushInfo, "视频当前状态", videoPlaybackState.getPlaybackStatus());
            addLineToSB(pushInfo, "视频完整度", videoPlaybackState.getCachedPercentage());
            addLineToSB(pushInfo, "视频暂存位置", videoPlaybackState.getCachedPosition());
            pushInfo.append("\n");
            setResultToText(pushInfo.toString());
        }
    }

    private void addLineToSB(StringBuffer sb, String name, Object value) {
        if (sb == null) return;
        sb.
                append((name == null || "".equals(name)) ? "" : name + ": ").
                append(value == null ? "" : value + "").
                append("\n");
    }

    private void downloadFileByIndex(final int index){
        if (index < 0 || index >= mediaFileList.size()) {
            // 索引值无效，不执行任何操作
            Log.e("页面M 下载文件函数","负数索引");
            setResultToToast("请点击图片右侧的文字部分！");
            return;
        }
        if ((mediaFileList.get(index).getMediaType() == MediaFile.MediaType.PANORAMA)
                || (mediaFileList.get(index).getMediaType() == MediaFile.MediaType.SHALLOW_FOCUS)) {
            return;
        }
        if (bladePlanString == null || bladeIDString == null) {
            showToastLog("下载函数","请在右侧输入想要保存的名字再下载!", true);
            return;
        }
        showToastLog("下载函数","文件位置"+destDir.toString(), false);
        // TODO 叶片编号+叶片位置 考虑用intent传数据或者静态变量拿到传过来的数据 SIGNAL_READY
        // SIGNAL_READY22 暂时注释 测试名字
        //String fileName = bladeIDString + "-" + bladePlanString;
        //showToastLog("下载函数","将要保存的文件名字是："+ fileName, true);
        // mediaFileList.get(index).fetchFileData(destDir, fileName, new DownloadListener<String>()
        mediaFileList.get(index).fetchFileData(destDir, null, new DownloadListener<String>() {
            @Override
            public void onFailure(DJIError error) {
                HideDownloadProgressDialog();
                setResultToToast("文件下载失败" + error.getDescription());
                currentProgress = -1;
            }

            @Override
            public void onProgress(long total, long current) {
            }

            @Override
            public void onRateUpdate(long total, long current, long persize) {
                int tmpProgress = (int) (1.0 * current / total * 100);
                if (tmpProgress != currentProgress) {
                    mDownloadDialog.setProgress(tmpProgress);
                    currentProgress = tmpProgress;
                }
            }

            @Override
            public void onRealtimeDataUpdate(byte[] bytes, long l, boolean b) {

            }

            @Override
            public void onStart() {
                currentProgress = -1;
                ShowDownloadProgressDialog();
            }

            @Override
            public void onSuccess(String filePath) {
                HideDownloadProgressDialog();
                setResultToToast("下载成功,路径为" + ":" + filePath);
                currentProgress = -1;
                // SIGNAL_READY8
                getFileList();
            }
        });
    }

    private void deleteFileByIndex(final int index) {
        if (index < 0 || index >= mediaFileList.size()) {
            // 索引值无效，不执行删除操作
            Log.e("页面M 删除文件函数","负数索引，mediaFileList大小："+ mediaFileList.size());
            setResultToToast("请点击图片右侧文字部分！");
            return;
        }

        ArrayList<MediaFile> fileToDelete = new ArrayList<MediaFile>();
        if (mediaFileList.size() > index) {
            fileToDelete.add(mediaFileList.get(index));
            mMediaManager.deleteFiles(fileToDelete, new CommonCallbacks.CompletionCallbackWithTwoParam<List<MediaFile>, DJICameraError>() {
                @Override
                public void onSuccess(List<MediaFile> x, DJICameraError y) {
                    DJILog.e(TAG, "页面M 删除文件："+index);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            MediaFile file = mediaFileList.remove(index);

                            //Reset select view
                            lastClickViewIndex = -1;
                            lastClickView = null;

                            //Update recyclerView
                            mListAdapter.notifyItemRemoved(index);
                            try { // SIGNAL_READY22 测试2
                                Thread.sleep(200);
                                // update SIGNAL_READY8
                                getFileList();
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
//                    try { // SIGNAL_READY22
//                        Thread.sleep(500);
//                        // update SIGNAL_READY8
//                        getFileList();
//                    } catch (InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
                }

                @Override
                public void onFailure(DJIError error) {
                    setResultToToast("Delete file failed");
                }
            });
        }
    }

    private void playVideo() {
        if (lastClickViewIndex == -1) {
            // 索引值无效
            Log.e("页面M playVideo","负数索引，mediaFileList大小："+ mediaFileList.size());
            setResultToToast("请点击视频，再点击右侧文字后再点击播放！");
            return;
        }
        boolean image = isClickImage();
        if (image) {
            showToastLog("防呆函数2","你选中的类型不是视频数据！", true);
            return;
        }
        mDisplayImageView.setVisibility(View.INVISIBLE);
        MediaFile selectedMediaFile = mediaFileList.get(lastClickViewIndex);
        if ((selectedMediaFile.getMediaType() == MediaFile.MediaType.MOV) || (selectedMediaFile.getMediaType() == MediaFile.MediaType.MP4)) {
            mMediaManager.playVideoMediaFile(selectedMediaFile, error -> {
                if (null != error) {
                    setResultToToast("视频播放失败 " + error.getDescription());
                } else {
                    DJILog.e(TAG, "Play Video Success");
                }
            });
        }
    }

    private void moveToPosition(){
        if (lastClickViewIndex == -1) {
            // 索引值无效
            Log.e("页面M playVideo","负数索引，mediaFileList大小："+ mediaFileList.size());
            setResultToToast("请点击视频，再点击右侧文字后再点击播放！");
            return;
        }
        boolean image = isClickImage();
        if (image) {
            showToastLog("防呆函数2","你选中的类型不是视频数据！", true);
            return;
        }
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.prompt_input_position, null);
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(promptsView);
        final EditText userInput = (EditText) promptsView.findViewById(R.id.editTextDialogUserInput);
        alertDialogBuilder.setCancelable(false).setPositiveButton("OK", (dialog, id) -> {
                    String ms = userInput.getText().toString();
                    mMediaManager.moveToPosition(Integer.parseInt(ms),
                            new CommonCallbacks.CompletionCallback() {
                                @Override
                                public void onResult(DJIError error) {
                                    if (null != error) {
                                        setResultToToast("Move to video position failed" + error.getDescription());
                                    } else {
                                        DJILog.e(TAG, "Move to video position successfully.");
                                    }
                                }
                            });
                })
                .setNegativeButton("Cancel", (dialog, id) -> dialog.cancel());
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    /**
     * SIGANL_READY22添加后缀
     */
    private void addMeidaSuffix() {
//        if (lastClickViewIndex == -1) {
//            // 索引值无效
//            Log.e("页面M 加后缀","负数索引，mediaFileList大小："+ mediaFileList.size());
//            setResultToToast("请点击对应媒体的文本框再运行！");
//            return;
//        }
        //MediaFile selectedMediaFile = mediaFileList.get(lastClickViewIndex);
        Camera camera = DemoApplication.getCameraInstance();
//        camera.getCustomExpandFileName(new CommonCallbacks.CompletionCallbackWith<String>() {
//            @Override
//            public void onSuccess(String s) {
//                showToastLog("addMeidaSuffix", "get success rename" +s, false);
//            }
//
//            @Override
//            public void onFailure(DJIError djiError) {
//                showToastLog("addMeidaSuffix", "get rename fail"+djiError.getDescription(), false);
//            }
//        });
        camera.setCustomExpandFileName("abbbbb", new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    showToastLog("addMeidaSuffix", "success rename", false);
                } else {
                    showToastLog("addMeidaSuffix", "Not rename: " + djiError.getDescription(), false);
                }
            }
        });
        //TODO selectedMediaFile.setFileName();
        // TODO setMediaFileCustomInformation
    }

    /** SIGNAL_READY6  实现难度大 废弃了
     * 容错机制和防呆判断 在每一个里面添加 使用方法：
     * boolean equals = isIndexEqualsToLastClick();
     *         if (!equals) {
     *             showToastLog("防呆函数","请先点击缩略图，再点击右侧文本！", true);
     *             return;
     *         }
     */
    private boolean isIndexEqualsToLastClick() {
        if (bitmapIndexGlobal != lastClickViewIndex) {
            return false;
        }
        return true;
    }

    /** TODO
     * SIGNAL_READY4 防呆函数2 检测因为过快点击导致的出现了两个蓝色选择区域导致的卡死
     */
    private void isDoubleQuickClick() {

    }

    /**
     * 防止把图片当作视频点击功能导致崩溃
     * @return 是图片返回true
     */
    private boolean isClickImage() {
        final MediaFile mediaFile = mediaFileList.get(bitmapIndexGlobal);
        if (mediaFile != null) {
            if (mediaFile.getMediaType() != MediaFile.MediaType.MOV
                    && mediaFile.getMediaType() != MediaFile.MediaType.MP4) {
                return true;
            }
        } else {
            showToastLog("isClickImage", "获取mediaFile失败", false);
        }
        return false;
    }

    /**
     * 这个v是itemView 文字信息 不是缩率图
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.back_btn: {
                this.finish();
                break;
            }
            case R.id.delete_btn: {
                deleteFileByIndex(lastClickViewIndex);
                break;
            }
            case R.id.reload_btn: {
                getFileList();
                break;
            }
            case R.id.download_btn: {
                downloadFileByIndex(lastClickViewIndex);
                break;
            }
            case R.id.status_btn: {
                if (mPushDrawerSd.isOpened()) {
                    mPushDrawerSd.animateClose();
                } else {
                    mPushDrawerSd.animateOpen();
                }
                break;
            }
            case R.id.play_btn: {
                playVideo();
                break;
            }
            case R.id.resume_btn: {
                boolean image = isClickImage();
                if (image) {
                    showToastLog("防呆函数2","你选中的类型不是视频数据！", true);
                    break;
                }
                mMediaManager.resume(error -> {
                    if (null != error) {
                        setResultToToast("视频恢复播放失败：" + error.getDescription());
                    } else {
                        DJILog.e(TAG, "Resume Video Success");
                    }
                });
                break;
            }
            case R.id.pause_btn: {
                boolean image = isClickImage();
                if (image) {
                    showToastLog("防呆函数2","你选中的类型不是视频数据！", true);
                    break;
                }
                mMediaManager.pause(error -> {
                    if (null != error) {
                        setResultToToast("暂停视频播放失败：" + error.getDescription());
                    } else {
                        DJILog.e(TAG, "Pause Video Success");
                    }
                });
                break;
            }
            case R.id.stop_btn: {
                boolean image = isClickImage();
                if (image) {
                    showToastLog("防呆函数2","你选中的类型不是视频数据！", true);
                    break;
                }
                mMediaManager.stop(error -> {
                    if (null != error) {
                        setResultToToast("停止视频播放失败：" + error.getDescription());
                    } else {
                        DJILog.e(TAG, "Stop Video Success");
                    }
                });
                break;
            }
            case R.id.moveTo_btn: {
                moveToPosition();
                break;
            }
            // SIGNAL_READY22 测试用
//            case R.id.rename_btn: {
//                addMeidaSuffix();
//                break;
//            }
            default:
                break;
        }
    }

    private boolean isMavicAir2() {
        BaseProduct baseProduct = DemoApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MAVIC_AIR_2;
        }
        return false;
    }

    private boolean isAir2S() {
        BaseProduct baseProduct = DemoApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.DJI_AIR_2S;
        }
        return false;
    }

    private boolean isM300() {
        BaseProduct baseProduct = DemoApplication.getProductInstance();
        if (baseProduct != null) {
            return baseProduct.getModel() == Model.MATRICE_300_RTK;
        }
        return false;
    }
}