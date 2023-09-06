package com.dji.mediaManagerDemo;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import dji.common.camera.CameraVideoStreamSource;
import dji.common.camera.SettingsDefinitions;
import dji.common.camera.SystemState;
import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;
import dji.keysdk.ProductKey;
import dji.sdk.camera.Camera;
import dji.sdk.camera.Lens;
import dji.ux.widget.FPVWidget;

public class PureZoomActivity extends AppCompatActivity implements View.OnClickListener{

    private FPVWidget fpvWidgetPure;
    private Button mPureBackBtn;
    // TextView
    private TextView mPureStatus1Tv;
    private TextView mPureStatus2Tv;
    private TextView mPureStatus3Tv;
    private TextView mPureStatus4Tv;
    private TextView mPureStatus5Tv;
    private TextView mPureStatus6Tv;
    // Data
    protected double lastVelocityTotal = 0.0;
    protected Integer focalLength = 0;
    // SIGNAL_READY10 添加录像拍照
    private Button  mRecordingPhotoBtn;
    private Handler photoHandler;

    private void showToastLog(final String tag, final String description, boolean isRunToast) {
        Log.e("页面P: " + tag, description);
        if (isRunToast) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Toast.makeText(PureZoomActivity.this, description, Toast.LENGTH_SHORT).show();  // 显示2s  long时3.5s
                }
            });
        }
    }

    private void djiErrorJudeg(DJIError djiError, final String tag, final String description, boolean isShowToast) {
        if (djiError == null) {
            showToastLog(tag, description, isShowToast);
        }
        else {
            showToastLog(tag, description + ",错误代码为: " + djiError.getDescription(), isShowToast);
        }
    }

    /**
     * 切换相机显示来源 测试函数 SIGNAL_READY3
     * @param mode 选择测试API
     */
    private void switchCameraDisplaySourceTest(Integer mode, byte modeByte) {
        Camera camera = DemoApplication.getCameraInstance();
        Lens cameraLens2 = DemoApplication.getCameraInstance().getLens(2);
        Lens cameraLens0 = DemoApplication.getCameraInstance().getLens(0);
        Lens cameraLens1 = DemoApplication.getCameraInstance().getLens(1);
        //showToastLog("2切换显示模式","镜头2："+cameraLens2.getDisplayName()+"镜头1"+cameraLens1.getDisplayName()+"镜头0："+cameraLens0.getDisplayName(),false);
        // 这个是变焦还是光学还是红外？
        if (modeByte == 0x21 && mode == 0) { // 变焦  ZOOM正常
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.ZOOM;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "2切换显示模式0x21", "切换变焦模式", false);

                }
            });
        } else if (modeByte == 0x22 && mode == 1) { // 广角
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.WIDE;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "2切换显示模式0x22", "切换广角模式", false);
                }
            });
        } else if (modeByte == 0x23 && mode ==2) { // 红外
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.INFRARED_THERMAL;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "2切换显示模式0x23", "切换红外模式", false);
                }
            });
        } else if (modeByte == 0x24 && mode == 3) { // 画中画测试
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.INFRARED_THERMAL;
            SettingsDefinitions.DisplayMode displayMode = SettingsDefinitions.DisplayMode.PIP; // MSX
            cameraLens2.setDisplayMode(displayMode, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "2切换显示模式0x24", "切换为分屏模式", false);
                    if (djiError == null) {
                        SettingsDefinitions.PIPPosition pipPosition = SettingsDefinitions.PIPPosition.ALIGN; // SIDE_BY_SIDE 其他的
                        cameraLens2.setPIPPosition(pipPosition, new CommonCallbacks.CompletionCallback() {
                            @Override
                            public void onResult(DJIError djiError) {
                                djiErrorJudeg(djiError, "2切换显示模式0x24", "画中画设置", false);
                            }
                        });
                    }
                }
            });
        } else {
            CameraVideoStreamSource cameraVideoStreamSource = CameraVideoStreamSource.DEFAULT;
            camera.setCameraVideoStreamSource(cameraVideoStreamSource, new CommonCallbacks.CompletionCallback() {
                @Override
                public void onResult(DJIError djiError) {
                    djiErrorJudeg(djiError, "2切换0x23", "测试设置", false);
                }
            });
        }
    }

    private void initUI(Bundle savedInstanceState) {
//        mPureStatus1Tv = findViewById(R.id.tv_status1_pure);
//        mPureStatus2Tv = findViewById(R.id.tv_status2_pure);
//        mPureStatus3Tv = findViewById(R.id.tv_status3_pure);
//        mPureStatus4Tv = findViewById(R.id.tv_status4_pure);
//        mPureStatus5Tv = findViewById(R.id.tv_status5_pure);
//        mPureStatus6Tv = findViewById(R.id.tv_status6_pure);
//        Bundle bundle = getIntent().getExtras();
//        lastVelocityTotal = bundle.getDouble("速度");
//        focalLength = bundle.getInt("焦距");
//        mPureStatus1Tv.setText(String.valueOf(lastVelocityTotal));
//        mPureStatus2Tv.setText(String.valueOf(focalLength));

    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pure_zoom_layout);
        fpvWidgetPure = findViewById(R.id.fpv_widget_pure);
        mPureBackBtn = findViewById(R.id.pure_zoom_back);
        mPureBackBtn.setOnClickListener(this);
        switchCameraDisplaySourceTest(0, (byte) 0x21);
        // SIGNAL_READY10 录像同时拍照测试
        mRecordingPhotoBtn = (Button) findViewById(R.id.btn_recording_photo_pure);
        mRecordingPhotoBtn.setOnClickListener(this);
        photoHandler = new Handler();

        initUI(savedInstanceState);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.pure_zoom_back) {
            this.finish();
        }
        // SIGNAL_READY10
        if (v.getId() == R.id.btn_recording_photo_pure) {
            captureAction();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fpvWidgetPure.updateWidget(ProductKey.create(ProductKey.MODEL_NAME));
        switchCameraDisplaySourceTest(0, (byte) 0x21);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /** SIGNAL_READY10
     * 拍照测试按钮逻辑
     */
    private void captureAction(){
        final Camera camera = DemoApplication.getCameraInstance();
        takePhoto();
    }

    /**SIGNAL_READY10
     * 拍照测试按钮逻辑2
     */
    private void takePhoto(){
        final boolean[] isRecording = {false};
        final Camera camera = DemoApplication.getCameraInstance();
        if (camera == null){
            return;
        }
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
        showToastLog("拍照测试按钮逻辑00","0  isrecording "+isRecording[0] ,false);
        if (isRecording[0]) {
            showToastLog("拍照测试按钮逻辑20","1  isrecording "+isRecording[0] ,false);
            photoHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    camera.startShootPhoto(djiError1 -> {
                        if (null == djiError1) {
                            showToastLog("拍照测试按钮逻辑30","成功拍摄照片" ,true);
                            // 看在这里加切换还是哪里 SIGNAL_READY14的FIXME
                        } else {
                            showToastLog("拍照测试按钮逻辑40","失败： "+djiError1.getDescription(),true);
                        }
                    });
                }
            }, 1500); // 延迟1500ms等待下载和相机拍照
        }
        try { // 等待执行
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

    }
}