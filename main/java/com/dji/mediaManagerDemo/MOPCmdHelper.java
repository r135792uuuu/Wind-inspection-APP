package com.dji.mediaManagerDemo;

import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import dji.log.DJILog;
import dji.mop.common.Pipeline;

/**
 * MOP 的头类和定义
 */
public class MOPCmdHelper {
    // MOP 默认状态机
    private static final byte CMD_REQ = 0x50;
    private static final byte CMD_ACK = 0x51;
    private static final byte CMD_TRANS_ACK = 0x52;
    private static final byte CMD_FILE_INFO = 0x60;
    private static final byte CMD_DOWNLOAD = 0x61;
    private static final byte CMD_FILE_DATA = 0x62;
    private static final byte CMD_FILE_TRANS_FAIL = 0x63;
    private static final byte CMD_FILE_TRANS_FAIL_ACK = 0x64;
    private static final byte CMD_0 = 0x00;
    private static final byte CMD_1 = 0x01;
    public static final int PACK_HEADER_SIZE = 8;
    public static final int PACK_FILE_INFO_SIZE = 53;

    private static final int FILE_NAME_LENGTH = 32;

    // MOP改-自定义状态机

    private static final String TAG = MOPCmdHelper.class.getSimpleName();
    public static PipelineAdapter.OnEventListener listener;  // z先注释 8.2 去掉PipelineAdapter的影响编译  下面一样：标识符：removePA

    public static byte[] getUploadFileHeader() {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_REQ;
        cmd[1] = CMD_0;
        return cmd;
    }

    public static byte[] getDownloadFileHeader() {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_REQ;
        cmd[1] = CMD_1;
        return cmd;
    }

    public static byte[] getDownloadFile() {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_DOWNLOAD;
        cmd[1] = (byte) 0xFF;
        cmd[4] = (byte) 0x20;
        return cmd;
    }

    /**
     * 把整数size的4个字节存储到byte中国
     * @param size
     * @param flag
     * @return
     */
    public static byte[] getFileDataHeader(int size, int flag) {
        byte[] cmd = new byte[PACK_HEADER_SIZE];
        cmd[0] = CMD_FILE_DATA;
        cmd[1] = (byte) flag;
        cmd[4] = (byte) (size >> 0 & 0xff);
        cmd[5] = (byte) (size >> 8 & 0xff);
        cmd[6] = (byte) (size >> 16 & 0xff);
        cmd[7] = (byte) (size >> 24 & 0xff);
        return cmd;
    }

    public static boolean sendDownloadCmd(Pipeline p) {
        byte[] cmd = getDownloadFileHeader();
        int result = p.writeData(cmd, 0, cmd.length);
        if (result > 0) {
            return parseCommonAck(p);
        }
        return false;
    }


    /**
     * TODO 无法runUiThread
     * @param tag
     * @param description
     */
    private void showToastLog(final String tag, final String description) {
        Log.e("位置：" + tag, description);
    }

