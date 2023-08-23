package com.dji.mediaManagerDemo;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import dji.common.util.CommonCallbacks;
import dji.log.DJILog;
import dji.mop.common.Pipeline;
import dji.mop.common.PipelineError;
import dji.sdk.base.BaseProduct;
import dji.sdk.flightcontroller.FlightController;
import dji.sdk.payload.Payload;
import dji.sdk.products.Aircraft;
import dji.sdk.sdkmanager.DJISDKManager;

public class PipelineAdapter extends RecyclerView.Adapter<PipelineAdapter.ViewHolder> {
    private List<Pipeline> data;
    private LayoutInflater mInflater;
    private static final int OSDK_FORCE_ARRAY_FLOAT_LENGTH = 12; // float 4 bytes
    private static final int OSDK_FORCE_ARRAY_INT_LENGTH = 12;  // int = 4 bytes
    private static final int READ_DATA_FROM_OSDK_FREQ = 20;  // 读取OSDK数据的频率
    private static final float OSDK_FLOAT_DATA_INT_MAGNIFICATION = 1000;  // float数据放大为int数据的放大倍数

    private static final int PACK_WIND_HEADER_SIZE = 3;
    // 数据更新对象
    private DataUpdater dataUpdater;
    private OnDisconnectListener listener = new OnDisconnectListener() {
        @Override
        public void onDisconnect(Pipeline d) {
            if (data.contains(d)) {
                data.remove(d);
                notifyItemRemoved(data.indexOf(d));
            }

        }
    };

    // 从主函数传进来开始执行构造
    public PipelineAdapter(Context context, List<Pipeline> data, DataUpdater dataUpdater) {
        this.mInflater = LayoutInflater.from(context);
        this.data = data;
        this.dataUpdater = dataUpdater;
    }

    // 在主函数runOnUiThread中传递进来具体的单个Pipeline接口实例
    public void addItem(Pipeline action) {
        if (action == null || data == null || data.contains(action)) {
            return;
        }
        data.add(action);
        notifyItemInserted(getItemCount() - 1);
    }

    public List<Pipeline> getData() {
        return data;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // 引入小mop的itemView
        return new ViewHolder(mInflater.inflate(R.layout.item_pipeline, parent, false));
    }

