package com.dji.mediaManagerDemo;

import android.app.ProgressDialog;
import android.content.DialogInterface;
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

import java.util.Random;

import dji.common.error.DJIError;
import dji.common.flightcontroller.FlightControllerState;
import dji.common.util.CommonCallbacks;
import dji.keysdk.ProductKey;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.products.Aircraft;
import dji.ux.widget.FPVWidget;
import dji.ux.widget.controls.CameraControlsWidget;

// uxsdk的UI界面
public class DefaultLayoutActivity extends AppCompatActivity implements View.OnClickListener{

    private Button mMediaManagerBtn;
    private FPVWidget fpvWidget;
    // 测试用
    private CameraControlsWidget cameraControlsWidget;
    // 各个按钮
    private Button mMOPBtn, mTakeoffBtn, mPauseBtn, mModeBtn,
            mBlade1Btn, mBlade2Btn, mBlade3Btn;
    // 文本显示
    private TextView mStatus1Tv;
    private TextView mStatus2Tv;
    private TextView mStatus3Tv;
    private TextView mStatus4Tv;
    private TextView mStatus5Tv;
    private TextView mStatus6Tv;
    // MOP
    private static final byte TRIGGER_HIDE_BUTTON = (byte) 0x11; // 接收到这个字节，触发三个blade隐藏按钮
    private byte[] uploadDataBuf = new byte[10]; // MSDK发送数据的缓存变量
    private byte[] downloadDataBuf = new byte[10]; // MSDK接收数据的缓存变量

