package com.xiaolan.device.wash;

import com.xiaolan.device.OnDeviceMessageListener;
import com.xiaolan.device.OnDeviceStateListener;

import org.apache.commons.lang3.ArrayUtils;

import java.io.*;
import java.util.Date;

public class Device {

    public final static int ACTION_KILL = 1;
    public final static int ACTION_HOT = 2;
    public final static int ACTION_WARM = 3;
    public final static int ACTION_COLD = 4;
    public final static int ACTION_DELICATES = 5;
    public final static int ACTION_SUPER_HOT = 6;
    public final static int ACTION_SUPER_WARM = 7;
    public final static int ACTION_SUPER_COLD = 8;
    public final static int ACTION_SUPER_DELICATES = 9;
    public final static int ACTION_START = 10;
    public final static int ACTION_CLEAN = 11;

    public final static int STATE_IDLE = 1;
    public final static int STATE_RUNNING = 2;
    public final static int STATE_ERROR = 3;

    public final static int STATE_IDLE_END = 0x11;
    public final static int STATE_RUNNING_S1 = 0x21;
    public final static int STATE_RUNNING_S2 = 0x22;
    public final static int STATE_RUNNING_S3 = 0x23;
    public final static int STATE_ERROR_PAUSE = 0x30;
    public final static int STATE_ERROR_IE = 0x31;
    public final static int STATE_ERROR_OE = 0x32;
    public final static int STATE_ERROR_DE = 0x33;
    public final static int STATE_ERROR_UE = 0x34;

    private BufferedInputStream readBuffer;
    private BufferedOutputStream writeBuffer;
    private OnDeviceMessageListener handler;
    private DeviceInterface device;

    public Device(InputStream input, OutputStream output, final OnDeviceMessageListener handler) {
        readBuffer = new BufferedInputStream(input, 1024 * 64);
        writeBuffer = new BufferedOutputStream(output, 1024 * 64);
        this.handler = new OnDeviceMessageListener() {
            @Override
            public void onMsgChange(boolean valid, byte[] msg) {
                //if (!valid) {
                System.out.println("read " + valid + " " + ArrayUtils.toString(msg));
                //}
                handler.onMsgChange(valid, msg);
            }
        };
    }

    public synchronized int poll(final OnDeviceStateListener handler) throws IOException {
        check();
        return device.poll(new OnDeviceStateListener() {
            @Override
            public void onDeviceState(int state, String text) {
                System.out.println("poll " + state + " " + text);
                handler.onDeviceState(state, text);
            }
        });
    }

    public synchronized boolean push(final int mode) throws IOException {
        check();
        return device.push(mode);
    }

    private synchronized void check() throws IOException {

        if (device != null) {
            return;
        }

        readBuffer.mark(65536);
        Date now = new Date();
        for (; ; ) {
            try {
                if ((device = new JrWasher(readBuffer, writeBuffer, handler)).check()) {
                    break;
                } else if ((device = new XjlWasher(readBuffer, writeBuffer, handler)).check()) {
                    break;
                } else if ((device = new JrplusWasher(readBuffer, writeBuffer, handler)).check()) {
                    break;
                } else {
                    readBuffer.skip(1);
                    readBuffer.mark(65536);
                    device = null;
                    continue;
                }
            } catch (IOException ignored) {
            }
            Date now2 = new Date();
            if (now2.getTime() - now.getTime() > 10000) {
                throw new IOException("no data");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }

    }

}
