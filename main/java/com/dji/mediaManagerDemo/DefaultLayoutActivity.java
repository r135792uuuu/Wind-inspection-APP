package com.dji.mediaManagerDemo;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.SurfaceTexture;
import android.media.ExifInterface;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.SlidingDrawer;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.bouncycastle.asn1.cms.MetaData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import dji.common.camera.CameraStreamSettings;
import dji.common.camera.CameraVideoStreamSource;
import dji.common.camera.SettingsDefinitions;
import dji.common.camera.SystemState;
import dji.common.error.DJIError;
import dji.common.flightcontroller.Attitude;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.flightcontroller.LocationCoordinate3D;
import dji.common.flightcontroller.RTKState;
import dji.common.flightcontroller.rtk.CoordinateSystem;
import dji.common.flightcontroller.rtk.NetworkServiceChannelState;
import dji.common.flightcontroller.rtk.NetworkServiceState;
import dji.common.flightcontroller.rtk.ReferenceStationSource;
import dji.common.gimbal.GimbalState;
import dji.common.model.LocationCoordinate2D;
import dji.common.util.CommonCallbacks;
import dji.keysdk.ProductKey;
import dji.sdk.base.BaseProduct;
import dji.sdk.camera.Camera;
import dji.sdk.camera.Lens;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.flightcontroller.RTK;
import dji.sdk.media.MediaFile;
import dji.sdk.media.MediaFileInfo;
import dji.sdk.media.MediaManager;
import dji.sdk.network.RTKNetworkServiceProvider;
import dji.sdk.products.Aircraft;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.controls.CameraControlsWidget;
import kotlin.Metadata;

// uxsdk的UI界面
public class DefaultLayoutActivity extends AppCompatActivity implements View.OnClickListener{

    private Button mMediaManagerBtn;
    private FPVWidget fpvWidget;
    // 测试用
    private CameraControlsWidget cameraControlsWidget;
    // 各个按钮 SIGNAL_READY2
    private Button mMOPBtn, mSendCommandBtn, mPauseBtn, mModeBtn,
            mBlade1Btn, mBlade2Btn, mBlade3Btn, mBlade4Btn;
    // SIGNAL_READY6 同时拍照测试按钮
    private Button mRecordingPhotoBtn;
    private Handler photoHandler;
    // SIGNAL_READY15 更新视角handler
    private Handler fpvWidgetHandler;
    private HandlerThread fpvWidgetThread;
    // 文本显示
    private TextView mStatus1Tv;
    private TextView mStatus2Tv;
    private TextView mStatus3Tv;
    private TextView mStatus4Tv;
    private TextView mStatus5Tv;
    private TextView mStatus6Tv;
    private TextView mStatus7Tv;