    /**
     * MSDK 下载文件
     * @param p
     * @param filename
     * @param listener
     * @return
     */
    public static FileInfo sendDownloadFileReq(Pipeline p, String filename, PipelineAdapter.OnEventListener listener) {
        if (!sendDownloadCmd(p)) {
            return null;
        }
        byte[] header = getDownloadFile(); // 8
        byte[] chars = filename.getBytes();

        byte[] req = new byte[header.length + FILE_NAME_LENGTH];
        // parameter：source 矩阵；开始位置；复制到的目标矩阵；开始位置，要复制的数组长度
        // 把指令头和要下载的目标文件字节组合成请求req
        System.arraycopy(header, 0, req, 0, header.length);
        System.arraycopy(chars, 0, req, header.length, chars.length);

        // 不超过最大长度32字节 末尾添加休止符
        if (chars.length < FILE_NAME_LENGTH) {
            req[header.length + chars.length] = '\0';
        }
        // 发送请求,要下载的文件
        DJILog.logWriteE("PipelineAdapter", "--------------send download req start"  , "/MOP");
        int result = p.writeData(req, 0, req.length);
        DJILog.logWriteE("PipelineAdapter", "----------------------send download req end"  , "/MOP");
        if (result > 0) {
            // C++OSDK那边接收到之后会先发送一个ack回来，然后那边才会开始传输数据
            DJILog.logWriteE("ack", "-------------------sendDownloadFileReq ack: Success", "/MOP");
            // 读取文件信息 8 + 53
            byte[] fileInfoBuff = new byte[MOPCmdHelper.PACK_HEADER_SIZE + MOPCmdHelper.PACK_FILE_INFO_SIZE];
            int sum = 0;
            int readLength = fileInfoBuff.length;
            // 当前读取长度小于总长度
            while (sum < fileInfoBuff.length) {
                // OSDK接收到请求之后，这边接收发过来的文件基础信息
                DJILog.logWriteE("PipelineAdapter", "------------------read start 1"  , "/MOP");
                int len = p.readData(fileInfoBuff, 0, readLength);
                DJILog.logWriteE("PipelineAdapter", "---------------read end 1"  , "/MOP");
                if (len > 0) {
                    // 由于read不支持offset，这里处理fileInfoBuff的拼接，应该使用临时的byte[]来copyArray
                    sum += len;
                    readLength -= len;
                    DJILog.logWriteE(TAG, "--------------sendDownloadFileReq download:" + sum, "/MOP");
                } else {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            // 分析传过来的文件基础信息，保存为文件基础信息类型
            FileInfo fileInfo = FileInfo.parse(fileInfoBuff);
            return fileInfo;

        } else {
            // 发送请求没有收到osdk那边的回执ack
            postResultTipEvent(TipEvent.DOWNLOAD, "request failure", null, listener);

        }

        return null;
    }

    public static int sendTransFileFailReq(Pipeline pipeline, long length) {
        byte[] buff = new byte[12];
        buff[0] = CMD_FILE_TRANS_FAIL;
        buff[1] = (byte) 0xFF;
        buff[7] = 0x04;
        buff[8] = (byte) (length >> 0 & 0xff);
        buff[9] = (byte) (length >> 8 & 0xff);
        buff[10] = (byte) (length >> 16 & 0xff);
        buff[11] = (byte) (length >> 24 & 0xff);

        int len = getInt(buff, PACK_HEADER_SIZE, 4);
        int result = pipeline.writeData(buff, 0, buff.length);
        DJILog.logWriteI(TAG, "sendTransFileFailReq:" + result, "/MOP");
        DJILog.logWriteI(TAG, "len:" + len, "/MOP");
        return result;
    }

    public static int sendTransFileFailAck(Pipeline pipeline, long length) {
        byte[] buff = new byte[12];
        buff[0] = CMD_FILE_TRANS_FAIL_ACK;
        buff[1] = (byte) 0xFF;
        buff[7] = 0x04;
        buff[8] = (byte) (length >> 0 & 0xff);
        buff[9] = (byte) (length >> 8 & 0xff);
        buff[10] = (byte) (length >> 16 & 0xff);
        buff[11] = (byte) (length >> 24 & 0xff);
        int result = pipeline.writeData(buff, 0, buff.length);
        DJILog.logWriteI(TAG, "sendTransFileFailAck:" + result, "/MOP");
        return result;
    }

    public static int parseTransFileFailAck(Pipeline p) {
        byte[] buff = new byte[12];
        int size;
        // 读取回包
        while ((size = p.readData(buff, 0, buff.length)) < 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DJILog.logWriteE(TAG, "parseTransFileFailAck wait ack: " + size, "/MOP");
        }
        int length = getInt(buff, PACK_HEADER_SIZE, 4);
        return length;
    }


    public static boolean parseCommonAck(Pipeline p) {
        // 读取文件下载的信息
        byte[] buff = new byte[PACK_HEADER_SIZE];
        int size;
        // 读取回包
        while ((size = p.readData(buff, 0, buff.length)) < 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DJILog.logWriteE(TAG, "sendDownloadFileReq wait ack: " + size, "/MOP");
        }
        return (buff[0] == CMD_ACK) && (buff[1] == CMD_0);
    }

    public static boolean parseUploadAck(Pipeline p) {
        // 读取文件下载的信息
        byte[] buff = new byte[PACK_HEADER_SIZE];
        int size;
        // 读取回包
        while ((size = p.readData(buff, 0, buff.length)) < 0) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            DJILog.logWriteE(TAG, "upload ack: " + size, "/MOP");
        }
        return (buff[0] == CMD_TRANS_ACK) && (buff[1] == CMD_0);
    }

    public static FileTransResult parseFileDataCmd(byte[] buff) {
        if (buff[0] == CMD_FILE_DATA) {  //0x62  在OSDK里面找
            int length = getInt(buff, 4, 4);
            return new FileTransResult(true, length);
        }
        if (buff[0] == CMD_FILE_TRANS_FAIL) {  // 0x63
            int length = getInt(buff, 4, 4);
            return new FileTransResult(false, length);
        }
        DJILog.logWriteE(TAG, "parseFileDataCmd error", "/MOP");
        return null;
    }

    public static int sendTransAck(Pipeline p, boolean result) {
        byte[] buff = new byte[PACK_HEADER_SIZE];
        buff[0] = CMD_TRANS_ACK;
        buff[1] = result ? CMD_0 : CMD_1;
        return p.writeData(buff, 0, buff.length);
    }

    public static int sendAck(Pipeline p, byte cmd) {
        byte[] buff = new byte[PACK_HEADER_SIZE];
        buff[0] = CMD_ACK;
        buff[1] = cmd;
        return p.writeData(buff, 0, buff.length);
    }

    public static boolean isFileEnd(byte[] buff) {
        return buff[0] == CMD_FILE_DATA && buff[1] == CMD_1;
    }

    /**  removePA
     * 自定义发送upload req请求函数
     * @param data
     * @param filename
     * @param buff
     * @param time
     * @param listener
     * @return
     */
    public static int sendUploadFileReqSelf(Pipeline data, String filename, byte[] buff, long time, PipelineAdapter.OnEventListener listener) {
        byte[] header = getUploadFileHeader(); // 0x50 0x00 NAN NAN ...
        int result = data.writeData(getUploadFileHeader(), 0, header.length); // 发送全局头
        // 头发送成功判断
        if (result < 0) {
            postResultTipEvent(TipEvent.UPLOAD, "MSDK发送全局头失败: " + result, null, listener);
            return result;
        }
        // 读取头文件OSDK回包 回报返回是true 0x51 0x00
        if (parseCommonAck(data)) {
            // 读取的回包允许发送
            DJILog.logWriteE(TAG, "准备发送指令到OSDK:" + filename, "/MOP");
            // 上传文件
            uploadFileSelf(data, buff, time, listener);
            // 上传完的ack
            if (parseUploadAck(data)) {
                postResultTipEvent(TipEvent.UPLOAD, "上传成功", null, listener);
            }
        }
        else{
            postResultTipEvent(TipEvent.UPLOAD, "MSDK接收OSDK回包拒绝", null, listener);
            DJILog.logWriteE(TAG, "MSDK接收OSDK回包拒绝" + "header cmd返回不正确" , "/MOP");
        }
        return result;
    }

    /**removePA
     * 自定义发送函数
     * @param data
     * @param buff
     * @param time
     * @param listener
     * @return
     */
    private static int uploadFileSelf(Pipeline data, byte[] buff, long time, PipelineAdapter.OnEventListener listener) {
        int size = 8;
        byte[] header;
        int hadWrote = 0;
        if (buff.length <= size) {
            // 发送数据头
            header = getFileDataHeader(buff.length, CMD_1); // 如果是8个字节的buff，那就是 0x62 0x01 0x00 0x00 0x08 0x00 0x00 0x00
            // 发送的字节是d:0x62 0x01 0x00 0x00 0x08 0x00 0x00 0x00(header) 0x07 0x00  0x00 0x00 0x00 0x00 0x00 0x00(buff)
            int result = writeData(data, buff, time, header, hadWrote, buff.length, listener);
            DJILog.logWriteE(TAG, "uploadFile: " + result, "/MOP");
            return result;
        }
        // 注：不涉及到大批量指令上传，不需要分段上传

        String progress = String.format("uploadSize:%d, useTime:%d(ms)", hadWrote, System.currentTimeMillis() - time);
        postResultTipEvent(TipEvent.UPLOAD, null, progress, listener);
        return 0;
    }


    /** removePA
     * 官方提供原始的发送upload请求函数
     * @return
     */
    public static int sendUploadFileReq(Pipeline data, String filename, byte[] buff, long time, byte[] md5, PipelineAdapter.OnEventListener listener) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.filename = filename;
        fileInfo.fileLength = buff.length;
        fileInfo.md5 = md5;
        DJILog.logWriteE(TAG, "sendUploadFileReq fileInfo:" + fileInfo, "/MOP");
        listener.onFileInfoEvent(fileInfo);

        byte[] header = getUploadFileHeader();
        // 先发送全局头指令
        int result = data.writeData(getUploadFileHeader(), 0, header.length);
        if (result < 0) {
            postResultTipEvent(TipEvent.UPLOAD, "Upload Failure: " + result, null, listener);
            return result;
        }
        /// OSDK 返回是 cmd0x51 subcmd 0x00
        if (parseCommonAck(data)) {
            // 发送md5头指令等
            byte[] fileHealder = fileInfo.getHeader();
            result = data.writeData(fileHealder, 0, fileHealder.length);
            DJILog.logWriteE(TAG, "sendUploadFileReq send md5:" + result, "/MOP");
            if (result > 0) {
                if (parseCommonAck(data)) {
                    // 上传文件
                    uploadFile(data, buff, time, listener);
                    // 上传完的ack
                    if (parseUploadAck(data)) {
                        postResultTipEvent(TipEvent.UPLOAD, "Upload Success", null, listener);
                    }
                } else {
                    postResultTipEvent(TipEvent.UPLOAD, "Upload Failure", null, listener);
                }
            }
        }
        return result;
    }

