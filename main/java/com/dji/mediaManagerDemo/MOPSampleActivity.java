package com.dji.mediaManagerDemo;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import dji.log.DJILog;
import dji.mop.common.Pipeline;
import dji.mop.common.PipelineDeviceType;
import dji.mop.common.TransmissionControlType;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.payload.Payload;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class MOPSampleActivity extends AppCompatActivity {

    private EditText keyEt;
    private RecyclerView pipelineRc;
    private RadioGroup typeGroup;
    private AppCompatCheckBox safeBox;
    private PipelineAdapter adapter;
    // 需要更新的数据 需要在多个activity之间传递
    private DataUpdater dataUpdater;
    private AppCompatCheckBox logBox;
    private TextView mMopBackBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e("页面B", "成功进入MOP页面");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mopsample);
        keyEt = findViewById(R.id.et_channel_key);
        pipelineRc = findViewById(R.id.rc_pipeline);
        typeGroup = findViewById(R.id.rg_mop_type);
        safeBox = findViewById(R.id.cb_reliable);
        logBox = findViewById(R.id.cb_log);
        // 返回
        mMopBackBtn = findViewById(R.id.btn_mop_back);
        mMopBackBtn.setOnClickListener(v -> {
           this.finish();
        });

        adapter = new PipelineAdapter(this, new ArrayList<>(), dataUpdater);
        // 设置 RecyclerView 的布局管理器和数据
        pipelineRc.setLayoutManager(new LinearLayoutManager(this));
        pipelineRc.setAdapter(adapter);
        pipelineRc.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));

        // 是否保存log文件
        getFlightController().savePipelinesLog(logBox.isChecked());

        logBox.setOnCheckedChangeListener((buttonView, isChecked) -> getFlightController().savePipelinesLog(isChecked));
        Log.e("页面B", "onCreate logBox设置完成");
        // 初始化以及读取用户选择的参数，要和O/PSDK那边对应上
        // 这个按钮式connect按钮，需要同时开启通道和Onboard那边才能连接成功
        // 连接不成功会执行下面的tip那里
        findViewById(R.id.btn_create).setOnClickListener(v -> {
            Log.e("页面B", "onCreate 开始设置连接按钮 btn_create");
            // 用户输入管道id，手动输入需要和psdk 以及osdk的MOP接口那边对应上
            // linux那边可靠传输是49155  不可靠是49144
            String key = keyEt.getText().toString().trim();
            if (TextUtils.isEmpty(key)) {
                Toast.makeText(v.getContext(), "请输入管道id", Toast.LENGTH_SHORT).show();
                return;
            }
            // 选择psdk还是osdk
            PipelineDeviceType type = getType(typeGroup.getCheckedRadioButtonId());
            // 通信种类
            TransmissionControlType transferType = safeBox.isChecked() ? TransmissionControlType.STABLE : TransmissionControlType.PUSH;
            Log.e("页面B", "onCreate 通信种类设置完成进入选择");
            switch (type) {
                case PAYLOAD:
                    Payload payload = getPayload();
                    if (payload == null) {
                        Toast.makeText(MOPSampleActivity.this, "当前没有payload设备", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    payload.getPipelines().connect(MOPTools.getInt(key, 0), transferType, error -> {
                        if (error == null) {
                            Pipeline p = payload.getPipelines().getPipeline(MOPTools.getInt(key, 0));
                            if (p != null) {
                                DJILog.d("MopActivity", "connect success: " + p.toString());
                                runOnUiThread(() -> adapter.addItem(p));
                            }
                        }
                        String tip = error == null ? "成功" : error.toString();
                        runOnUiThread(() -> Toast.makeText(MOPSampleActivity.this, "connect result:" + tip, Toast.LENGTH_SHORT).show());
                    });
                    break;
                case ON_BOARD: // 根据type选择进入对应的通信目标
                    Log.e("页面B", "成功选择onboard选项");
                    // 先获取flightController实例
                    FlightController flightController = getFlightController();
                    if (flightController == null) {
                        Toast.makeText(MOPSampleActivity.this, "当前没有外部设备", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 获取管线束,并且开启连接connect，也可以分开就是单独定义管线束变量：
                    // flightController.getPipelines()是一个Map<Integer, Pipeline>类型的数据，是Pipelines的接口
                    // connect三个参数，第一个是id(string转换成int，没有就默认是0），第二个是传递类型，第三个是回调函数这里写成匿名回调方式
                    // 这里开启MOP通道连接
                    Log.e("页面B", "onboard 尝试获取管线束piprlines");
                    flightController.getPipelines().connect(MOPTools.getInt(key, 0), transferType, error -> {
                        if (error == null) {
                            // 跑在线程里面，就是循环执行
                            // adapter就是PipelineAdapter，右程序
                            // addItem：从Pipelines返回一个具体的Pipeline接口
                            // 把通道数值行返回给逻辑函数
                            Log.e("页面B", "onboard 获取管线束piprlines成功 尝试进入pipelineadapter");
                            runOnUiThread(() -> adapter.addItem(flightController.getPipelines().getPipeline(MOPTools.getInt(key, 0))));
                        }
                        String tip = error == null ? "成功" : error.toString();
                        runOnUiThread(() -> Toast.makeText(MOPSampleActivity.this, "连接结果:" + tip, Toast.LENGTH_SHORT).show());
                    });
                    break;
            }
        });
    }

    private Payload getPayload() {
        if (DJISDKManager.getInstance().getProduct() == null) {
            return null;
        }
        return DJISDKManager.getInstance().getProduct().getPayload();
    }

    private FlightController getFlightController() {
        BaseProduct product = DJISDKManager.getInstance().getProduct();
        if (product != null && product instanceof Aircraft) {
            return ((Aircraft) product).getFlightController();
        }
        return null;
    }

    private PipelineDeviceType getType(int checkedRadioButtonId) {
        switch (checkedRadioButtonId) {
            case R.id.rb_on_board:
                return PipelineDeviceType.ON_BOARD;
            case R.id.rb_payload:
                return PipelineDeviceType.PAYLOAD;
            default:
                return PipelineDeviceType.PAYLOAD;

        }
    }

}
