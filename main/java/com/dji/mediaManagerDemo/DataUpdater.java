package com.dji.mediaManagerDemo;

import android.os.Handler;

import java.util.Random;

/**
 * 实时更新MOP传来的数据，在页面A中实时更新，处理数据更新
 * 用以在多个线程之间传递数据
 */
public class DataUpdater {
    private Handler handler;
    private boolean isUpdating = false;
    private Runnable updateRunnable;
    private int b1, b2, b3; // TODO 确定还有多少个数据要显示

    public DataUpdater() {
        handler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                if (isUpdating) {
                    // 模拟更新数据 b1、b2、b3
                    Random random = new Random();
                    b1 = random.nextInt(100);
                    b2 = random.nextInt(100);
                    b3 = random.nextInt(100);

                    // 延时一段时间后再次执行更新
                    handler.postDelayed(this, 500); // 每隔0.5秒更新一次
                }
            }
        };
    }

    public void startUpdating() {
        isUpdating = true;
        handler.post(updateRunnable);
    }

    public void stopUpdating() {
        isUpdating = false;
        handler.removeCallbacksAndMessages(null);
    }

    public int getB1() {
        return b1;
    }

    public int getB2() {
        return b2;
    }

    public int getB3() {
        return b3;
    }
}