    /**
     * 因为这个类中无法使用runOnUiThread
     * 定义了一个TipEvent类来实现信息的保存 类似结构体
     * @param type
     * @param result
     * @param progress
     * @param listener
     */
    private static void postResultTipEvent(int type, String result, String progress, PipelineAdapter.OnEventListener listener) {
        TipEvent event = new TipEvent(type);
        event.result = result;
        event.progress = progress;
        listener.onTipEvent(event);
    }

    private static int uploadFile(Pipeline data, byte[] buff, long time, PipelineAdapter.OnEventListener listener) {
        int size = 3072;
        byte[] header;
        int hadWrote = 0;
        if (buff.length <= size) {
            header = getFileDataHeader(buff.length, CMD_1);
            int result = writeData(data, buff, time, header, hadWrote, buff.length, listener);
            DJILog.logWriteE(TAG, "uploadFile: " + result, "/MOP");
            return result;
        } else {
            while (buff.length - hadWrote > size) {
                header = getFileDataHeader(size, CMD_0);
                int result = writeData(data, buff, time, header, hadWrote, size, listener);

                hadWrote += size;
                DJILog.logWriteE(TAG, "uploadFile: " + hadWrote + " result:" + result);
            }
            int length = buff.length - hadWrote;
            header = getFileDataHeader(length, CMD_1);
            int result = writeData(data, buff, time, header, hadWrote, length, listener);
            hadWrote += result;
            DJILog.logWriteE(TAG, "uploadFile: " + length, "/MOP");
        }

        String progress = String.format("uploadSize:%d, useTime:%d(ms)", hadWrote, System.currentTimeMillis() - time);
        postResultTipEvent(TipEvent.UPLOAD, null, progress, listener);
        return 0;
    }