    // MOP
    private static final byte TRIGGER_HIDE_BUTTON = (byte) 0x11; // 接收到这个字节，触发三个blade隐藏按钮
    private byte[] uploadDataBuf = new byte[10]; // MSDK发送数据的缓存变量
    private byte[] downloadDataBuf = new byte[10]; // MSDK接收数据的缓存变量
    // Command
    private static final int OSDK_FORCE_ARRAY_INT_LENGTH = 12;  // int = 4 bytes
    private static final int READ_DATA_FROM_OSDK_FREQ = 20;  // 读取OSDK数据的频率
    private static final float OSDK_FLOAT_DATA_INT_MAGNIFICATION = 10;  // float数据转int放大为int数据的放大倍数
    // 注意需要导入一下V4 完整代码里面的ModuleVerificationUtil.java这个工具类
    private FlightController flightController = null;
    private BaseProduct product;
    // 要发送给无人机的指令
    byte[] byteToOSDK = new byte[1];
    // 可以直接从MSDK拿到的数据:速度 焦距 云台俯仰角
    protected double lastVelocityX = 0.0;
    protected double lastVelocityY = 0.0;
    protected double lastVelocityZ = 0.0;
    protected double lastVelocityTotal = 0.0;
    protected Integer focalLength = 0;
    protected float gimbalAttitudePitch;
    // SIGNAL_READY14 新增无人机位姿（欧拉角）和飞机位置
    protected LocationCoordinate3D currentVehiclePosition;
    protected Attitude currentVehicleAttitude;
    // 模式选择按钮  TODO 这里应该用状态机
    protected byte[] modeSignal = new byte[3]; // SIGNAL_READY25
    HandlerThread updateMSDKDataThread;
    Handler updateMSDKDataHandler;
    private static final int UPDATE_INTERVAL = 500; // 500ms 更新一次数据
    // RTK
    private RTK rtk = null;
    private TextView  mrtkTv;
    private boolean isNetowrkRTKSet = false;
    private boolean isCoordinateSystemSet = false;
    private boolean isNetowrkRTKAccessUse = false;
    private double rtkLocationLat; // 经纬
    private double rtkLocationLon;
    private float rtkAltitude;
    // 相机显示视频流切换 SIGNAL_READY3
    private TextView mHiFlyTv;
    // SIGNAL_READY4
    private Integer cameraVideoSource = 0; // 默认0是ZOOM、１是WIDE、２是红外
    // SIGNAL_READY14
    private double vehicleToBladeDistance = 0.0;
    private MediaManager mMediaManager;
    // SIGNAL_READY14 全局管理相机类
    private Camera cameraGlobal;
    // SIGNAL_READY16
    private Integer switchVideoSourceSignal = 1; // 奇数为广角 偶数变焦
    private long currentAbsolutelyTime; // 从1970.1.1开始秒数
    // SIGNAL_READY18
    private Button mRTK1Btn;
    private Button mRTK2Btn;
    private Button mRTK3Btn;
    private Button mRTK4Btn;
    private Integer rtkTriggerSignal = 1; // 和switchVideoSourceSignal类似
    private boolean isNetowrkRTKEnable = false;
    private boolean isNetowrkRTKStartSuccess = false;
    // 开一个给RTK做update的线程，更新数据和状态，防止占用主线程崩溃
    HandlerThread updateRTKThread;
    Handler updateRTKHandler;
    // SIGNAL_READY19 增加接收OSDK回传数据的变量储存
    float finalForceFloatX; // 接收叶片距离变量
    float finalForceFloatY; // 接收避障触发提示
    float finalForceFloatZ;
    float finalForceFloatW;
    // RTK 成功连接只有第一次显示
    private int notifyRTKSuccessConnectSignal = 1;
    // SIGNAL_READY20
    private TextView currentRTKStatus;
    // SIGNAL_READY21 切换页面中断频繁更新的线程
    private boolean isThreadRunning = true;
    private boolean shouldContinueRunning = true;
    // SIGNAL_READY23
    private Button mSuffixBtn;
    // SIGNAL_READY24 添加修改名字的线程 监听什么时候按下
    HandlerThread listenSuffixThread;
    Handler listenSuffixHandler;
    private String suffixVar1;
    private String suffixVar2;
    private int isSuffixReadySignal = 0;
    // SIGNAL_READY25
    private int isModeChooseSelect = 1; // 监听按下模式选择按键


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_layout);
        initUI();
        cameraGlobal = DemoApplication.getCameraInstance();
        showToastLog("onCreate", "initUI完成", false);
        Integer createCameraModeSignal = getCameraFlatMode();
        // testOncreate();
        // SIGNAL_READY14
        mMediaManager = DemoApplication.getCameraInstance().getMediaManager();
        //  SIGNAL_READY18
        initRTK();
        // SIGNAL_READY21
        isThreadRunning = true;
    }

    @Override
    protected void onDestroy() {
        // 取消线程
        if (updateMSDKDataHandler != null && updateMSDKDataThread != null) {
            updateMSDKDataHandler.removeCallbacksAndMessages(null);
            updateMSDKDataThread.quitSafely();
        }
        if (fpvWidgetHandler != null) {
            fpvWidgetHandler.removeCallbacksAndMessages(null);
        }
        // SIGNAL_READY18 关闭RTK更新线程
        if (updateRTKHandler != null && updateRTKThread != null) {
            updateRTKHandler.removeCallbacksAndMessages(null);
            updateRTKThread.quitSafely();
        }
        // SIGNAL_READY24
        if (listenSuffixHandler != null && listenSuffixThread != null) {
            listenSuffixHandler.removeCallbacksAndMessages(null);
            listenSuffixThread.quitSafely();
        }
        // 关闭RTK服务
        RTKNetworkServiceProvider.getInstance().removeNetworkServiceStateCallback(networkServiceState -> showToastLog("onDestroy","移除网络服务监听",false));
        RTKNetworkServiceProvider.getInstance().stopNetworkService(djiError -> showToastLog("onDestroy","关闭RTK", false));
        // SIGNAL_READY18
        showToastLog("onDestroy","关闭onDestroy",false);
        super.onDestroy();
    }

    /**
     * 测试函数草稿箱 SIGNAL_READY3
     */
    private void testOncreate() {
        //switchCameraFlatMode(SettingsDefinitions.FlatCameraMode.PHOTO_SINGLE);
        // SIGNAL_READY3 TEST
        //getDifferentCameraModeSource(1);
        //getDifferentCameraModeSource(2);
        getDifferentCameraModeSource(0); // 获取当前来源

    }

    void initUI() {
        // 初始化飞控类和onBoard类
        initFlightController();
        // 初始化按钮
        mMOPBtn = (Button) findViewById(R.id.btn_mop);
        //SIGNAL_READY2
        mSendCommandBtn = (Button) findViewById(R.id.btn_send_command);
        mrtkTv = (TextView) findViewById(R.id.tv_RTKConnect) ;

        mPauseBtn = (Button) findViewById(R.id.btn_pause);
        mModeBtn = (Button) findViewById(R.id.btn_mode);
        mMediaManagerBtn = (Button)findViewById(R.id.btn_mediaManager);
        fpvWidget = findViewById(R.id.fpv_widget);  // UI界面的主界面
        // CameraControlsWidget这个UI控件，封装了FPVDemo中类似camera类型拍照的功能
        // 照片和视频拍摄、录制完之后会进入到listView
        // 隐藏按钮 SIGNAL_READY25
        mBlade1Btn = (Button) findViewById(R.id.btn_blade1);
        mBlade1Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见
        mBlade2Btn = (Button) findViewById(R.id.btn_blade2);
        mBlade2Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见
        mBlade3Btn = (Button) findViewById(R.id.btn_blade3);
        mBlade3Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见
//        // SIGNAL_READY2
//        mBlade4Btn = (Button) findViewById(R.id.btn_blade4);
//        mBlade4Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见

        // SIGNAL_READY6 录像同时拍照测试
        mRecordingPhotoBtn = (Button) findViewById(R.id.btn_recording_photo);
        mRecordingPhotoBtn.setOnClickListener(this);


        // SIGNAL_READY18 rtk的监听在按下RTK连接后设置
        mRTK1Btn = (Button) findViewById(R.id.btn_rtke1);
        mRTK1Btn.setOnClickListener(this);
        mRTK2Btn = (Button) findViewById(R.id.btn_rtke2);
        mRTK2Btn.setOnClickListener(this);
        mRTK3Btn = (Button) findViewById(R.id.btn_rtke3);
        mRTK3Btn.setOnClickListener(this);
        mRTK4Btn = (Button) findViewById(R.id.btn_rtke4);
        mRTK4Btn.setOnClickListener(this);

        // 设置按钮监听
        // SIGNAL_READY2
        mrtkTv.setOnClickListener(this);

        mMOPBtn.setOnClickListener(this);
        mSendCommandBtn.setOnClickListener(this); // SIGNAL_READY2
        mPauseBtn.setOnClickListener(this);
        mModeBtn.setOnClickListener(this);
        // SIGNAL_READY2 把隐藏按钮 mBlade1Btn 2 3按钮监听放到ifHidenButtonTrigger里面去了

        mMediaManagerBtn.setOnClickListener(this); // 切换页面按钮 监听事件
        // 初始化文本
        mStatus1Tv = findViewById(R.id.tv_status1);
        mStatus2Tv = findViewById(R.id.tv_status2);
        mStatus3Tv = findViewById(R.id.tv_status3);
        mStatus4Tv = findViewById(R.id.tv_status4);
        mStatus5Tv = findViewById(R.id.tv_status5);
        mStatus6Tv = findViewById(R.id.tv_status6);
        mStatus7Tv = findViewById(R.id.tv_status7);

        // SIGNAL_READY3
        mHiFlyTv = (TextView) findViewById(R.id.hi_fly);
        mHiFlyTv.setOnClickListener(this);

        runThreadHandler();

        // SIGNAL_READY6
        photoHandler = new Handler();

        // SIGNAL_READY15
        fpvWidgetHandler = new Handler();

        // SIGNAL_READY20
        currentRTKStatus = findViewById(R.id.tv_current_rtk_status);
        // SIGNAL_READY23
        mSuffixBtn = findViewById(R.id.suffix_btn);
        mSuffixBtn.setOnClickListener(this);

        showToastLog("initUI", "initUI结束",false);

    }

    /**
     * 全局RTK初始化
     */
    private void initRTK() {
        if (ModuleVerificationUtil.isRTKAvailable()) {
            rtk = DemoApplication.getAircraftInstance().getFlightController().getRTK();
        } else {
            showToastLog("initRTK","设备不支持RTK", true);
        }
    }


    /** 需要判断，当isNetowrkRTKStartAccessUse 为true时 再判断
     * MSDK获取RTK数据 SIGNAL_READY18
     * 重构：开一个线程来执行，只负责update状态和数据，不要影响主线程
     * 因为都是update也不好直接放在回调执行函数里面，消息太多会爆炸
     */
    private void updateMSDKRTKData() {
        // 监听RTK的使用状态 SIGNAL_READY20 放在线程中避免阻塞主线程，以及不受生命周期影响
        listenRTKStatus();
        // 监听RTK的数据更新
        listenRTKData();
    }

    /** 监听RTK数据的变化 SIGNAL_READY19
     * SIGNAL_READY18
     */
    private void listenRTKData() {
        RTK rtkListenData = DemoApplication.getAircraftInstance().getFlightController().getRTK();
        if (ModuleVerificationUtil.isRTKAvailable()) {
            // SIGNAL_READY20取消 && isCoordinateSystemSet
            if (isNetowrkRTKSet && isNetowrkRTKStartSuccess && isNetowrkRTKEnable) {
                if (isNetowrkRTKAccessUse) {     // 这个状态在listenRTKStatus被引用
                    // 第一次成功提示用户RTK成功连接
                    if (notifyRTKSuccessConnectSignal == 1) { // SIGNAL_READY20 修改不然会崩溃
                        showToastLog("listenRTKStatus","RTK收敛成功，数据正常", false);
                        notifyRTKSuccessConnectSignal = 0;
                    }
                    // SIGNAL_READY20 SIGNAL_READY21
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                currentRTKStatus.setText("RTK收敛成功");
                            }
                        });
                    rtkListenData.setStateCallback(new RTKState.Callback() {
                        @Override
                        public void onUpdate(@NonNull RTKState rtkState) {
                            rtkLocationLat = rtkState.getFusionMobileStationLocation().getLatitude();
                            rtkLocationLon = rtkState.getFusionMobileStationLocation().getLongitude();
                            rtkAltitude = rtkState.getFusionMobileStationAltitude();
                        }
                    });
                } else {
                    rtkListenData.setStateCallback(new RTKState.Callback() {
                        @Override
                        public void onUpdate(@NonNull RTKState rtkState) {
                            // SIGNAL_READY20
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // 获取当前卫星数量
                                    currentRTKStatus.setText("RTK卫星数：" + rtkState.getSatelliteCount());
                                }
                            });
                        }
                    });
                }
            } else {
            }
        } else { // SIGNAL_READY20
            //currentRTKStatus.setText("飞机已断开,请重启");
            showToastLog("updateMSDKRTKData","飞机断开连接，请重启飞机连接RTK！", false);
        }
    }

    /**
     * SIGNAL_READY18 SIGNAL_READY19
     * 监听RTK  addNetworkServiceStateCallback，添加对网络RTK状态的监听
     * 当NetworkServiceState.getChannelState为TRANSMITTING时说明网络RTK已经打开并且连接。
     */
    private void listenRTKStatus() {
        RTK rtkListenStatus = DemoApplication.getAircraftInstance().getFlightController().getRTK();
        RTKNetworkServiceProvider.getInstance().addNetworkServiceStateCallback(new NetworkServiceState.Callback() {
            @Override
            public void onNetworkServiceStateUpdate(NetworkServiceState networkServiceState) {
                //showToastLog("listenRTKStatus","RTK状态监听速度signal 10Hz", false);
                if (networkServiceState.getChannelState() == NetworkServiceChannelState.TRANSMITTING) {
                    isNetowrkRTKAccessUse = true;
                } else {
                    isNetowrkRTKAccessUse = false;
                }
            }
        });
    }

    /**
     * 线程运行函数
     */
    private void runThreadHandler() {
        // 初始化就开始更新MSDK部分的数据，需要用到线程
        updateMSDKDataThread  = new HandlerThread("uploadMSDKData");
        updateMSDKDataThread.start();
        updateMSDKDataHandler = new Handler(updateMSDKDataThread.getLooper());
        updateMSDKDataHandler.postDelayed(updateDataRunnable, UPDATE_INTERVAL); // 500ms
        // 开一个更新RTK数据回调的线程 SIGNAL_READY18
        updateRTKThread = new HandlerThread("updateRTK");
        updateRTKThread.start();
        updateRTKHandler = new Handler(updateRTKThread.getLooper());
        updateRTKHandler.postDelayed(updateRTKRunnable, UPDATE_INTERVAL);
        // SIGNAL_READY24 监听视频图片添加后缀的函数
        listenSuffixThread = new HandlerThread("listenSuffix");
        listenSuffixThread.start();
        listenSuffixHandler = new Handler(listenSuffixThread.getLooper());
        listenSuffixHandler.postDelayed(listenSuffixRunnable, UPDATE_INTERVAL);
    }

    // 取消频繁更新 可能影响媒体页面 SIGNAL_READY21
    private Runnable updateDataRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                updateDataFromMSDK();
                //Log.e("updateDataRunnable","isThreadRunning"+isThreadRunning);
                //receiveDataFromOSDK(); // SIGNAL_READY19 先用按钮测试
                if (isThreadRunning) {
                    // 使用Handler在主线程中更新UI
                    runOnUiThread(() -> {
                        // SIGNAL_READY24 修改小数点后两位显示
                        // 速度
                        //String testStr0 = String.valueOf(lastVelocityTotal);
                        //mStatus1Tv.setText(testStr0);
                        mStatus1Tv.setText(String.format("%.2f", lastVelocityTotal));
                        // 焦距不用
                        mStatus7Tv.setText(String.valueOf(focalLength));
                        // RTK
//                        mStatus2Tv.setText(String.valueOf(rtkLocationLat));
//                        mStatus4Tv.setText(String.valueOf(rtkLocationLon));
//                        mStatus6Tv.setText(String.valueOf(rtkAltitude));
                        mStatus2Tv.setText(String.format("%.2f", rtkLocationLat));
                        mStatus4Tv.setText(String.format("%.2f", rtkLocationLon));
                        // SIGNAL_READY24 如果altitude小于0直接清0
                        if (rtkAltitude < 0) { rtkAltitude = 0;}
                        mStatus6Tv.setText(String.format("%.2f", rtkAltitude));
                        // 云台俯仰角 SIGNAL_READY5 不用
                        mStatus5Tv.setText(String.valueOf(gimbalAttitudePitch));
                        //mStatus5Tv.setText(String.format("%.1f", gimbalAttitudePitch));
                        // SIGNAL_READY19
                        //mStatus3Tv.setText(String.valueOf(finalForceFloatX));
                        mStatus3Tv.setText(String.format("%.2f", finalForceFloatX));
                        // TODO OSDK回传触发避障提示框 SIGNAL_READY19
                        if (finalForceFloatY == 0x77) {
                            // 弹出提示框和返航
                            ifAvoidanceTrigger();
                        }
                    });
                }
                Thread.sleep(200);
                updateMSDKDataHandler.postDelayed(this, UPDATE_INTERVAL); // 重新安排下一次执行
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    };

    // 取消频繁更新 可能影响媒体页面 SIGNAL_READY21
    private Runnable updateRTKRunnable = new Runnable() {
        @Override
        public void run() {
            // 只更新RTK状态和监听数据
            if (isThreadRunning) {
                try {
                    updateMSDKRTKData(); // SIGNAL_READY18
                    Thread.sleep(1300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                updateRTKHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };

    // SIGNAL_READY24
    private Runnable listenSuffixRunnable = new Runnable() {
        @Override
        public void run() {
                try {
                    // SIGNAL_READY26 移动到这里
                    if (!suffixVar1.isEmpty() && !suffixVar2.isEmpty()) {
                        if (isSuffixReadySignal == 1) { // 当用户新一次的选择完成再执行
                            isListenSuffixReady();
                        }
                    } else {
//                        showToastLog("suffix","名字后缀没有选择上", false);
                    }
                    Thread.sleep(600);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

            listenSuffixHandler.postDelayed(this, UPDATE_INTERVAL);
        }
    };

    /**
     * SIGNAL_READY19 触发避障返航小页面
     */
    private void ifAvoidanceTrigger() {
        // TODO :主节点那边降落，这里预留出来，现在只负责显示
        //View dialogView = getLayoutInflater().inflate(R.layout.dialog_avoidance_alert, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("触发避障模式！")
                .setMessage("无人机避障被触发，正在执行返航任务...")
                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 获取当前相机的模式，主页面保持为拍照模式
     */
    private int getCameraFlatMode(){
        // 实现相机类
        Camera camera = DemoApplication.getCameraInstance();
        final String[] cameraModeName = {""};
        final String cameraModeName2;
        Integer cameraModeSignal = 0;
        if (camera != null) {
            camera.getFlatMode(new CommonCallbacks.CompletionCallbackWith<SettingsDefinitions.FlatCameraMode>() {
                @Override
                public void onSuccess(SettingsDefinitions.FlatCameraMode flatCameraMode) {
                    String cameraMode = flatCameraMode.toString();
                    cameraModeName[0] = cameraMode;
                    showToastLog("获取拍照模式","当前相机模式为：" + cameraMode, false);
                }

                @Override
                public void onFailure(DJIError djiError) {
                    showToastLog("获取拍照模式","获取拍照模式失败："+ djiError.getDescription() , false);
                }
            });
        }
        else {
            return 0;
        }
        if ("PHOTO_SINGLE".equals(cameraModeName[0])) { // SIGNAL_READY4
            cameraModeSignal = 1; // 用1返回
            showToastLog("获取拍照模式", "返回值1:" + cameraModeSignal, false);
        } else if ("VIDEO_NORMAL".equals(cameraModeName[0])) {
            cameraModeSignal = 2;
            showToastLog("获取拍照模式", "返回值2:" + cameraModeSignal, false);
        } else {
            cameraModeSignal = 3;
            showToastLog("获取拍照模式", "返回值3:" + cameraModeName[0], false);
        }
        return cameraModeSignal;
    }

    /**
     * 实现相机模式切换，防止出问题 SIGNAL_READY2
     * @param flatCameraMode
     */
    private void switchCameraFlatMode(SettingsDefinitions.FlatCameraMode flatCameraMode){
        // 实现相机类
        Camera camera = DemoApplication.getCameraInstance();
        String cameraMode = flatCameraMode.toString();
        if (camera != null) {
            camera.setFlatMode(flatCameraMode, error -> {
                if (error == null) {
                    showToastLog("切换相机模式","切换相机模式为："+cameraMode, true);
                } else {
                    showToastLog("切换相机模式","相机模式切换失败"+error.getDescription(), true);
                }
            });
        }
    }

    /**
     * Test：SIGNAL_READY2
     * @param cameraStreamSettings
     */
    private void needCurrentLiveViewStreamTest(CameraStreamSettings cameraStreamSettings) {
        boolean abb = cameraStreamSettings.needCurrentLiveViewStream();
        //showToastLog("needCurrentLiveViewStreamTest", "abb:" + abb, false);
    }

    /**
     * 获取相机数据来源 SIGNAL_READY3
     * 包括：相机视频流源0、拍照相机流源1、录制视频相机流2
     */
    private void getDifferentCameraModeSource(int mode) {
        Camera camera = DemoApplication.getCameraInstance();
        switch (mode) {
            case 0: {
                camera.getCameraVideoStreamSource(new CommonCallbacks.CompletionCallbackWith<CameraVideoStreamSource>() {
                    @Override
                    public void onSuccess(CameraVideoStreamSource cameraVideoStreamSource) {
                        // 默认、wide、zoom、红外、未知  这个是不是显示在频幕上的视频流？
                        showToastLog("获取相机视频流数据来源","数据来源是："+ cameraVideoStreamSource.name(), false);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        djiErrorJudeg(djiError, "获取相机视频流数据来源", "获取相机视频数据源失败", false);
                    }
                });

            }
            case 1: {
                camera.getCaptureCameraStreamSettings(new CommonCallbacks.CompletionCallbackWith<CameraStreamSettings>() {
                    @Override
                    public void onSuccess(CameraStreamSettings cameraStreamSettings) {
                        showToastLog("获取相机拍照数据来源","数据来源是："+ cameraStreamSettings.toString(), false);
                        needCurrentLiveViewStreamTest(cameraStreamSettings);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        djiErrorJudeg(djiError, "获取相机拍照流数据来源", "获取相机拍照数据源失败", false);
                    }
                });
            }
            case 2: {
                camera.getRecordCameraStreamSettings(new CommonCallbacks.CompletionCallbackWith<CameraStreamSettings>() {
                    @Override
                    public void onSuccess(CameraStreamSettings cameraStreamSettings) {
                        showToastLog("获取相机录制数据来源","数据来源是："+ cameraStreamSettings.toString(), false);
                        needCurrentLiveViewStreamTest(cameraStreamSettings);
                    }

                    @Override
                    public void onFailure(DJIError djiError) {
                        djiErrorJudeg(djiError, "获取相机录频数据来源", "获取相机录频数据源失败", false);
                    }
                });
            }
        }
    }

    /**
     * 只负责初始化飞控类和onBoard类
     */
    private void initFlightController() {
        product = DemoApplication.getProductInstance();
        // 初始化product类和FlightController类
        if (product == null || !product.isConnected()) {
            // SIGNAL_READY12
            showToastLog("initFC","飞机已断开连接", true);
        } else {
            if (product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
                showToastLog("initFC","成功连接飞控", false);
                if(flightController.isOnboardSDKDeviceAvailable()) {
                    showToastLog("initFC","成功检测到机载电脑", false);
                }
                else {
                    showToastLog("initFC","当前设备不支持通信", true);
                }
            }

        }
    }

    /**
     * 用这个更好的，同时显示toast和Log的消息
     * @param tag
     * @param description
     */
    private void showToastLog(final String tag, final String description, boolean isRunToast) {
        Log.e("页面A: " + tag, description);
        if (isRunToast) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(DefaultLayoutActivity.this, description, Toast.LENGTH_SHORT).show();  // 显示2s  long时3.5s
                }
            });
        }
    }

    /**
     * API回调报错函数，增加代码复用能力
     * @param djiError 传入的错误代码
     * @param tag 函数位置标签
     * @param description 报错的具体描述（不用输入错误代码）
     * @param isShowToast 是否显示在toast
     */
    private void djiErrorJudeg(DJIError djiError, final String tag, final String description, boolean isShowToast) {
        if (djiError == null) {
            showToastLog(tag, description, isShowToast);
        }
        else {
            showToastLog(tag, description + "请重试,错误代码为: " + djiError.getDescription(), true);
        }
    }


    /** 实时更新MSDK能拿到的数据
     * onCreate开一个线程再线程里面去搞
     */
    private void updateDataFromMSDK() {
        // 获取飞控数据
        if (flightController != null) {
            // 通过回调获得飞控类里面的数据  比如海拔经纬 飞行模式等
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    lastVelocityX = flightControllerState.getVelocityX();
                    lastVelocityY = flightControllerState.getVelocityY();
                    lastVelocityZ = flightControllerState.getVelocityZ();
                    lastVelocityTotal = Math.sqrt(lastVelocityX * lastVelocityX + lastVelocityY * lastVelocityY + lastVelocityZ * lastVelocityZ);
                    //Log.e("页面A onUpdate","lastVelocityTotal"+ lastVelocityTotal);

                    // TODO SIGNAL_READY14
                    currentVehiclePosition = flightControllerState.getAircraftLocation();
                    currentVehicleAttitude = flightControllerState.getAttitude();
                }
            });
        } else {
            Log.e("页面A updateMKeyData", "飞控类实例不存在，断连接");
        }
        // 获取焦距
        DemoApplication.getCameraInstance().getLens(0).getHybridZoomFocalLength(new CommonCallbacks.CompletionCallbackWith<Integer>() {
            @Override
            public void onSuccess(Integer integer) {
                //showToastLog("updateDataFromMSDK","获取焦距:" + integer, false);
                focalLength = integer;
            }

            @Override
            public void onFailure(DJIError djiError) {
                //showToastLog("updateDataFromMSDK","获取焦距失败："+ djiError.getDescription(), false);
            }
        });
        // 获取云台数据 SIGNAL_READY5
        DemoApplication.getProductInstance().getGimbal().setStateCallback(new GimbalState.Callback() {
            @Override
            public void onUpdate(@NonNull GimbalState gimbalState) {
                gimbalAttitudePitch = gimbalState.getAttitudeInDegrees().getPitch();
            }
        });
    }

    // SIGNAL_READY26 每次执行成功后清零避免连续执行
    private void isListenSuffixReady() {
        Camera camera = DemoApplication.getCameraInstance();
        String strTemp  = suffixVar1 + suffixVar2;
        camera.setCustomExpandFileName(strTemp, new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    showToastLog("addMeidaSuffix", "设置后缀:" + strTemp, true);
                    isSuffixReadySignal = 0; // 命名完成关闭，不进入准备环节

                } else {
                    showToastLog("addMeidaSuffix", "设置后缀失败： " + djiError.getDescription(), true);
                    isSuffixReadySignal = 0;
                }
            }
        });

    }

    /** SIGNAL_READY23 SIGNAL_READY24 重构线程
     * 弹出一个页面来选择下一个拍摄的名字是什么
     * @return
     */
    private void showSuffixDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_suffix_input, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        RadioGroup radioGroup1 = dialogView.findViewById(R.id.group_file4); // 扇叶号
        RadioGroup radioGroup2 = dialogView.findViewById(R.id.group_file5); // 迎风面那些参数
        builder.setView(dialogView)
                .setTitle("命名后缀选择")
                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch (radioGroup1.getCheckedRadioButtonId()) {
                            case  R.id.radioButton1: {
                                suffixVar1 = "01";
                                break;
                            }
                            case  R.id.radioButton2: {
                                suffixVar1 = "02";
                                break;
                            }
                            case  R.id.radioButton3: {
                                suffixVar1 = "03";
                                break;
                            }
                        }
                        switch (radioGroup2.getCheckedRadioButtonId()) {
                            case  R.id.radioButton4: {
                                suffixVar2 = "Y";
                                break;
                            }
                            case  R.id.radioButton5: {
                                suffixVar2 = "B";
                                break;
                            }
                            case  R.id.radioButton6: {
                                suffixVar2 = "F";
                                break;
                            }
                            case  R.id.radioButton7: {
                                suffixVar2 = "R";
                                break;
                            }
                        }
                        // SIGNAL_READY24
                        // SIGNAL_READY26 考虑把这个判断移动到监听线程中去
                        isSuffixReadySignal = 1; // 选择完再赋值 这里是同步
                    }
                })
                .setNegativeButton("取消",null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 主页面更新MOP传输过来的数据 TODO
     * @param b1 状态数据1
     * @param b2 数据2
     * @param b3 数据3
     */
    private void updateDataFromOSDK(int b1, int b2, int b3) {
        mStatus1Tv.setText(b1);
        mStatus2Tv.setText(b2);
        mStatus3Tv.setText(b3);
    }

    /**  SIGNAL_READY2 SIGNAL_READY25 全部重构
     * 判断是否触发隐藏按钮的显示
     */
    private void ifHidenButtonTrigger() {
            mBlade1Btn.setVisibility(View.VISIBLE);
            mBlade1Btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    modeSignal[0] = (byte) 0x10; // 启动节点状态机
                }
            });
            mBlade2Btn.setVisibility(View.VISIBLE);
            mBlade2Btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showModeSelectionDialog(); // 选择三个模式
                }
            });
            mBlade3Btn.setVisibility(View.VISIBLE);
            mBlade3Btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    modeSignal[0] = (byte) 0x90; // 一键起飞
                }
            });

    }

    /**
     * 隐藏按钮 独立出来  SIGNAL_READY2
     */
    private void dismissHiddenBladeBtn() {
        mBlade1Btn.setVisibility(View.GONE);
        mBlade2Btn.setVisibility(View.GONE);
        mBlade3Btn.setVisibility(View.GONE);
    }

    /**
     * 切换相机显示来源 测试函数 SIGNAL_READY3
     * @param mode 选择测试API
     */
    private void switchCameraDisplaySourceTest(Integer mode, byte modeByte) {
        Camera camera = DemoApplication.getCameraInstance();
        Lens cameraLens2 = DemoApplication.getCameraInstance().getLens(2); // 2是
        Lens cameraLens0 = DemoApplication.getCameraInstance().getLens(0);
        Lens cameraLens1 = DemoApplication.getCameraInstance().getLens(1);
        //showToastLog("切换显示模式","镜头2："+cameraLens2.getDisplayName()+"镜头1"+cameraLens1.getDisplayName()+"镜头0："+cameraLens0.getDisplayName(),false);
        // 这个是变焦还是光学还是红外？
        if (modeByte == 0x21 && mode == 0) { // 变焦  ZOOM正常
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.ZOOM;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "切换显示模式0x21", "切换变焦模式", false);

                }
            });
        } else if (modeByte == 0x22 && mode == 1) { // 广角
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.WIDE;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    if (djiError == null) {
                        djiErrorJudeg(djiError, "切换显示模式0x22", "切换广角模式", false);
                        //fpvWidget.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));  // onSurfaceTextureAvailable实现视频流
                    }
                }
            });
        } else if (modeByte == 0x23 && mode == 2) { // 红外
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.INFRARED_THERMAL;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "切换显示模式0x23", "切换红外模式", false);
                }
            });
        } else if (modeByte == 0x24 && mode ==3) { // 画中画测试
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.INFRARED_THERMAL;
            SettingsDefinitions.DisplayMode displayMode = SettingsDefinitions.DisplayMode.PIP; // MSX
            cameraLens2.setDisplayMode(displayMode, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "切换显示模式0x24", "切换为分屏模式", false);
                    if (djiError == null) {
                        SettingsDefinitions.PIPPosition pipPosition = SettingsDefinitions.PIPPosition.ALIGN; // SIDE_BY_SIDE 其他的
                        cameraLens2.setPIPPosition(pipPosition, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                djiErrorJudeg(djiError, "切换显示模式0x24", "画中画设置", false);
                            }
                        });
                    }
                }
            });
        } else {
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.WIDE;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "切换0x23", "测试设置", false);
                }
            });
        }
    }


    /**
     * SIGNAL_READY18
     */
    private void ifRTKButtonTrigger() {
        mRTK1Btn.setVisibility(View.VISIBLE);
        mRTK2Btn.setVisibility(View.VISIBLE);
        mRTK3Btn.setVisibility(View.VISIBLE);
        mRTK4Btn.setVisibility(View.VISIBLE);
    }

    /**
     * 隐藏按钮 独立出来  SIGNAL_READY2
     */
    private void dismissRTKBtn() {
        mRTK1Btn.setVisibility(View.GONE);
        mRTK2Btn.setVisibility(View.GONE);
        mRTK3Btn.setVisibility(View.GONE);
        mRTK4Btn.setVisibility(View.GONE);
    }

    /**
     * SIGNAL_READY23 实时捕获后缀添加
     */