    /**
     * 每一个mop itemView的初始化设置 和 设置监听事件  position是索引序号
      */
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        holder.setData(data.get(position)); // 取出选中的管道序号位置
        holder.setListener(listener);
        // 绑定数据到 ViewHolder 中，并传递 DataUpdater 实例
        holder.bindData(dataUpdater);
    }

    @Override
    public int getItemCount() {
        return data == null ? 0 : data.size();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onDetachedFromRecyclerView(recyclerView);
        for (int i = 0; i < getItemCount(); i++) {
            ViewHolder viewholder = (ViewHolder) recyclerView.findViewHolderForAdapterPosition(i);
            if (viewholder != null) {
                viewholder.destroy();
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.destroy();
    }

    public static String getTime() {
        String patten = "yyyy-MM-dd HH:mm:ss.SSS";
        SimpleDateFormat format = new SimpleDateFormat(patten);
        String dateFormatStr = format.format(new Date());
        return dateFormatStr;
    }

    //    @Override
    public void onDisconnect(Pipeline data) {
        this.data.remove(data);
        notifyItemRemoved(this.data.indexOf(data));
    }

    /**
     * itemView viewHolder的实现类  这里是把他直接放到recycle里面来实现
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView nameTv, downloadTv, uploadTv;
        private TextView downloadLogTv, uploadLogTv, disconnectTv, filenameTv;
        private TextView finalForceTv, finalForce2Tv, finalForce3Tv;
        private Switch autoDownloadSwitch;

        HandlerThread uploadThread;
        HandlerThread downloadThread;
        Handler uploadHandler;
        Handler downloadHandler;
        private Payload payload;
        private FlightController flightController;
        private WeakReference<OnDisconnectListener> listenerWeakReference;

        private boolean uploading;
        private boolean downloading;

        private String uploadFileInfoLog;
        private String uploadProgress;
        private String uploadResult;

        private String downloadFileInfoLog;
        private String downloadProgress;
        private String downloadResult;
        private int downloadPackCount;
        private int downloadSize;

        // 要实现更新的数据 和 线程互传数据类
        private String downloadFinalForceText1, downloadFinalForceText2, downloadFinalForceText3, downloadFinalForceText4;
        private DataUpdater dataUpdater;

        private String uploadCommandText1 = "7"; // 尝试发送简单的字符数据，在OSDK接收后再编码

        private int downloadSuccessCount;
        private int downloadCount;

        private int uploadSuccessCount;
        private int uploadCount;
        private boolean isUpdatingData  = false;

        private String uploadFileName = "mop.log";
        private OnEventListener listener = new OnEventListener() {
            @Override
            public void onTipEvent(MOPCmdHelper.TipEvent event) {
                handlerTipEvent(event);
            }

            @Override
            public void onFileInfoEvent(MOPCmdHelper.FileInfo event) {
                onEvent3BackgroundThread(event);
            }
        };

        public ViewHolder(View itemView) {
            super(itemView);
            nameTv = itemView.findViewById(R.id.tv_name);
            downloadTv = itemView.findViewById(R.id.tv_download);
            uploadTv = itemView.findViewById(R.id.tv_upload);
            downloadLogTv = itemView.findViewById(R.id.tv_download_log);
            uploadLogTv = itemView.findViewById(R.id.tv_upload_log);
            disconnectTv = itemView.findViewById(R.id.tv_disconnect);
            filenameTv = itemView.findViewById(R.id.et_file_name);
            autoDownloadSwitch = itemView.findViewById(R.id.switch_auto_download);
            finalForceTv = itemView.findViewById(R.id.final_force);
            finalForce2Tv = itemView.findViewById(R.id.final_force2);
            finalForce3Tv = itemView.findViewById(R.id.final_force3);

            dataUpdater = new DataUpdater();

            BaseProduct product = DJISDKManager.getInstance().getProduct();
            if (product != null && product instanceof Aircraft) {
                flightController = ((Aircraft) product).getFlightController();
                payload = product.getPayload();
            }
        }

        /**
         * 在这里设置其他基本信息的数据，例如：setText("基本信息");
         * @param dataUpdater
         */
        public void bindData(DataUpdater dataUpdater) {
            // 将 DataUpdater 实例传递给 bindData 方法
            this.dataUpdater = dataUpdater;
            // 在这里获取 DataUpdater 类的数据并更新到对应的 TextView 上
            finalForceTv.setText(String.valueOf(dataUpdater.getB1()));
            finalForce2Tv.setText(String.valueOf(dataUpdater.getB2()));
            finalForce3Tv.setText(String.valueOf(dataUpdater.getB3()));
        }

        public void setData(Pipeline data) {
            // 销毁之前的
            destroy();
            // 创建上传和下载的进程，不要开一次就无了
            uploadThread = new HandlerThread("upload");
            downloadThread = new HandlerThread("download");
            uploadThread.start();
            downloadThread.start();
            uploadHandler = new Handler(uploadThread.getLooper());
            downloadHandler = new Handler(downloadThread.getLooper());
            View.OnClickListener listener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    switch (v.getId()) {
                        // 如果点击upload按钮
                        case R.id.tv_upload:
                            // 这里是命令选项，如果是wind才会启动和OSDK风速的传输内容
                            if (filenameTv.getText().toString().equals("wind")){
                                DJILog.logWriteE("PipelineAdapter", "----------Ready to Send To OSDK: "+ filenameTv.getText().toString());
                                uploadCommandReliable(data, filenameTv.getText().toString());
                            }
                            else{
                                // 跳转upload界面
                                View root = LayoutInflater.from(itemView.getContext()).inflate(R.layout.dialog_mop_upload, null, false);
                                new AlertDialog.Builder(itemView.getContext())
                                        .setTitle("Select File Size")
                                        .setView(root)
                                        .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                dialog.dismiss();
                                                RadioGroup group = root.findViewById(R.id.group_file2);
                                                switch (group.getCheckedRadioButtonId()) {
                                                    case R.id.rb_1:
                                                        uploadFileName = "mop.log";
                                                        break;
                                                    case R.id.rb_2:
                                                        uploadFileName = "mop.jpeg";
                                                        break;
                                                    case R.id.rb_3:
                                                        uploadFileName = "mop.mp4";
                                                        break;
                                                }
                                                uploadHandler.post(() -> {
                                                    resetUploadInfo();
                                                    uploadFile(data);
                                                });
                                            }
                                        })
                                        .show();
                            }
                            break;
                        case R.id.tv_download: // 按下下载按钮，激活数据更新函数，开启多activity通信
                            downloadHandler.post(() -> {
                                resetDownInfo();
                                // 这里是命令选项，如果是wind才会启动和OSDK风速的传输内容
                                if (filenameTv.getText().toString().equals("wind")){
                                    DJILog.logWriteE("PipelineAdapter", "----------Ready to Start: "+ filenameTv.getText().toString());
                                    downloadForceUnreliable(data, filenameTv.getText().toString());
                                    // 开始数据更新任务，上面download到数据后执行
                                    dataUpdater.startUpdating();
                                }
                                else {
                                    // 在这个地方把目标文件名字传给download函数
                                    DJILog.logWriteE("PipelineAdapter", "-------------------Signa222222");
                                    downloadFile(data, filenameTv.getText().toString());
                                }
                            });
                            break;
                        case R.id.tv_disconnect:
                            destroy();
                            disconnect(data);
                            dataUpdater.stopUpdating();
                            break;
                    }
                }
            };

            String title = String.format("Id=%d, MOPType = %s, trans_type=%s", data.getId(), data.getDeviceType(), data.getType());
            nameTv.setText(title);
            downloadTv.setOnClickListener(listener);
            uploadTv.setOnClickListener(listener);
            disconnectTv.setOnClickListener(listener);
        }

        public void setListener(OnDisconnectListener listener) {
            listenerWeakReference = new WeakReference<OnDisconnectListener>(listener);
        }

        private void disconnect(Pipeline data) {
            switch (data.getDeviceType()) {
                case PAYLOAD:
                    if (payload == null) {
                        return;
                    }
                    payload.getPipelines().disconnect(data.getId(), new CommonCallbacks.CompletionCallback<PipelineError>() {
                        @Override
                        public void onResult(PipelineError error) {
                            if (error == null) {
                                toast("disconnect success");
                            } else {
                                toast("disconnect fail:" + error.toString());
                            }
                        }
                    });
                    break;
                case ON_BOARD:
                    if (flightController == null) {
                        return;
                    }
                    flightController.getPipelines().disconnect(data.getId(), new CommonCallbacks.CompletionCallback<PipelineError>() {
                        @Override
                        public void onResult(PipelineError error) {
                            if (error == null) {
                                toast("disconnect success");
                            } else {
                                toast("disconnect fail:" + error.toString());
                            }
                        }
                    });
                    break;
            }
            if (data != null && listenerWeakReference != null && listenerWeakReference.get() != null) {
                listenerWeakReference.get().onDisconnect(data);
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        public void destroy() {
            if (uploadHandler != null && uploadThread != null) {
                uploadHandler.removeCallbacksAndMessages(null);
                uploadThread.quitSafely();
            }
            if (downloadHandler != null && downloadThread != null) {
                uploadHandler.removeCallbacksAndMessages(null);
                downloadThread.quitSafely();
            }
            resetDownInfo();
            resetUploadInfo();
        }

        private void resetDownInfo() {
            downloadFileInfoLog = "";
            downloadProgress = "";
            downloadResult = "";
            downloadPackCount = 0;
            downloadSize = 0;

            downloadFinalForceText1 = "";
            downloadFinalForceText2 = "";
            downloadFinalForceText3 = "";
        }

        private void resetUploadInfo() {
            uploadFileInfoLog = "";
            uploadProgress = "";
            uploadResult = "";

            uploadCommandText1 = "";
        }

        /**
         * upload向OSDK上传文件函数
         * @param data
         */
        private void uploadFile(Pipeline data) {
            if (data == null) {
                return;
            }
            if (uploading) {
                toast("uploading");
                return;
            }
            uploading = true;
            long time = System.currentTimeMillis();

            InputStream inputStream = null; // 读取流，但是这里应该是用FileInputStream比较好
            FileOutputStream out = null; // 输出流，数据写出本地文件
            ByteArrayOutputStream outputStream = null; //字节输出流，写字节
            try {
                byte[] buff = new byte[3072];
                inputStream = itemView.getContext().getAssets().open("mop/" + uploadFileName);
                Log.e("hooyee_mop", inputStream.toString());
                File tmp = new File(itemView.getContext().getCacheDir(),  "mop.tmp");
                out = new FileOutputStream(tmp);
                outputStream = new ByteArrayOutputStream();
                int len;
                while ((len = inputStream.read(buff, 0, 3072)) > 0) {
                    outputStream.write(buff, 0, len);
                    out.write(buff, 0, len);
                }
                MOPCmdHelper.sendUploadFileReq(data, uploadFileName, outputStream.toByteArray(), time, MOPCmdHelper.getMD5(tmp), listener);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    inputStream.close();
                    out.close();
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            uploadCount++;
            updateUploadUI();
            uploading = false;
//            if (autoDownloadSwitch.isChecked()) {
//                uploadTv.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        uploadTv.performClick();
//                    }
//                }, 500);
//            }
        }

        /**
         * 自定义发送指令到OSDK 主要逻辑函数
         * 全双工，可以考虑走非可靠通道TODO 7.6
         * @param pipeline
         * @param filename
         */
        private  void uploadCommandReliable(Pipeline pipeline, String filename) {
            if (pipeline == null) {
                return;
            }
            if (uploading) {
                toast("正在上传，稍后再试");
                return;
            }
            uploading = true;
            long time = System.currentTimeMillis();

            ByteArrayOutputStream outputStream = null; //字节输出流，写字节
            try {
                byte[] buff = new byte[8]; // 指令编码成1,2,3,4这种只需要1个字节.留出缓冲区 TODO：2023年7月6日
                Log.e("hooyee_mop_upload", "  Ready to upload command");

                // 把sendUploadFileReq拿过来在这里实现
//                MOPCmdHelper.sendUploadFileReq(pipeline, uploadFileName, outputStream.toByteArray(), time, MOPCmdHelper.getMD5(tmp), listener);
                // 指令转字节，传入字节中
                byte[] commandBytes = uploadCommandText1.getBytes();
                buff[0] = commandBytes[0];

                // 自定义实现
                MOPCmdHelper.sendUploadFileReqSelf(pipeline, uploadCommandText1, buff, time, listener);

            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            uploadCount++;
            updateUploadWindUI();
            uploading = false;
//            if (autoDownloadSwitch.isChecked()) {
//                uploadTv.postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        uploadTv.performClick();
//                    }
//                }, 500);
//            }
        }

        /**
         * 走不可靠通道，本项目的主发送程序
         * @param pipeline  管道
         * @param filename  文件名字 wind生效
         */
        private void downloadForceUnreliable(Pipeline pipeline, String filename) {
            if (pipeline == null) {
                return;
            }
            // 如果这个参数是true，弹窗显示 正在下载
            if (downloading) {
                toast("downloading");
                return;
            }
            downloading = true;
            long time = System.currentTimeMillis();

            updateDownloadWindUI();

            while (true) {
                // MSDK开始接收文件数据  先接收头。OSDK那边好像是先发送头的数据
                byte[] headBuff = new byte[OSDK_FORCE_ARRAY_INT_LENGTH];
                int len1 = pipeline.readData(headBuff, 0, headBuff.length);
                // 返回值大于0，小于头的字节长度说明没有读取完，就一直读取
                if (len1 < 2) {
                    // TODO
                    DJILog.logWriteE("PipelineAdapter", "------------headbuff < 12, is "+ len1  , "/MOP");
                    break;
                }
                // 接收到的文件头验证正确之后，开始进入具体数据的传输
                    int length = OSDK_FORCE_ARRAY_INT_LENGTH;
                    byte[] dataBuff = new byte[length];

                int sum = 0;
                    int readLength = length;
                        downloading = true;
                        // MOP数据读取到dataBuff里面
                        int len = pipeline.readData(dataBuff, 0, readLength);
                        sum += len;
                        if (len > 0) {
                            DJILog.logWriteI("PipelineAdapter", "==============----------------"+filename + " download : " + sum);
                            byte[] subArray = ByteUtils.subArray(dataBuff, 0, 8);
                            String tmp = ByteUtils.toHexString(subArray);

                            // 测试参数，截取字节
                            int finalForceTest1 = MOPCmdHelper.getInt(dataBuff,4,4);
                            int finalForceTest2 = MOPCmdHelper.getInt(dataBuff,0,4);
                            int finalForceTest3 = MOPCmdHelper.getInt(dataBuff,0,10);
                            int finalForceTest4 = byteArrayToIntBigEndian(dataBuff);
                            // int下这三个是对的
                            int finalForceTestX = byteToInt(dataBuff[0]);
                            int finalForceTestY = byteToInt(dataBuff[1]);
                            int finalForceTestZ = byteToInt(dataBuff[2]);
                            // 转float
                            float finalForceFloatX = finalForceTestX / OSDK_FLOAT_DATA_INT_MAGNIFICATION;
                            float finalForceFloatY = finalForceTestY / OSDK_FLOAT_DATA_INT_MAGNIFICATION;
                            float finalForceFloatZ = finalForceTestZ / OSDK_FLOAT_DATA_INT_MAGNIFICATION;

                            String finalForceStrTest1 = ByteUtils.toHexString(dataBuff);
                            Log.e("myself_wind_download", ", data:" + tmp + ", length:" + len);
                            downloadFinalForceText1 = String.format("测试1：t1 = %d, t2 = %d, t3 = %d, t4 = %d",
                                    finalForceTest1, finalForceTest2, finalForceTest3, finalForceTest4);
                            downloadFinalForceText2 = String.format("测试2：Intx = %d, Inty = %d, Inty = %d",
                                    finalForceTestX, finalForceTestY, finalForceTestZ);
                            //downloadFinalForceText3 = String.format("dataBuff Origin"+ finalForceStrTest1);
                            downloadFinalForceText4 = String.format("测试3：ForceX = %f, ForceY = %f, ForceZ = %f",
                                    finalForceFloatX, finalForceFloatY, finalForceFloatZ);
                            downloadFinalForceText3 = String.format("原始接收dataBuff"+ finalForceStrTest1);

                            updateDownloadWindUI();
                        }
                // 检测文件头字节  判断是否结束
                if (MOPCmdHelper.isFileEnd(headBuff)) {
                    downloadResult = "Wind success";
                    Log.e("myself_wind_download", "End Sending Data");
                    break;
                }
                try{
                    Thread.sleep(1000 / READ_DATA_FROM_OSDK_FREQ);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            downloading = false;
            downloadCount++;
            if (true) {
                downloadSuccessCount++;
                // 第四次挥手 返回ack，告诉发送方OSDK我收到了
                MOPCmdHelper.sendTransAck(pipeline, true);
            } else {
                MOPCmdHelper.sendTransAck(pipeline, false);
            }
            updateDownloadWindUI();
        }

        /**
         * 可靠通道，下载其余电脑任务。不在本次项目范围之内
         * 巡检项目 下载日志
         * @param pipeline
         * @param filename
         */
        private void downloadFile(Pipeline pipeline, String filename) {
            if (pipeline == null) {
                return;
            }
            // 如果这个参数是true，弹窗显示 正在下载，别乱动的意思
            if (downloading) {
                toast("downloading");
                return;
            }
            downloading = true;
            long time = System.currentTimeMillis();

            // 类似TCP UDP的 三次握手四次挥手，这个是发送文件请求、接收文件信息类似的操作开始传输MOP
            // 整个函数返回从OSDK那边传来的文件基础信息。FileInfo类型的
            // 在这里把目标文件名字传给OSDK那边去识别，之后返回结果
            MOPCmdHelper.FileInfo fileInfo = MOPCmdHelper.sendDownloadFileReq(pipeline, filename, listener);
            if (fileInfo == null || !fileInfo.isExist) {
                DJILog.logWriteE("PipelineAdapter", "--------------downloadFile fail", "/MOP");
                downloading = false;
                return;
            }
            downloadFileInfoLog = fileInfo.toString();
            DJILog.logWriteE("PipelineAdapter", "++++++++++++++++fileInfo is : " +downloadFileInfoLog , "/MOP");
            // 更新下面那个显示框
            // 这个显示的位置就是红色的 read data那个地方
            updateDownloadUI();

            // 应该是java里面创建文件的操作 不懂TODO
            RandomAccessFile stream = null;
            try {
                File file = new File(getExternalCacheDirPath(itemView.getContext(), fileInfo.filename));
                if (file.exists()) {
                    file.delete();
                    file.createNewFile();
                } else {
                    file.createNewFile();
                }
                stream = new RandomAccessFile(file, "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // 上面结束之后，开始正式进入OSDK的读取文件，然后发送过来
            // 应该不是一帧，是一字节的收，就是按照阻塞的方式osdk发一串字节就阻塞一下等待这边接收
            // 阻塞通道收发消息  全双工的不影响对方
            while (true) {
                // MSDK开始接收文件数据  先接收头。OSDK那边好像是先发送头的数据
                byte[] headBuff = new byte[MOPCmdHelper.PACK_HEADER_SIZE];
                int len1 = pipeline.readData(headBuff, 0, headBuff.length);
                // 返回值大于0，小于头的字节长度说明没有读取完，就一直读取
                if (len1 < MOPCmdHelper.PACK_HEADER_SIZE) {
                    // TODO
                    byte abb = 0x11;
                    continue;
                }
                // 这个包带有的文件字节
                // 是不是先读取文件的头 确认是什么指令. 返回一个文件传输结果值FileTransResult类型
                MOPCmdHelper.FileTransResult result = MOPCmdHelper.parseFileDataCmd(headBuff);
                if (result == null) {
                    downloading = false;
                    return;
                }
                // 接收到的文件头验证正确之后，开始进入具体数据的传输
                if (result.success) {
                    int length = result.length;
                    byte[] dataBuff = new byte[length];
                    DJILog.logWriteE("PipelineAdapter", "++++++++--------------read data directly"  , "/MOP");
                    int sum = 0;
                    int readLength = length;
                    while (sum < length) {
                        int len = pipeline.readData(dataBuff, 0, readLength);

                        if (len > 0) {
                            downloadPackCount ++;
                            downloadSize += len;
                            DJILog.logWriteI("PipelineAdapter", "==============----------------"+filename + " download : " + sum);
                            byte[] subArray = ByteUtils.subArray(dataBuff, 0, 3);
                            // TODO是只有前四个字节是有效数据吗？需要明确一下。查看OSDK 那边发送的数据顺序到这里是哪个
                            String tmp = ByteUtils.toHexString(subArray);
                            Log.e("hooyee_pipe_download", "pack seq:" + downloadPackCount + ", data:" + tmp + ", length:" + len);
                            sum += len;
                            readLength -= len;
                            downloadLogTv.post(new Runnable() {
                                @Override
                                public void run() {
                                    downloadProgress = String.format("have downloadPack = %d, downloadSize:%d/%d, useTime:%d(ms)", downloadPackCount, downloadSize, fileInfo.fileLength, System.currentTimeMillis() - time);
                                    updateDownloadUI();
                                }
                            });
                            try {
                                stream.write(dataBuff, 0, len);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                } else
                {
                    // 解析出错位置
                    int position = MOPCmdHelper.parseFileFailIndex(pipeline);
                    // ack
                    MOPCmdHelper.sendTransFileFailAck(pipeline, position);
                    // 确认是否能接着传
                    if (MOPCmdHelper.parseCommonAck(pipeline)) {
                        try {
                            stream.seek(position);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } else {
                        try {
                            stream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        downloadLogTv.post(new Runnable() {
                            @Override
                            public void run() {
                                downloadLogTv.setText("transfer failure");
                            }
                        });
                        downloading = false;
                        return;
                    }

                }
                // 检测文件头字节  判断是否结束
                if (MOPCmdHelper.isFileEnd(headBuff)) {
                    downloadResult = "Download success";
                    updateDownloadUI();
                    break;
                }
            }
            try {
                stream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            downloading = false;
            // 检验md5。每次点击download下载一个文件才会检验一次
            boolean result = verifyMd5(fileInfo, new File(getExternalCacheDirPath(itemView.getContext(), fileInfo.filename)));
            downloadResult = "verify md5 :" + result;
            downloadCount++;
            if (downloadSize == fileInfo.fileLength && result) {
                downloadSuccessCount++;
                // TCP第四次挥手（好像是？） 返回ack，告诉发送方OSDK我收到了
                MOPCmdHelper.sendTransAck(pipeline, true);
            } else {
                MOPCmdHelper.sendTransAck(pipeline, false);
            }
            updateDownloadUI();
            // auto download的选项
            if (autoDownloadSwitch.isChecked()) {
                downloadTv.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        downloadTv.performClick();
                    }
                }, 500);
            }
        }

        private boolean verifyMd5(MOPCmdHelper.FileInfo fileInfo, File file) {
            String md5 = ByteUtils.toHexString(fileInfo.md5);
            String md5_1 = ByteUtils.toHexString(MOPCmdHelper.getMD5(file));
            String log = String.format("MOPCmdHelper.FileInfo_md5: %s, file_md5:%s", md5, md5_1);
            DJILog.logWriteE("PipelineAdapter", log, "/MOP");
            return md5.equals(md5_1);
        }

        private void toast(String text) {
            if (itemView == null) {
                return;
            }
            itemView.post(() -> Toast.makeText(itemView.getContext(), text, Toast.LENGTH_SHORT).show());
        }

        public void handlerTipEvent(MOPCmdHelper.TipEvent event) {
            switch (event.type) {
                case MOPCmdHelper.TipEvent.UPLOAD:
                    if (event.result != null) {
                        uploadResult = event.result;
                        if (uploadResult.contains("Success")) {
                            // hard code
                            uploadSuccessCount++;
                        }
                    }
                    if (event.progress != null) {
                        uploadProgress = event.progress;
                    }
                    updateUploadUI();
                    break;
                case MOPCmdHelper.TipEvent.DOWNLOAD:
                    if (event.result != null) {
                        downloadResult = event.result;
                    }
                    if (event.progress != null) {
                        downloadProgress = event.progress;
                    }
                    downloadLogTv.post(new Runnable() {
                        @Override
                        public void run() {
                            downloadLogTv.setText(downloadLogTv.getText().toString() + "\n" + event.result);
                        }
                    });
                    // 这里加入就报错
//                    if (filenameTv.toString().equals("wind")){
//                        finalForceTv.post(new Runnable() {
//                            @Override
//                            public void run() {
//                                finalForceTv.setText(finalForceTv.getText().toString() + "/n" + event.result);
//                            }
//                        });
//                    }
                    break;
            }

        }

        public void onEvent3BackgroundThread(MOPCmdHelper.FileInfo event) {
            uploadFileInfoLog = event.toString();
            updateUploadUI();
        }

        private void updateUploadUI() {
            StringBuffer sb = new StringBuffer("Upload:" + "\n")
                    .append(uploadFileInfoLog == null ? "" : uploadFileInfoLog).append("\n")
                    .append(uploadProgress == null ? "" : uploadProgress).append("\n")
                    .append(uploadResult == null ? "" : uploadResult).append("\n")
                    .append("成功/次数：" + uploadSuccessCount + "/" + uploadCount);

            uploadLogTv.post(new Runnable() {
                @Override
                public void run() {
                    uploadLogTv.setText(sb.toString());
                }
            });
        }

        private void updateDownloadUI() {
            StringBuffer sb = new StringBuffer("Download:" + "\n")
                    .append(downloadFileInfoLog == null ? "" : downloadFileInfoLog).append("\n")
                    .append(downloadProgress == null ? "" : downloadProgress).append("\n")
                    .append(downloadResult == null ? "" : downloadResult).append("\n")
                    .append("成功/次数：" + downloadSuccessCount + "/" + downloadCount);
            downloadLogTv.post(new Runnable() {
                @Override
                public void run() {
                    downloadLogTv.setText(sb.toString());
                }
            });
        }

        private void updateDownloadWindUI() {
            StringBuffer showBuffer = new StringBuffer("Wind result" + "\n")
                    .append(downloadFinalForceText1 == null ? "" : downloadFinalForceText1).append("\n")
                    .append(downloadFinalForceText2 == null ? "" : downloadFinalForceText2).append("\n")
                    .append(downloadFinalForceText3 == null ? "" : downloadFinalForceText3).append("\n")
                    .append(downloadFinalForceText4 == null ? "" : downloadFinalForceText4).append("\n");
            finalForceTv.post(new Runnable() {
                @Override
                public void run() {
                    finalForceTv.setText(showBuffer.toString());
                }
            });
        }

        private void updateUploadWindUI() {
            StringBuffer sb = new StringBuffer("Wind Upload:" + "\n")
                    .append(uploadCommandText1 == null ? "" : uploadCommandText1).append("\n")
                    .append(uploadProgress == null ? "" : uploadProgress).append("\n")
                    .append(uploadResult == null ? "" : uploadResult).append("\n")
                    .append("成功/次数：" + uploadSuccessCount + "/" + uploadCount);

            uploadLogTv.post(new Runnable() {
                @Override
                public void run() {
                    uploadLogTv.setText(sb.toString());
                }
            });
        }

        public static int byteArrayToIntBigEndian(byte[] bytes) {
            int x = 0;
            for (int i = 0; i < 4; i++) {
                x <<= 8;
                int b = bytes[i] & 0xFF;
                x |= b;
            }
            return x;
        }
        public static int byteToInt(byte bytes) {
            int x = 0;
            x <<= 8;
            int b = bytes & 0xFF;
            x |= b;
            return x;
        }

        public static float byteToFloat(byte[] arr) {
            int accum = 0;
            accum = accum|(arr[0] & 0xff) << 0;
            accum = accum|(arr[1] & 0xff) << 8;
            accum = accum|(arr[2] & 0xff) << 16;
            accum = accum|(arr[3] & 0xff) << 24;
            return Float.intBitsToFloat(accum);
        }


    }

    /**
     * 获取APP外部存储路径，例如下面的路径
     * /sdcard/Android/data/com.example.myapp/files/DJI/com.example.myapp/images
     * @param context
     * @param path
     * @return
     */
    public static String getExternalCacheDirPath(Context context,String path) {
        String dirName = Environment.getExternalStorageDirectory() + "/DJI/";
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            dirName = context.getExternalFilesDir("DJI").getPath() + File.separator;
        }
        return dirName + context.getPackageName() + File.separator + path;
    }

    public interface OnDisconnectListener {
        void onDisconnect(Pipeline data);
    }

    public interface OnEventListener {
        void onTipEvent(MOPCmdHelper.TipEvent event);

        void onFileInfoEvent(MOPCmdHelper.FileInfo event);
    }
}