    private static int writeData(Pipeline pipeline, byte[] buff, long time, byte[] header, int hadWrote, int length, PipelineAdapter.OnEventListener listener) {
        byte[] d = new byte[length + header.length];
        // 把header和buff赋值到d，准备发送
        System.arraycopy(header, 0, d, 0, header.length);
        System.arraycopy(buff, 0, d, header.length, length);
        int result = pipeline.writeData(d, 0, d.length);

        String progress = String.format("uploadSize:%d, useTime:%d(ms)", hadWrote, System.currentTimeMillis() - time);
        postResultTipEvent(TipEvent.UPLOAD, null, progress, listener);

        if (result < 0) {
            DJILog.logWriteE(TAG, "writeData miss: " + result);
            // 发送上传出错的req
            sendTransFileFailReq(pipeline, hadWrote);
            // 读取对端返回的已读长度
            int len = parseTransFileFailAck(pipeline);
            if (len == hadWrote) {
                sendAck(pipeline, CMD_0);
                return writeData(pipeline, buff, time, header, hadWrote, length, listener);
            } else {
                DJILog.logWriteE(TAG, "writeData error: " + len);
                sendAck(pipeline, CMD_1);
            }
        }
        return result;
    }


    public static int parseFileFailIndex(Pipeline pipeline) {
        byte[] buff = new byte[4];
        pipeline.readData(buff, 0, 4);
        int length = getInt(buff, 0, 4);
        return length;
    }


