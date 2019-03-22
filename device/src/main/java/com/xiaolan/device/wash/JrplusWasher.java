package com.xiaolan.device.wash;

import android.util.Log;

import com.xiaolan.device.OnDeviceMessageListener;
import com.xiaolan.device.OnDeviceStateListener;

import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.*;

public class JrplusWasher implements DeviceInterface {

    private final static CRC16 crc16 = new CRC16();
    private static final String TAG = "JrplusWasher";
    private final BufferedInputStream reader;
    private final BufferedOutputStream writer;
    private final OnDeviceMessageListener handler;
    private final static Map<Byte, String> textTable = new HashMap<Byte, String>() {
        {
            put((byte) 0x00, "");
            put((byte) 0x3F, "0");
            put((byte) 0x06, "1");
            put((byte) 0x5B, "2");
            put((byte) 0x4F, "3");
            put((byte) 0x66, "4");
            put((byte) 0x6D, "5");
            put((byte) 0x7D, "6");
            put((byte) 0x27, "7");
            put((byte) 0x7F, "8");
            put((byte) 0x6F, "9");
            put((byte) 0x77, "A");
            put((byte) 0x7C, "b");
            put((byte) 0x5E, "d");
            put((byte) 0x79, "E");
            put((byte) 0x67, "g");
            put((byte) 0x74, "h");
            put((byte) 0x39, "C");
            put((byte) 0x76, "H");
            put((byte) 0x38, "L");
            put((byte) 0x54, "n");
            put((byte) 0x73, "P");
            put((byte) 0x78, "t");
            put((byte) 0x71, "F");
            put((byte) 0x50, "r");
            put((byte) 0x5C, "o");
            put((byte) 0x3E, "U");
            put((byte) 0x58, "c");
        }
    };
    private final static int KEY_START = 1;
    private final static int KEY_KEY1 = 2;
    private final static int KEY_KEY2 = 3;
    private final static int KEY_KEY3 = 4;
    private final static int KEY_KEY4 = 5;
    private final static int KEY_SUPER = 6;
    private final static int KEY_MENU = 8;

    private byte[] prevMessage;
    private int seq = 1;
    private int lightadd = 0;        // 加强
    private int lightst = 0;    // 开始
    private int lights1 = 0;    // 第1步
    private int lights2 = 0;    // 第2步
    private int lights3 = 0;    // 第3步
    private int lightlk = 0;    // 锁
    private int err = 0;
    private int msgInt = 0;
    private String text;
    private int view;
    private int viewStep;
    private int msgType = 1;

    public JrplusWasher(BufferedInputStream readBuffer, BufferedOutputStream writeBuffer, OnDeviceMessageListener handler) {
        this.reader = readBuffer;
        this.writer = writeBuffer;
        this.handler = handler;
    }

    @Override
    public boolean check() throws IOException {
        prevMessage = null;
        readAll();
        if (prevMessage == null) {
            return false;
        }
        msgType = 0;
        writeOne(msgType, KEY_MENU);
        readAll();
        if (3 == msgInt) {
            return true;
        }
        msgType = 1;
        writeOne(msgType, KEY_MENU);
        readAll();
        return 3 == msgInt;
    }