    // 注意需要导入一下V4 完整代码里面的ModuleVerificationUtil.java这个工具类
    private FlightController flightController = null;
    // 要发送给无人机的指令
    byte[] byteToOSDK = new byte[1];
    // 可以直接从MSDK拿到的数据:速度
    protected double lastVelocityX = 0.0;
    protected double lastVelocityY = 0.0;
    protected double lastVelocityZ = 0.0;
    protected double lastVelocityTotal = 0.0;
    // 模式选择按钮  TODO 这里应该用状态机
    protected byte modeSignal = 0x00; // 0不执行，阻塞等待；1双面；2四面
    // TODO 接收数据的线程
    HandlerThread updateMSDKDataThread;
    Handler updateMSDKDataHandler;
    private static final int UPDATE_INTERVAL = 500; // 500ms 更新一次数据


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_default_layout);
        initUI();
        showToastLog("onCreate", "initUI完成", false);
    }

    @Override
    protected void onDestroy() {
        // 取消线程
        if (updateMSDKDataHandler != null && updateMSDKDataThread != null) {
            updateMSDKDataHandler.removeCallbacksAndMessages(null);
            updateMSDKDataThread.quitSafely();
        }
        super.onDestroy();
    }

    void initUI() {
        // 初始化飞控类和onBoard类
        initFlightController();
        // 初始化按钮
        mMOPBtn = (Button) findViewById(R.id.btn_mop);
        mTakeoffBtn = (Button) findViewById(R.id.btn_takeoff);
        mPauseBtn = (Button) findViewById(R.id.btn_pause);
        mModeBtn = (Button) findViewById(R.id.btn_mode);
        mMediaManagerBtn = (Button)findViewById(R.id.btn_mediaManager);
        fpvWidget = findViewById(R.id.fpv_widget);  // UI界面的主界面
        // CameraControlsWidget这个UI控件，封装了FPVDemo中类似camera类型拍照的功能
        // 照片和视频拍摄、录制完之后会进入到listView
        // 隐藏按钮
        mBlade1Btn = (Button) findViewById(R.id.btn_blade1);
        mBlade1Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见
        mBlade2Btn = (Button) findViewById(R.id.btn_blade2);
        mBlade2Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见
        mBlade3Btn = (Button) findViewById(R.id.btn_blade3);
        mBlade3Btn.setVisibility(View.GONE); // 初始不可见 当满足mop按钮的触发条件可见
        // 设置按钮监听
        mMOPBtn.setOnClickListener(this);
        mTakeoffBtn.setOnClickListener(this);
        mPauseBtn.setOnClickListener(this);
        mModeBtn.setOnClickListener(this);
        mBlade1Btn.setOnClickListener(this);
        mBlade2Btn.setOnClickListener(this);
        mBlade3Btn.setOnClickListener(this);
        mMediaManagerBtn.setOnClickListener(this); // 切换页面按钮 监听事件
        // 初始化文本
        mStatus1Tv = findViewById(R.id.tv_status1);
        mStatus2Tv = findViewById(R.id.tv_status2);
        mStatus3Tv = findViewById(R.id.tv_status3);
        mStatus4Tv = findViewById(R.id.tv_status4);
        mStatus5Tv = findViewById(R.id.tv_status5);
        mStatus6Tv = findViewById(R.id.tv_status6);
        // TODO 初始化就开始更新MSDK部分的数据，需要用到线程
        updateMSDKDataThread  = new HandlerThread("uploadMSDKData");
        updateMSDKDataThread.start();
        updateMSDKDataHandler = new Handler(updateMSDKDataThread.getLooper());
        updateMSDKDataHandler.postDelayed(updateRunnable, UPDATE_INTERVAL); // 500ms
    }

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateDataFromMSDK();
            updateMSDKDataHandler.postDelayed(this, UPDATE_INTERVAL); // 重新安排下一次执行
        }
    };

    /**
     * 只负责初始化飞控类和onBoard类
     */
    private void initFlightController() {
        BaseProduct product = DemoApplication.getProductInstance();
        // 初始化product类和FlightController类
        if (product == null || !product.isConnected()) {
            // ToastUtils看情况更换为我的showToast
            showToastLog("initFC","飞机已断开连接", false);
        } else {
            if (product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
                showToastLog("initFC","成功连接飞控", false);
                if(flightController.isOnboardSDKDeviceAvailable()) {
                    showToastLog("initFC","成功检测到机载电脑", true);
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


    /** TODO onCreate开一个线程再线程里面去搞
     * 实时更新MSDK能拿到的数据
     */
    private void updateDataFromMSDK() {
        if (flightController != null) {
            // 通过回调获得飞控类里面的数据  比如海拔经纬 飞行模式等
            flightController.setStateCallback(new FlightControllerState.Callback() {
                @Override
                public void onUpdate(@NonNull FlightControllerState flightControllerState) {
                    lastVelocityX = flightControllerState.getVelocityX();
                    lastVelocityY = flightControllerState.getVelocityY();
                    lastVelocityZ = flightControllerState.getVelocityZ();
                    lastVelocityTotal = Math.sqrt(lastVelocityX * lastVelocityX + lastVelocityY * lastVelocityY + lastVelocityZ * lastVelocityZ);
                    // 使用Handler在主线程中更新UI
                    runOnUiThread(() -> {
                        String testStr0 = String.valueOf(lastVelocityTotal);
                        mStatus1Tv.setText(testStr0);

                        // 随机参数测试
                        Random random = new Random();
                        int intRandomNumber = random.nextInt(50);
                        String testStr1 = String.valueOf(intRandomNumber);
                        mStatus2Tv.setText(testStr1);
                    });
                }
            });
        }
        else {
            Log.e("页面A updateMKeyData", "飞控类实例不存在，断连接");
        }
    }

    /**
     * 主页面更新MOP传输过来的数据
     * @param b1 状态数据1
     * @param b2 数据2
     * @param b3 数据3
     */
    private void updateDataFromOSDK(int b1, int b2, int b3) {
        mStatus1Tv.setText(b1);
        mStatus2Tv.setText(b2);
        mStatus3Tv.setText(b3);
    }

    /** TODO 逻辑需要设计
     * 判断是否触发隐藏按钮的显示
     */
    private void ifHidenButtonTrigger() {
        if (uploadDataBuf[0] == TRIGGER_HIDE_BUTTON) {
            findViewById(R.id.btn_blade1).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_blade2).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_blade3).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.btn_blade1).setVisibility(View.GONE);
            findViewById(R.id.btn_blade2).setVisibility(View.GONE);
            findViewById(R.id.btn_blade3).setVisibility(View.GONE);
        }
    }


    @Override
    public void onClick(View v) {
        if(v.getId() == R.id.btn_mediaManager){
            showToastLog("onClick","监听到媒体流页面按下",false);
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        }
        if(v.getId() == R.id.btn_mop){
            showToastLog("onClick","监听到媒体流页面按下",false);
            Intent intent = new Intent(this, MOPSampleActivity.class);
            startActivity(intent);
        }
        if (v.getId() == R.id.btn_takeoff) {
            showToastLog("onClick","监听到起飞按钮按下",false);
            byteToOSDK[0] = 0x01;
            sendDataToOnboard(byteToOSDK);
        }
        if (v.getId() == R.id.btn_mode) {
            showToastLog("onClick","监听到模式选择按钮按下",false);
            // TODO 这里选择之后要再次按下才会发送指令
            showModeSelectionDialog();
            byte[] modeByteToOSDK = {modeSignal};
            sendDataToOnboard(modeByteToOSDK);
        }
    }

    /**
     * 模式选择小页面
     */
    private void showModeSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_choose, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("选择模式")
                .setView(dialogView)
                .setPositiveButton("确认", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        RadioGroup group1 = dialogView.findViewById(R.id.group_file1);
                        switch (group1.getCheckedRadioButtonId()) {
                            case R.id.mode_1:
                                // 模式 1 的操作
                                modeSignal = 0x20;
                                break;
                            case R.id.mode_2:
                                // 模式 2 的操作
                                modeSignal = 0x21;
                                break;
                            case R.id.mode_3:
                                // 模式 3 的操作
                                modeSignal = 0x22;
                                break;
                        }
                    }
                })
                .setNegativeButton("取消", null);
        AlertDialog dialog = builder.create();
        dialog.show();
        showToastLog("模式选择页面","模式选择的状态是："+modeSignal, false);
    }

    /**
     * 发送指令给无人机
     */
    private void sendDataToOnboard(byte[] commandBytes) {
        // 看一下是什么
        showToastLog("sendDataToOnboard","字节是："+ commandBytes[0], false);
        if(ModuleVerificationUtil.isFlightControllerAvailable() && null != flightController) {
            flightController.sendDataToOnboardSDKDevice(commandBytes, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    Log.e("页面A sendDataToOnboard", "发送指令:"+commandBytes[0]);
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 需要实时更新
        fpvWidget.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));  // onSurfaceTextureAvailable实现视频流
        showToastLog("onResume","onResume：", false);
        //ifHidenButtonTrigger();

    }

}