    public static byte[] getMD5(File file) {

        byte[] desc = new byte[16];
        try {
            InputStream ins = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            int len;
            while((len = ins.read(buffer)) != -1){
                md5.update(buffer, 0, len);
            }
            ins.close();
            byte[] source = md5.digest();
            System.arraycopy(source, 0, desc, 0, desc.length);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return desc;
    }
    // 按照我单独测试，输入
    public static int getInt(byte[] bytes, final int offset, int length) {
        if (null == bytes) {
            return 0;
        }
        final int bytesLen = bytes.length;
        if (bytesLen == 0 || offset < 0 || bytesLen <= offset) {
            return 0;
        }
        if (length > bytesLen - offset) {
            length = bytesLen - offset;
        }

        int value = 0;
        // 字节数组转int 大端模式的写法固定的写法
        for (int i = length + offset - 1; i >= offset; i--) {
            value = (value << 8 | (bytes[i] & 0xff));
        }
        return value;
    }

    public static class FileInfo {
        public boolean isExist;
        public int fileLength;
        public String filename;
        public byte[] md5;

        public static FileInfo parse(byte[] data) {
            if (data[0] != CMD_FILE_INFO) {
                return null;
            }

            StringBuffer sb = new StringBuffer("mop_cmd_file:");
            for (int i = 0; i < data.length; i++) {
                sb.append(data[i] + ",");
            }

            FileInfo info = new FileInfo();
            info.isExist = data[8] == 1;
            info.fileLength = getInt(data, 9, 4);
            byte[] d = new byte[32];
            System.arraycopy(data, 13, d, 0, 32);
            info.filename = getString(d);
            byte[] md5 = new byte[16];
            System.arraycopy(data, 45, md5, 0, 16);
            info.md5 = md5;
            return info;
        }

        public byte[] getHeader() {
            byte[] buff = new byte[61];
            buff[0] = CMD_FILE_INFO;
            buff[1] = (byte) 0xFF;
            buff[4] = 0x35;
            buff[8] = 0;
            buff[9] = (byte) (fileLength >> 0 & 0xff);
            buff[10] = (byte) (fileLength >> 8 & 0xff);
            buff[11] = (byte) (fileLength >> 16 & 0xff);
            buff[12] = (byte) (fileLength >> 24 & 0xff);

            byte[] chars = filename.getBytes();
            System.arraycopy(chars, 0, buff, 13, chars.length);
            System.arraycopy(md5, 0, buff, 45, 16);
            if (chars.length < 32) {
                buff[13 + chars.length] = '\0';
            }
            return buff;
        }

        public static String getString(byte[] bytes) {
            if (null == bytes) {
                return "";
            }
            // 去除NULL字符
            byte zero = 0x00;
            byte no = (byte)0xFF;
            for (int i = 0; i < bytes.length; i++) {
                if (bytes[i] == zero || bytes[i] == no) {
                    bytes = readBytes(bytes, 0, i);
                    break;
                }
            }
            return new String(bytes, Charset.forName("GBK"));
        }

        public static byte[] readBytes(byte[] source, int from, int length) {
            byte[] result = new byte[length];
            System.arraycopy(source, from, result, 0, length);
            /**
             for (int i = 0; i < length; i++) {
             result[i] = source[from + i];
             }
             */
            return result;
        }

        @Override
        public String toString() {
            return "FileInfo{" +
                    "isExist=" + isExist +
                    ", fileLength=" + fileLength +
                    ", filename='" + filename + '\'' +
                    ", md5=" + Arrays.toString(md5) +
                    '}';
        }
    }

    public interface ProcessCallback {
        void callback(int length);
    }

    public static class FileTransResult {
        // true代表读了多少数据(length)，false重新读写，并根据之后length个字节锁代表的int值去移动游标到指定位置
        public boolean success;
        public int length;

        public FileTransResult(boolean success, int length) {
            this.success = success;
            this.length = length;
        }
    }

    /**
     * 点击事件的结构
     */
    public static class TipEvent {
        public static final int UPLOAD = 0;
        public static final int DOWNLOAD = 1;

        public TipEvent(int type) {
            this.type = type;
        }

        public int type;
        public String state;
        public String result;
        public String progress;
    }
}