    @Override
    public boolean push(int action) throws IOException {
        if (action == Device.ACTION_HOT
                || action == Device.ACTION_WARM
                || action == Device.ACTION_COLD
                || action == Device.ACTION_DELICATES
                || action == Device.ACTION_SUPER_HOT
                || action == Device.ACTION_SUPER_WARM
                || action == Device.ACTION_SUPER_COLD
                || action == Device.ACTION_SUPER_DELICATES) {
            System.out.println("run " + action);

            boolean add = false;
            int key;
            if (action == Device.ACTION_HOT) {
                key = KEY_KEY1;
            } else if (action == Device.ACTION_WARM) {
                key = KEY_KEY2;
            } else if (action == Device.ACTION_COLD) {
                key = KEY_KEY3;
            } else if (action == Device.ACTION_DELICATES) {
                key = KEY_KEY4;
            } else if (action == Device.ACTION_SUPER_HOT) {
                key = KEY_KEY1;
                add = true;
            } else if (action == Device.ACTION_SUPER_WARM) {
                key = KEY_KEY2;
                add = true;
            } else if (action == Device.ACTION_SUPER_COLD) {
                key = KEY_KEY3;
                add = true;
            } else if (action == Device.ACTION_SUPER_DELICATES) {
                key = KEY_KEY4;
                add = true;
            } else {
                return false;
            }

            for (; ; ) {
                // 强制返回
                for (; ; ) {
                    writeOne(msgType, KEY_KEY1);
                    readAll();
                    if (isRunning()) {
                        // 正在运行
                        return false;
                    } else if (isError()) {
                        writeOne(msgType, KEY_START);
                    } else if (prevMessage[6] == 0x3c) {
                        break;
                    }
                }

                writeOne(msgType, key);

                if (add) {
                    writeOne(msgType, KEY_SUPER);
                    readAll();
                    if (!(isOn(lightadd))) {
                        continue;
                    }
                }

                break;
            }

            // 开始
            for (; ; ) {
                writeOne(msgType, KEY_START);
                readAll();
                if (isRunning()) {
                    return true;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        } else if (action == Device.ACTION_START) {
            System.out.println("start");
            writeOne(msgType, KEY_START);
            return true;
        } else if (action == Device.ACTION_KILL) {//执行kill的过程
            System.out.println("kill");

            for (; ; ) {
                // 强制返回
                for (; ; ) {
                    readAll();
                    if (isRunning()) {
                        // 正在运行
                        break;
                    } else if (isError()) {
                        writeOne(msgType, KEY_START);
                        continue;
                    } else if (isIdle()) {
                        return false;
                    }
                    writeOne(msgType, KEY_KEY1);
                }

                writeOne(msgType, KEY_MENU);
                for (int i = 1; i <= 3; ++i) {
                    writeOne(msgType, KEY_KEY2);
                }
                writeOne(msgType, KEY_START);
                readAll();
                if (!text.equals("LgC1")) {
                    System.out.println("kill step 1 retry");
                    continue;
                }
                for (; ; ) {
                    writeOne(msgType, KEY_KEY2);
                    readAll();
                    if (text.equals("0")) {
                        break;
                    } else if (text.equals("h1LL")) {
                        break;
                    }
                }

                writeOne(msgType, KEY_START);
                for (int i = 1; i <= 17; ++i) {
                    writeOne(msgType, KEY_KEY2);
                }
                for (Integer v; ; ) {
                    readAll();
                    try {
                        v = Integer.parseInt(text);
                    } catch (Exception ex) {
                        break;
                    }
                    if (v > 17) {
                        writeOne(msgType, KEY_KEY3);
                    } else if (v < 17) {
                        writeOne(msgType, KEY_KEY3);
                    } else {
                        writeOne(msgType, KEY_START);
                        break;
                    }
                }

                for (; ; ) {
                    readAll();
                    if (text.equals("17")) {
                        System.out.println("kill step 2 retry");
                        break;
                    } else if (text.equals("h1LL")) {
                        return false;
                    } else if (isIdle()) {
                        return true;
                    }
                }

            }
        } else if (Device.ACTION_CLEAN == action) {
            System.out.println("clean");

            for (; ; ) {
                // 强制返回
                for (; ; ) {
                    readAll();
                    if (isIdle()) {
                        break;
                    } else if (isError()) {
                        writeOne(msgType, KEY_START);
                        continue;
                    }
                    writeOne(msgType, KEY_KEY1);
                }

                writeOne(msgType, KEY_MENU);
                for (int i = 1; i <= 3; ++i) {
                    writeOne(msgType, KEY_KEY2);
                }
                writeOne(msgType, KEY_START);
                readAll();
                if (!text.equals("LgC1")) {
                    System.out.println("clean step 1 retry");
                    continue;
                }
                for (; ; ) {
                    writeOne(msgType, KEY_KEY2);
                    readAll();
                    if (text.equals("tcL")) {
                        break;
                    }
                }

                writeOne(msgType, KEY_START);
                for (; ; ) {
                    writeOne(msgType, KEY_START);
                    readAll();
                    try {
                        Integer.parseInt(text);
                    } catch (Exception ex) {
                        break;
                    }
                    if (isIdle()) {
                        writeOne(msgType, KEY_KEY2);
                    } else if (isRunning()) {
                        break;
                    }
                }
                readAll();
                return true;
            }
        }
        return false;
    }

    @Override
    public int poll(OnDeviceStateListener handler) throws IOException {
        readAll();
        int state;
        if (isRunning()) {
            if (isOn(lights1)) {
                state = Device.STATE_RUNNING_S1;
            } else if (isOn(lights2)) {
                state = Device.STATE_RUNNING_S2;
            } else if (isOn(lights3)) {
                state = Device.STATE_RUNNING_S3;
            } else {
                state = Device.STATE_RUNNING;
            }
        } else if (isError()) {
            if (2 == err) {
                state = Device.STATE_ERROR_IE;
            } else if (1 == err || 17 == err) {
                state = Device.STATE_ERROR_DE;
            } else if (4 == err) {
                state = Device.STATE_ERROR_UE;
            } else if (viewStep == 2) {
                state = Device.STATE_ERROR_PAUSE;
            } else {
                state = Device.STATE_ERROR;
            }
        } else {
            if (viewStep == 0xa) {
                state = Device.STATE_IDLE_END;
            } else {
                state = Device.STATE_IDLE;
            }
        }
        handler.onDeviceState(state, text);
        return state;
    }

    /**
     * 判断是否处于错误状态
     */
    private boolean isError() {
        boolean b1 = viewStep == 2;
        boolean b2 = err > 0;
        return b1 || b2;
    }

    /**
     * 判断是否是闲置的
     */
    private boolean isIdle() {
        return !isRunning() && !isError();
    }

    /**
     * 判断是否处于运行状态
     */
    private boolean isRunning() {
        return (viewStep == 6 || viewStep == 7 || viewStep == 8) && err == 0;
    }

    /**
     * 灯是否亮
     */
    private boolean isOn(int light) {
        return light > 0;
    }

    private boolean isOff(int light) {
        return light == 0;
    }

    private void readAll() throws IOException {
        System.out.println("read start");
        // 先清空缓存再读
        for (; ; ) {
            try {
                int len = readOne();
                reader.reset();
                for (int i = len; i > 0; i -= reader.skip(i)) ;
                reader.mark(65536);
                //System.out.println("read " + len + "/" + reader.available());
            } catch (IOException ignored) {
                break;
            }
        }
        // 清空状态
        prevMessage = null;

        Date now = new Date();
        for (; ; ) {
            try {
                int len = readOne();
                // 重置掉多读的字节
                reader.reset();
                for (int i = len; i > 0; i -= reader.skip(i));
                reader.mark(65536);
            } catch (IOException ignored) {
                try {
                    reader.reset();
                } catch (IOException ignored2) {
                }
                if (prevMessage != null) {
                    System.out.println("read finish");
                    return;
                }
                Date now2 = new Date();
                // 30秒无数据
                if (now2.getTime() - now.getTime() > 5000) {
                    throw new IOException("no data");
                }
                System.out.println("wait reading [" + reader.available() + "]");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored2) {
                }
            }

        }
    }

    private int readOne() throws IOException {

        if (reader.available() <= 0) {
            Log.e(TAG,"reader.available() <= 0");
            throw new IOException("unsure");
        }
        byte[] buf = new byte[64];    // 至少读到一个包
        int len = reader.read(buf);
        Log.e(TAG,"len:"+len);
        if (len < buf.length) {
            Log.e(TAG,"len < buf.length");
            throw new IOException("unsure");
        }
        Log.e(TAG,"下一步------------------------------------》");
        //查找数组中是否有0x02
        int off02 = ArrayUtils.indexOf(buf, (byte) 0x02);
        int size = 30;
        if (off02 < 0) {
            handler.onMsgChange(false, ArrayUtils.subarray(buf, 0, len));
            return len;
        } else if (off02 + size > len) {
            handler.onMsgChange(false, ArrayUtils.subarray(buf, 0, off02));
            return off02;
        } else if (buf[off02 + size - 1] != 0x03) {
            handler.onMsgChange(false, ArrayUtils.subarray(buf, 0, off02 + 1));
            return off02 + 1;
        }

        // crc校验
        short crc16_a = crc16.getCrc(buf, off02, size - 3);
        short crc16_msg = (short) (buf[off02 + size - 3] << 8 | (buf[off02 + size - 2] & 0xff));
        if (crc16_a != crc16_msg) {
            handler.onMsgChange(false, ArrayUtils.subarray(buf, 0, off02 + 1));
            return off02 + 1;
        }

        byte[] msg = ArrayUtils.subarray(buf, off02, off02 + size);
        if (!Objects.deepEquals(msg, prevMessage)) {
            handler.onMsgChange(true, msg);
        }
        prevMessage = msg;
        if (msg[1] == 0x00) {
            writeOne(msgType, 0);
        }
        pushState(msg);
        return off02 + size;
    }

    private void writeOne(int t, int key) throws IOException {
        byte[] msg;
        if (t == 1) {
            msg = new byte[]{0x02, 0x06, (byte) (key & 0xff), (byte) (seq & 0xff), (byte) 0x80, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03};
        } else {
            msg = new byte[]{0x02, 0x06, (byte) (key & 0xff), (byte) (seq & 0xff), (byte) 0x80, 0x20, 0, 1, 0, 0, 3};
        }
        short crc16_a = crc16.getCrc(msg, 0, msg.length - 3);
        msg[msg.length - 2] = (byte) (crc16_a >> 8);
        msg[msg.length - 3] = (byte) (crc16_a & 0xff);
        for (int i = 0; i < 12; ++i) {
            writer.write(msg);
            writer.flush();
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        ++seq;
    }

    private void pushState(byte[] msg) {
        if (msg.length == 30) {
            lights1 = (msg[3] & 0x80) >> 7;
            lights2 = (msg[3] & 0x40) >> 6;
            lights3 = (msg[3] & 0x20) >> 5;
            lightadd = ((msg[21] & 0xf0) >> 6 > 0) ? 1 : 0;
            lightlk = (msg[23] & 0x01);
            view = (msg[5] & 0xf0) >> 4;
            viewStep = (msg[3] & 0xf);//获取工作状态值 如msg[3] = E1,viewStep = 1  msg[3] = E6,viewStep = 6
            err = msg[4];
            msgInt = msg[6] & 0xf;
            //屏显(当msgInt = 0xC || 0xD || 0x3)的时候，屏幕显示的就是msg[7]的值
            if (msgInt == 0xc || msgInt == 0xd || msgInt == 0x3) {
                text = "" + msg[7];
            } else {
                text = textTable.get(msg[7]) + textTable.get(msg[8]) + textTable.get(msg[13]) + textTable.get(msg[14]);
            }
        }
    }
}
