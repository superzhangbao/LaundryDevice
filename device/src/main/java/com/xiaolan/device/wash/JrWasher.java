package com.xiaolan.device.wash;

import android.util.Log;

import com.xiaolan.device.OnDeviceMessageListener;
import com.xiaolan.device.OnDeviceStateListener;

import org.apache.commons.lang3.ArrayUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JrWasher implements DeviceInterface {

    private final static CRC16 crc16 = new CRC16();
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
    private final static int KEY_MENU = 13;

    private final static int HIS_SIZE = 12;
    private static final String TAG = "JrWasher";

    private String text;
    private String text_running;
    private int light1 = 0;
    private int light2 = 0;
    private int light3 = 0;
    private int light4 = 0;
    private int light5 = 0;     // 加强
    private int lightst = 0;    // 开始
    private int lights1 = 0;    // 第1步
    private int lights2 = 0;    // 第2步
    private int lights3 = 0;    // 第3步
    private int lightlk = 0;    // 锁
    private int lighttxt = 0;
    private List<byte[]> his = new LinkedList<>();
    private int seq = 2;
    private final OnDeviceMessageListener handler;
    private final BufferedInputStream reader;
    private final BufferedOutputStream writer;

    public JrWasher(BufferedInputStream reader, BufferedOutputStream writer, OnDeviceMessageListener handler) {
        this.reader = reader;
        this.writer = writer;
        this.handler = handler;
    }

    @Override
    public boolean check() throws IOException {
        for (; !his.isEmpty(); ) dequeueState();
        int len = readOne();
        // 如果不成功就要返回原来的位置
        reader.reset();
        if (his.isEmpty()) {
            return false;
        }
        for (int i = len; i > 0; i -= reader.skip(i)) ;
        reader.mark(65536);
        return true;
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
                key = KEY_KEY1;
            } else if (action == Device.ACTION_COLD) {
                key = KEY_KEY2;
            } else if (action == Device.ACTION_DELICATES) {
                key = KEY_KEY3;
            } else if (action == Device.ACTION_SUPER_HOT) {
                key = KEY_KEY1;
                add = true;//代表是加强洗
            } else if (action == Device.ACTION_SUPER_WARM) {
                key = KEY_KEY1;
                add = true;
            } else if (action == Device.ACTION_SUPER_COLD) {
                key = KEY_KEY2;
                add = true;
            } else if (action == Device.ACTION_SUPER_DELICATES) {
                key = KEY_KEY3;
                add = true;
            } else {
                return false;
            }

            for (; ; ) {
                // 强制返回
                for (; ; ) {
                    writeAll(KEY_KEY1);
                    readAll();
                    if (isRunning()) {
                        // 正在运行
                        return false;
                    } else if (isError()) {
                        writeAll(KEY_START);
                    } else if (isOn(light1) && isFlash(lightst)) {
                        break;
                    }
                }

                writeAll(key);
                readAll();
                Map<Integer, Integer> l = new HashMap<>();
                l.put(KEY_KEY1, light1);
                l.put(KEY_KEY2, light2);
                l.put(KEY_KEY3, light3);
                l.put(KEY_KEY4, light4);

                if (isRunning()) {
                    return false;
                } else if (isError()) {
                    return false;
                } else if (!(isOn(l.get(key)) && isFlash(lightst))) {
                    continue;
                }

                if (add) {
                    writeAll(KEY_SUPER);
                    readAll();
                    if (!(isOn(light5))) {
                        continue;
                    }
                }

                break;
            }

            // 开始
            for (; ; ) {
                writeAll(KEY_START);
                readAll();
                if (isRunning()) {
                    return true;
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ignored) {
                }
            }
        } else if (action == Device.ACTION_START) {//------------------------------------->>start指令
            System.out.println("start");
            writeAll(KEY_START);
            return true;
        } else if (action == Device.ACTION_KILL) {
            System.out.println("kill");

            for (; ; ) {
                // 强制返回
                for (; ; ) {
                    readAll();
                    if (isRunning()) {
                        // 正在运行
                        break;
                    } else if (isError()) {
                        writeAll(KEY_START);
                        continue;
                    } else if (isOn(light1) && isFlash(lightst)) {
                        break;
                    } else if (isIdle()) {
                        return false;
                    }
                    writeAll(KEY_KEY1);
                }

                writeAll(KEY_MENU);
                for (int i = 1; i <= 3; ++i) {
                    writeAll(KEY_KEY2);
                }
                writeAll(KEY_START);
                readAll();
                if (!text.equals("LgC1")) {
                    System.out.println("kill step 1 retry");
                    continue;
                }
                for (; ; ) {
                    writeAll(KEY_KEY2);
                    readAll();
                    if (text.equals("h1LL")) {
                        break;
                    }
                }
                writeAll(KEY_START);
                for (int i = 1; i <= 17; ++i) {
                    writeAll(KEY_KEY2);
                }
                for (Integer v; ; ) {
                    readAll();
                    try {
                        v = Integer.parseInt(text);
                    } catch (Exception ex) {
                        break;
                    }
                    if (v > 17) {
                        writeAll(KEY_KEY3);
                    } else if (v < 17) {
                        writeAll(KEY_KEY3);
                    } else {
                        writeAll(KEY_START);
                    }
                }

                for (; ; ) {
                    readAll();
                    if (text.equals("h1LL")) {
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
                    if (isRunning()) {
                        // 正在运行
                        return false;
                    } else if (isError()) {
                        writeAll(KEY_START);
                        continue;
                    } else if (isOn(light1) && isFlash(lightst)) {
                        break;
                    }
                    writeAll(KEY_KEY1);
                }

                writeAll(KEY_MENU);
                for (int i = 1; i <= 3; ++i) {
                    writeAll(KEY_KEY2);
                }
                writeAll(KEY_START);
                readAll();
                if (!text.equals("LgC1")) {
                    System.out.println("clean step 1 retry");
                    continue;
                }
                for (; ; ) {
                    writeAll(KEY_KEY2);
                    readAll();
                    if (text.equals("tCL")) {
                        break;
                    }
                }

                writeAll(KEY_START);
                readAll();
                if (isRunning()) {
                    return true;
                }
                for (; ; ) {
                    writeAll(KEY_START);
                    readAll();
                    try {
                        Integer.parseInt(text);
                    } catch (Exception ex) {
                        break;
                    }
                    if (isIdle()) {
                        writeAll(KEY_KEY2);
                    } else if (isRunning()) {
                        break;
                    }
                }

                return true;
            }
        }
        return false;
    }

    @Override
    public int poll(OnDeviceStateListener handler) throws IOException {
        readAll();
        int state;
        String text = this.text;
        if (isRunning()) {
            if (isFlash(lights1)) {
                state = Device.STATE_RUNNING_S1;
            } else if (isFlash(lights2)) {
                state = Device.STATE_RUNNING_S2;
            } else if (isFlash(lights3)) {
                state = Device.STATE_RUNNING_S3;
            } else {
                state = Device.STATE_RUNNING;
            }
            try {
                int time = Integer.parseInt(text);
                text = "" + ((time / 100) * 60 + (time % 100));
            } catch (Exception ignored) {
            }
        } else if (isError()) {
            if ("1E".equals(text)) {
                state = Device.STATE_ERROR_IE;
            } else if ("0E".equals(text)) {
                state = Device.STATE_ERROR_OE;
            } else if ("dE".equals(text)) {
                state = Device.STATE_ERROR_DE;
            } else if ("UE".equals(text)) {
                state = Device.STATE_ERROR_UE;
            } else {
                state = Device.STATE_ERROR;
            }
        } else {
            if (text.equals("End")) {
                state = Device.STATE_IDLE_END;
            } else {
                state = Device.STATE_IDLE;
            }
        }
        handler.onDeviceState(state, text);
        return state;
    }

    private boolean isError() {
        boolean b1 = isOff(lightst)
                && isOff(light1) && isOff(light2) && isOff(light3) && isOff(light4) && isOff(light5)
                && isOff(lights1) && isOff(lights2) && isOff(lights3)
                && isFlash(lighttxt);
        boolean b2;
        try {
            Integer.parseInt(text);
            b2 = false;
        } catch (Exception ignored) {
            b2 = true;
        }
        return b1 && b2;
    }

    private boolean isIdle() {
        return !isRunning() && !isError();
    }

    private boolean isRunning() {
        return isOn(lightst) && (isFlash(lights1) || isFlash(lights2) || isFlash(lights3));
    }

    private boolean isOn(int light) {
        return light > HIS_SIZE - 3;
    }

    private boolean isOff(int light) {
        return light < 3;
    }

    private boolean isFlash(int light) {
        return !isOn(light) && !isOff(light);
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
            } catch (IOException ignored) {
                break;
            }
        }
        // 清空状态
        for (; !his.isEmpty(); ) dequeueState();

        Date now = new Date();
        for (; ; ) {
            try {
                int len = readOne();
                // 重置掉多读的字节
                reader.reset();
                for (int i = len; i > 0; i -= reader.skip(i)) ;
                reader.mark(65536);
            } catch (IOException ignored) {
                try {
                    reader.reset();
                } catch (IOException ignored2) {
                }
                Date now2 = new Date();
                // 5秒无数据
                if (now2.getTime() - now.getTime() > 5000) {
                    throw new IOException("no data");
                }
                System.out.println("wait reading [" + " " + reader.available() + "," + his.size() + "]");
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored2) {
                }
            }
            if (his.size() >= HIS_SIZE) {
                System.out.println("read finish");
                return;
            }
        }
    }

    private void writeAll(int key) {
        for (int i = 0; i < 12; ++i) {
            try {
                writeOne(writer, key);
            } catch (IOException ignored) {
                break;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
        seq++;
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
        Log.e("JrWasher","下一步------------------------------------》");
        int off02 = ArrayUtils.indexOf(buf, (byte) 0x02);
        int size = 23;
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
        short crc16_msg = (short) (buf[off02 + 20] << 8 | (buf[off02 + 21] & 0xff));
        if (crc16_a != crc16_msg) {
            handler.onMsgChange(false, ArrayUtils.subarray(buf, 0, off02 + 1));
            return off02 + 1;
        }

        byte[] msg = ArrayUtils.subarray(buf, off02, off02 + size);
        if (his.isEmpty() || !Objects.deepEquals(msg, his.get(his.size() - 1))) {
            handler.onMsgChange(true, msg);
        }
        enqueueState(msg);
        if (his.size() > HIS_SIZE) dequeueState();
        return off02 + size;
    }

    private void writeOne(BufferedOutputStream writer, int key) throws IOException {
        byte[] msg = {0x02, 0x06, (byte) (key & 0xff), (byte) (seq & 0xff), (byte) 0x80, 0x20, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03};
        short crc16_a = crc16.getCrc(msg, 0, msg.length - 3);
        msg[msg.length - 2] = (byte) (crc16_a >> 8);
        msg[msg.length - 3] = (byte) (crc16_a & 0xff);
        writer.write(msg);
        writer.flush();
    }

    private void enqueueState(byte[] msg) {
        his.add(msg);

        int l1 = msg[5];
        int l2 = msg[10];
        light1 += (l2 & 0x20) >> 5;
        light2 += (l2 & 0x40) >> 6;
        light3 += (l1 & 0x04) >> 2;
        light4 += (l1 & 0x08) >> 3;
        light5 += (l1 & 0x10) >> 4;
        lightst += (l2 & 0x01);
        lights1 += (l2 & 0x02) >> 1;
        lights2 += (l2 & 0x04) >> 2;
        lights3 += (l2 & 0x08) >> 3;
        lightlk += (l2 & 0x10) >> 4;
        String t = textTable.get(msg[9]) + textTable.get(msg[8]) + textTable.get(msg[7]) + textTable.get(msg[6]);
        if (t.length() > 0) {
            text = t;
            lighttxt += 1;
        }
    }

    private void dequeueState() {
        if (his.isEmpty()) {
            return;
        }

        byte[] msg = his.remove(0);
        int l1 = msg[5];
        int l2 = msg[10];
        light1 -= (l2 & 0x20) >> 5;
        light2 -= (l2 & 0x40) >> 6;
        light3 -= (l1 & 0x04) >> 2;
        light4 -= (l1 & 0x08) >> 3;
        light5 -= (l1 & 0x10) >> 4;
        lightst -= (l2 & 0x01);
        lights1 -= (l2 & 0x02) >> 1;
        lights2 -= (l2 & 0x04) >> 2;
        lights3 -= (l2 & 0x08) >> 3;
        lightlk -= (l2 & 0x10) >> 4;
        String t = textTable.get(msg[9]) + textTable.get(msg[8]) + textTable.get(msg[7]) + textTable.get(msg[6]);
        if (t.length() > 0) {
            lighttxt -= 1;
        }
    }
}