//    private void addMeidaSuffix() {
//        Camera camera = DemoApplication.getCameraInstance();
//        String strTemp = "";
//        //
//        strTemp = showSuffixDialog(); // TODO 还是要用线程才行
//
//        String finalStrTemp = strTemp;
//        camera.setCustomExpandFileName(strTemp, new CommonCallbacks.CompletionCallback() {
//            @Override
//            public void onResult(DJIError djiError) {
//                if (djiError == null) {
//                    showToastLog("addMeidaSuffix", "设置后缀:" + finalStrTemp, true);
//                } else {
//                    showToastLog("addMeidaSuffix", "设置后缀失败： " + djiError.getDescription(), true);
//                }
//            }
//        });
//    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_mediaManager : {
                showToastLog("onClick","监听到媒体流页面按下",false);
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.btn_mop : {
                showToastLog("onClick","监听到媒体流页面按下",false);
                Intent intent = new Intent(this, MOPSampleActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.btn_send_command : {
                showToastLog("onClick","监听到发送指令按钮按下",false);
                // SIGNAL_READY2 SIGNAL_READY25
                sendDataToOnboard(modeSignal);
                break;
            }
            case R.id.btn_mode : {
                // SIGNAL_READY2 SIGNAL_READY25
                showToastLog("onClick","监听到模式选择按钮按下",false);
                // 初始化按下可见
                isModeChooseSelect = isModeChooseSelect + 1;
                if (isModeChooseSelect % 2 == 0) { // 开始没有 需要点击触发
                    ifHidenButtonTrigger();
                } else { // 再次点击之后消失
                    dismissHiddenBladeBtn();
                }
                break;
            }
            case R.id.hi_fly : {
                // SIGNAL_READY2
                showToastLog("onClick","监听到HI-FLY按钮按下",false);
                // SIGNAL_READY4
                // SIGNAL_READY16 取消掉PureZoom界面，直接再主页面更换
                switchVideoSourceSignal = switchVideoSourceSignal + 1;
                if (switchVideoSourceSignal % 2 == 0) {
                    switchCameraDisplaySourceTest(0, (byte) 0x21);
                    fpvWidget.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));
                } else {
                    switchCameraDisplaySourceTest(1, (byte) 0x22);
                    fpvWidget.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));
                }
                break;
            }
            case R.id.btn_recording_photo : {
                // SIGNAL_READY22

                captureAction();
                break;
            }
            case R.id.btn_rtke1: { // CMCC // SIGNAL_READY20
                if (rtk != null) {
                    showToastLog("initRTK","进入开始准备等待1",false);
                    rtk.setReferenceStationSource(ReferenceStationSource.NTRIP_NETWORK_SERVICE, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                showToastLog("initRTK","准备开启网络RTK连接...(1/5)", true);
                                isNetowrkRTKSet = true;
                            } else {
                                showToastLog("initRTK","rtk开启失败,检查遥控器网络连接(1/5):" + djiError, true);
                            }
                        }
                    });
                } else {
                    showToastLog("initRTK","rtk对象为空", true);
                }
                break;
            }
            case R.id.btn_rtke2: {
                // SIGNAL_READY23
                showToastLog("onCLick","RTK初始化(2/5)...",true);
                // SIGNAL_READY20 对于CMCC来讲无效
//                showToastLog("initRTK","进入准备2",false);
//                if (rtk != null) {
//                    RTKNetworkServiceProvider.getInstance().setNetworkServiceCoordinateSystem(CoordinateSystem.CGCS2000, new CommonCallbacks.CompletionCallback() {
//                        @Override
//                        public void onResult(DJIError djiError) {
//                            if (djiError == null) {
//                                showToastLog("initRTK","设置坐标系CGCS2000...(2/5)", true);
//                                isCoordinateSystemSet = true;
//                            } else {
//                                showToastLog("initRTK","设置坐标系CGCS2000错误...(2/5)"+ djiError.getDescription(), true);
//                            }
//                        }
//                    });
//                } else {
//                    showToastLog("initRTK","rtk对象为空", true);
//                }
                break;
            }
            case R.id.btn_rtke3: {
                showToastLog("initRTK","进入准备3",false);
                // SIGNAL_READY20 取消 && isCoordinateSystemSet
                if (rtk != null && isNetowrkRTKSet) {
                    rtk.setRtkEnabled(true, new CommonCallbacks.CompletionCallback() { // 3
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                showToastLog("initRTK","成功使能RTK...(3/5)", true);
                                isNetowrkRTKEnable = true;
                            } else {
                                showToastLog("initRTK","使能RTK错误...(3/5)" + djiError.getDescription(), true);
                            }
                        }
                    });
                }
                break;
            }
            case R.id.btn_rtke4: { // SIGNAL_READY20 CMCC 在这里传递坐标系
                showToastLog("initRTK","进入准备4",false);
                if (rtk != null) {
                    RTKNetworkServiceProvider.getInstance().startNetworkService(CoordinateSystem.CGCS2000, new CommonCallbacks.CompletionCallback() {
                        @Override
                        public void onResult(DJIError djiError) {
                            if (djiError == null) {
                                showToastLog("initRTK","成功开启网络RTK服务,请等待收敛...(4/5)", true);
                                isNetowrkRTKStartSuccess = true;
                            } else {
                                showToastLog("initRTK","开启网络RTK服务失败...(4/5)"+djiError.getDescription(), false);
                            }
                        }
                    });
//                    RTKNetworkServiceProvider.getInstance().startNetworkService(new CommonCallbacks.CompletionCallback() {
//                        @Override
//                        public void onResult(DJIError djiError) {
//                            if (djiError == null) {
//                                showToastLog("initRTK","成功开启网络RTK服务,请等待...(4/5)", true);
//                                isNetowrkRTKStartSuccess = true;
//                            } else {
//                                showToastLog("initRTK","开启RTK服务失败...(4/5)"+djiError.getDescription(), false);
//                            }
//                        }
//                    });
                }
                break;
            }
            case R.id.tv_RTKConnect: {
                showToastLog("onClick", "监听到RTK按钮按下", false);
                // 启动RTK SIGNAL_READY2 //initRTKT();  SIGNAL_READY18已经废弃
                //  SIGNAL_READY18
                rtkTriggerSignal = rtkTriggerSignal + 1;
                if (rtkTriggerSignal % 2 == 0) { // 开始就是激活
                    dismissRTKBtn();
                } else { // 再次点击之后消失
                    ifRTKButtonTrigger();
                }
                break;
            }
            case R.id.btn_pause: { // SIGNAL_READY19 测试
                receiveDataFromOSDK();
                break;
            }
            case R.id.suffix_btn: {
                //SIGNAL_READY23
                //addMeidaSuffix();
                Log.e("suffix","按钮触发suffix");
                isSuffixReadySignal = 0; // 当按钮按下，先清零上一次标志位
                showSuffixDialog();
                break;
            }
            default:
                break;
        }
    }

    /** SIGNAL_READY7
     * 拍照测试按钮逻辑
     */
    private void captureAction(){
        final Camera camera = DemoApplication.getCameraInstance();
        takePhoto();
        // 这里不能按照FPVDemo那里面的切换FlatMode，这里会冲突 SIGNAL_READY8
    }

    /**SIGNAL_READY7
     * 拍照测试按钮逻辑2
     */
    private void takePhoto(){
        final boolean[] isRecording = {false};
        final Camera camera = DemoApplication.getCameraInstance();
        if (camera == null){
            return;
        }
        // SIGNAL_READY8  要独立出去，单独把isRecording赋值给局部变量，然后判断局部来拍照
        camera.setSystemStateCallback(new SystemState.Callback() {
            @Override
            public void onUpdate(@NonNull SystemState systemState) {
                if (systemState.isRecording()) {
                    isRecording[0] = true;
                }
                else {
                    isRecording[0] = false;
                }
            }
        });
        showToastLog("拍照测试按钮逻辑0","0  isrecording "+isRecording[0] ,false);
        if (isRecording[0]) {
            showToastLog("拍照测试按钮逻辑2","1  isrecording "+isRecording[0] ,false);
            photoHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    camera.startShootPhoto(djiError1 -> {
                    if (null == djiError1) {
                        showToastLog("拍照测试按钮逻辑3","成功拍摄照片" ,true);
                    } else {
                            showToastLog("拍照测试按钮逻辑4","拍照失败： "+djiError1.getDescription(),true);
                        }
                    });
                }
            }, 1500); // 延迟1500ms等待下载和相机拍照
        }
        try { // 等待执行
            Thread.sleep(300);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * SIGNAL_READY14
     * @return
     */
    private String  getCurrentData(){
        final StringBuffer pushInfo = new StringBuffer();
        // 加入速度 云台角度 焦距 RTK经纬高 姿态欧拉角  距离叶片距离 位移方向
        pushInfo.
                append(lastVelocityTotal).append(" ").append(gimbalAttitudePitch).append(" ").append(focalLength).append(" ").
                append(rtkLocationLon).append(" ").append(rtkLocationLat).append(" ").append(rtkAltitude).append(" ").
                append(currentVehicleAttitude.roll).append(" ").append(currentVehicleAttitude.pitch).append(" ").append(currentVehicleAttitude.yaw).append(" ").
                append(vehicleToBladeDistance).append(" ");
        // TODO  还剩下一个位移方向
        return (pushInfo.toString());
    }


    /**  SIGNAL_READY2 SIGNAL_READY25
     * 模式选择小页面
     */
    private void showModeSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_choose, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        RadioGroup radioGroup1 = dialogView.findViewById(R.id.group_file1); // 巡检面类型
        RadioGroup radioGroup2 = dialogView.findViewById(R.id.group_file6); // 录频选择
        RadioGroup radioGroup3 = dialogView.findViewById(R.id.group_file7); // EV选择
        builder.setTitle("选择模式")
                .setView(dialogView)
                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        switch (radioGroup1.getCheckedRadioButtonId()) {
                            case R.id.mode_1: {
                                modeSignal[0] = 0x20;
                                break;
                            }
                            case R.id.mode_2: {
                                modeSignal[0] = 0x21;
                                break;
                            }
                            case R.id.mode_3: {
                                modeSignal[0] = 0x22;
                                break;
                            }
                            case R.id.mode_5: {
                                modeSignal[0] = 0x23;
                                break;
                            }
                            case R.id.mode_6: {
                                modeSignal[0] = 0x24;
                                break;
                            }
                            case R.id.mode_8: {
                                modeSignal[0] = 0x25;
                                break;
                            }
                        }
                        switch (radioGroup2.getCheckedRadioButtonId()) {
                            case R.id.mode_9: {
                                modeSignal[1] = 0x30;
                                break;
                            }
                            case R.id.mode_10: {
                                modeSignal[1] = 0x31;
                                break;
                            }
                        }
                        switch (radioGroup3.getCheckedRadioButtonId()) {
                            case R.id.mode_11: {
                                modeSignal[2] = 0x40;
                                break;
                            }
                            case R.id.mode_12: {
                                modeSignal[2] = 0x41;
                                break;
                            }
                        }
                    }
                })
                .setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    /**
     * 发送指令给无人机 SIGNAL_READY2 SIGNAL_READY25
     */
    private void sendDataToOnboard(byte[] commandBytes) {
            showToastLog("sendDataToOnboard","字节1："+ commandBytes[0] + "2: "+commandBytes[1]+ "3: "+commandBytes[2], false);
            if(ModuleVerificationUtil.isFlightControllerAvailable() && null != flightController) {
                flightController.sendDataToOnboardSDKDevice(commandBytes, new CommonCallbacks.CompletionCallback() {
                    @Override
                    public void onResult(DJIError djiError) {
                        if (djiError == null) {
                            showToastLog("snedDataToOnboard", "成功发送指令！",true);
                            // SIGNAL_READY25 用完需要清理
                            Arrays.fill(modeSignal, (byte) 0x00);
                        } else {
                            showToastLog("sendDataToOnborad", "发送指令失败，错误码：" + djiError.getDescription(), true);
                        }
                    }
                });
            }

    }

    /** SIGNAL_READY5 SIGNAL_READY19 没有成功进来执行
     * 接收从OSDK过来的数据
     */
    private void receiveDataFromOSDK() {
        Log.e("页面A","recvOSDKData进入");
        if (ModuleVerificationUtil.isFlightControllerAvailable() && null != flightController) {
            // 这里进来了 就是回调没有执行成功
            Log.e("页面A","recvOSDKData进入22222");
            flightController.setOnboardSDKDeviceDataCallback(new FlightController.OnboardSDKDeviceDataCallback() {
                @Override
                public void onReceive(byte[] bytes) {
                    showToastLog("接收OSDK数据", "1：" + bytes[0]+":2:"+bytes[1] + ":3:"+bytes[2]+":4:"+bytes[3], true);
                    // 发送过来的数据是变成了int类型的
                    int recvOSDkByteTmp1 = byteToInt(bytes[0]);
                    int recvOSDkByteTmp2 = byteToInt(bytes[1]);
                    int recvOSDkByteTmp3 = byteToInt(bytes[2]);
                    int recvOSDkByteTmp4 = byteToInt(bytes[3]);
                    // 转float
                    finalForceFloatX = recvOSDkByteTmp1 / OSDK_FLOAT_DATA_INT_MAGNIFICATION; // 叶片距离
                    finalForceFloatY = recvOSDkByteTmp2 / OSDK_FLOAT_DATA_INT_MAGNIFICATION; // 避障提示
                    finalForceFloatZ = recvOSDkByteTmp3 / OSDK_FLOAT_DATA_INT_MAGNIFICATION;
                    finalForceFloatW = recvOSDkByteTmp4 / OSDK_FLOAT_DATA_INT_MAGNIFICATION;
                    //showToastLog("接收OSDK数据 finalForceFloatX", "1：" + finalForceFloatX+":2:"+finalForceFloatY + ":3:"+finalForceFloatZ+":4:"+finalForceFloatW, false);
                }
            });
        } else {
            showToastLog("接收OSDK数据","飞控连接失败...",true);
        }
    }

    /** SIGNAL_READY5
     * 字节转Int数据
     * @param bytes
     * @return
     */
    public static int byteToInt(byte bytes) {
        int x = 0;
        x <<= 8;
        int b = bytes & 0xFF;
        x |= b;
        return x;
    }

    @Override
    protected void onResume() {
        super.onResume();
        showToastLog("页面A onResume","onResume开始", false);
        fpvWidget.destroy();
        mMediaManager.exitMediaDownloading();
        cameraGlobal = DemoApplication.getCameraInstance();
        initFlightController();
        // 取消频繁更新 可能影响媒体页面 SIGNAL_READY21
        isThreadRunning = true;
        runThreadHandler();
        Log.e("onResume","isThreadRunning"+isThreadRunning);
        // SIGNAL_READY18
        //listenRTKStatus();

        final boolean[] isInPlayBack = {false};
        // 先尝试退出回放模式
        cameraGlobal.exitPlayback(new CommonCallbacks.CompletionCallback() {
            @Override
            public void onResult(DJIError djiError) {
                if (djiError == null) {
                    try { // 尝试等待完成
                        Thread.sleep(500);
                        cameraGlobal.setSystemStateCallback(new SystemState.Callback() {
                            @Override
                            public void onUpdate(@NonNull SystemState systemState) {
                                try { // 同理等待
                                    Thread.sleep(100);
                                    if (systemState.isInPlayback()) {
                                        isInPlayBack[0] = true;
                                    } else {
                                        isInPlayBack[0] = false;
                                    }
                                    // 这个执行最慢
                                    showToastLog("onResume","isInPlayBack状态：" + isInPlayBack[0], false);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                        switchCameraDisplaySourceTest(1, (byte) 0x22);
                        fpvWidget.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    showToastLog("onResume","没有成功退出播放模式："+djiError.getDescription(),true);
                }
            }
        });
        // 一直while等着 直到退出回放模式，修改一次模式和更新
        fpvWidgetHandler.post(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    if (!isInPlayBack[0]) {
                        break;
                    }
                }
                showToastLog("页面A onResume","onResume循环退出", false);
                }
        });
        try { // 必须保证1000以上，不然无法更新
            Thread.sleep(800);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        switchCameraDisplaySourceTest(1, (byte) 0x22);
        fpvWidget.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));  // onSurfaceTextureAvailable实现视频流


        showToastLog("页面A onResume","onResume结束", false);
    }

    // SIGNAL_READY21 Handler不能释放
    @Override
    protected void onPause() {
        super.onPause();
        Log.e("页面onPause","暂停线程");
        // 停止更新线程和 UI 更新 SIGNAL_READY21
        isThreadRunning = false;
        if (updateMSDKDataThread != null) {
            updateMSDKDataThread.quitSafely();
            updateMSDKDataThread = null;
        }
        if (updateRTKThread != null) {
            updateRTKThread.quitSafely();
            updateRTKThread = null;
        }
        // SIGNAL_READY24
        if (listenSuffixThread != null) {
            listenSuffixThread.quitSafely();
            listenSuffixThread = null;
        }
    }
}

