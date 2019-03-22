package com.xiaolan.device.dryer;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.xiaolan.device.OnDeviceMessageListener;
import com.xiaolan.device.OnDeviceStateListener;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class DryDevice {

    private static final long MAX_CLICK = 100;
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

    /*烘干机 空闲状态*/
    public static final int STATE_FREE = 0;
    /*烘干机 运行状态*/
    public static final int STATE_RUN = 1;
    /*烘干机 开门状态*/
    public static final int STATE_OPEN = 2;
    /*烘干机 结束状态*/
    public static final int STATE_END = 3;
    /*烘干机 未知状态*/
    public static final int STATE_UNKNOWN = 4;

    /*灯 不亮*/
    private static final int LAMP_CLOSE = 0;
    /*灯 闪烁*/
    private static final int LAMP_GLINT = 1;
    /*灯 长亮*/
    private static final int LAMP_BRIGHT = -1;
    /*灯 过度状态*/
    private static final int LAMP_NORMAL = -2;

    public static final int ACTION_NOHEAT = 8;
    public static final int ACTION_LOW = 4;
    public static final int ACTION_MED = 2;
    public static final int ACTION_HIGH = 1;

    private final int KEY_HIGH = 1;
    private final int KEY_MED = 2;
    private final int KEY_LOW = 4;
    private final int KEY_NOHEAT = 8;
    private final int KEY_START = 16;
    private final int KEY_SET = 5;

    private int lamp_1;
    private int lamp_2;
    private int lamp_3;
    private int lamp_4;
    private int lamp_5;
    private int lamp_6;
    private int lamp_7;

    private StringBuilder showStr;
    private BufferedInputStream readBuffer;
    private BufferedOutputStream writeBuffer;
    private OnDeviceMessageListener handler;
    private int state;
    private int seq = 1;
    private long click = 0;

    public DryDevice(InputStream input, OutputStream output, OnDeviceMessageListener handler) {
        this.showStr = new StringBuilder("");
        this.handler = handler;
        this.readBuffer = new BufferedInputStream(input, 1024 * 64);
        this.writeBuffer = new BufferedOutputStream(output, 1024 * 64);
    }

    private void readAll() {
        for (; ; ) {
            try {
                readOne();
            } catch (IOException e) {
                break;
            }
        }
    }

    private void readOne() throws IOException {
        if (readBuffer.available() <= 0) {
            throw new IOException("unsure");
        }
        byte[] buf = new byte[32];
        int len = readBuffer.read(buf);
        if (len < buf.length) {
            throw new IOException("unsure");
        }
        int size = 14;
        int off7F = ArrayUtils.indexOf(buf, (byte) 0x7F);
        while (off7F >= 0) {
            if (len >= off7F + size && buf[off7F + 1] == 0x70 && (buf[off7F + 2] + buf[off7F + 3] + buf[off7F + 4] + buf[off7F + 5]) != 0) {
                byte[] valid = ArrayUtils.subarray(buf, off7F, off7F + size);
                boolean isDiffWord = dryerMsg(valid);
                int stateNow = dryerState();
                handler.onMsgChange(isDiffWord || stateNow != state, valid);
                state = stateNow;
            }
            off7F = ArrayUtils.indexOf(buf, (byte) 0x7F, off7F + 1);
        }
    }

    private int dryerState() {
        if (TextUtils.equals(showStr.toString(), "End")) {
            //结束状态
            return STATE_END;
        } else if ((lamp_1 == LAMP_GLINT || lamp_2 == LAMP_GLINT)
                && lamp_3 == LAMP_BRIGHT
                && (lamp_4 == LAMP_BRIGHT || lamp_5 == LAMP_BRIGHT || lamp_6 == LAMP_BRIGHT || lamp_7 == LAMP_BRIGHT)) {
            //运行状态
            return STATE_RUN;
        } else if (lamp_1 == LAMP_CLOSE
                && lamp_2 == LAMP_CLOSE
                && (lamp_4 != LAMP_CLOSE || lamp_5 != LAMP_CLOSE || lamp_6 != LAMP_CLOSE || lamp_7 != LAMP_CLOSE)) {
            //空闲状态
            return STATE_FREE;
        } else if ((lamp_1 == LAMP_BRIGHT || lamp_2 == LAMP_BRIGHT)
                && lamp_3 == LAMP_GLINT
                && (lamp_4 == LAMP_BRIGHT || lamp_5 == LAMP_BRIGHT || lamp_6 == LAMP_BRIGHT || lamp_7 == LAMP_BRIGHT)
                && NumberUtils.isCreatable(showStr.toString())) {
            //开门状态
            return STATE_OPEN;
        } else {
            //未知状态
            return STATE_UNKNOWN;
        }
    }

    private boolean dryerMsg(@NonNull byte[] buf) {
        StringBuilder showWord = new StringBuilder("");
        showWord.append(textTable.get(buf[2])).append(textTable.get(buf[3])).append(textTable.get(buf[4])).append(textTable.get(buf[5]));

        lamp_1 = lampState(buf[7]);
        lamp_2 = lampState(buf[8]);
        lamp_3 = lampState(buf[9]);
        lamp_4 = lampState(buf[10]);
        lamp_5 = lampState(buf[11]);
        lamp_6 = lampState(buf[12]);
        lamp_7 = lampState(buf[13]);

        boolean isDiff = !TextUtils.equals(showStr.toString(), showWord.toString());
        showStr.delete(0, showStr.length());
        showStr.append(showWord);

        return isDiff;
    }

    private int lampState(byte b) {
        if (b == 0x08) {
            return LAMP_BRIGHT;
        } else if (b == 0x00) {
            return LAMP_CLOSE;
        } else if (b == 0x01 || b == 0x07) {
            return LAMP_NORMAL;
        } else {
            return LAMP_GLINT;
        }
    }

    private void writeOne(int key) throws IOException, InterruptedException {
        if (state != STATE_OPEN && ++click > MAX_CLICK) {
            throw new IOException("click error");
        }
        byte[] strByte = new byte[]{0x7F, (byte) (seq & 0xff), (byte) 0xF0, (byte) (key & 0xff)};
        for (int i = 0; i < 12; ++i) {
            writeBuffer.write(strByte);
            writeBuffer.flush();
            Thread.sleep(100);
        }
        ++seq;
    }

    private void coin() throws IOException, InterruptedException {
        if (++click > MAX_CLICK) {
            throw new IOException("click error");
        }
        byte[] strByte = new byte[]{0x7F, (byte) (seq & 0xff), (byte) 0xF1, 0x00};
        for (int i = 0; i < 12; ++i) {
            writeBuffer.write(strByte);
            writeBuffer.flush();
            Thread.sleep(100);
        }
        ++seq;
    }

    public synchronized void poll(OnDeviceStateListener handler) throws IOException, InterruptedException {
        readAll();
        handler.onDeviceState(state, showStr.toString());
        if (state == STATE_OPEN) {
            writeOne(KEY_START);
            Thread.sleep(1000);
        }
    }

    public boolean kill() throws IOException, InterruptedException {
        synchronized (DryDevice.class) {
            click = 0;
            for (; ; ) {
                for (; ; ) {
                    writeOne(KEY_HIGH);
                    readAll();
                    if (NumberUtils.isCreatable(showStr.toString())) {
                        break;
                    }
                }

                writeOne(KEY_SET);
                for (int i = 0; i < 3; i++) {
                    writeOne(KEY_MED);
                }
                writeOne(KEY_START);
                readAll();
                if (!TextUtils.equals(showStr.toString(), "LgC1")) {
                    continue;
                }

                for (; ; ) {
                    writeOne(KEY_MED);
                    readAll();
                    if (TextUtils.equals(showStr.toString(), "h1LL")) {
                        break;
                    }
                }

                writeOne(KEY_START);
                for (int i = 0; i < 17; i++) {
                    writeOne(KEY_MED);
                }
                for (Integer v; ; ) {
                    readAll();
                    try {
                        v = Integer.parseInt(showStr.toString());
                    } catch (Exception ex) {
                        break;
                    }
                    if (v > 17) {
                        writeOne(KEY_LOW);
                    } else if (v < 17) {
                        writeOne(KEY_MED);
                    } else {
                        writeOne(KEY_START);
                    }
                }

                readAll();
                if (TextUtils.equals(showStr.toString(), "End")) {
                    return true;
                }
            }
        }
    }

    public boolean push(int action, int coin) throws IOException, InterruptedException {
        synchronized (DryDevice.class) {
            if (state != STATE_FREE) return false;
            coin = coin > 9 ? 9 : coin;
            String time = String.valueOf(coin * 10 / 60 * 100 + coin * 10 % 60);
            click = 0;
            int value;
            for (; ; ) {
                for (; ; ) {
                    readAll();
                    try {
                        value = Integer.parseInt(showStr.toString());
                    } catch (Exception ex) {
                        break;
                    }

                    if ((value / 100 * 6 + value % 100 / 10) >= coin
                            && (lamp_4 + lamp_5 + lamp_6 + lamp_7) > 3) {
                        break;
                    }
                    coin();
                }

                for (; ; ) {
                    readAll();
                    if ((lamp_1 == LAMP_GLINT || lamp_2 == LAMP_GLINT)
                            && lamp_3 == LAMP_BRIGHT
                            && (lamp_4 == LAMP_BRIGHT || lamp_5 == LAMP_BRIGHT || lamp_6 == LAMP_BRIGHT || lamp_7 == LAMP_BRIGHT)) {
                        return true;
                    } else if (lamp_1 == LAMP_BRIGHT
                            && lamp_2 == LAMP_BRIGHT
                            && (lamp_4 == LAMP_BRIGHT || lamp_5 == LAMP_BRIGHT || lamp_6 == LAMP_BRIGHT || lamp_7 == LAMP_BRIGHT)
                            && TextUtils.equals(showStr.toString(), time)) {
                        writeOne(KEY_START);
                        Thread.sleep(800);
                    } else if (TextUtils.equals(showStr.toString(), time)) {
                        writeOne(action);
                    }
                }
            }
        }
    }

    public boolean initSet() throws IOException, InterruptedException {
        synchronized (DryDevice.class) {
            click = -500;
            return (mode() && price() && timeUnit() && timeDefault() && coin1() && coin2());
        }
    }

    private boolean mode() throws IOException, InterruptedException {
        for (; ; ) {
            for (; ; ) {
                writeOne(KEY_HIGH);
                readAll();
                if (NumberUtils.isCreatable(showStr.toString())) {
                    break;
                }
            }

            writeOne(KEY_SET);
            for (int i = 0; i < 3; i++) {
                writeOne(KEY_MED);
            }
            writeOne(KEY_START);
            readAll();
            if (!TextUtils.equals(showStr.toString(), "LgC1")) {
                continue;
            }

            for (; ; ) {
                if (TextUtils.equals(showStr.toString(), "LgC1")) {
                    writeOne(KEY_HIGH);
                } else if (TextUtils.equals(showStr.toString(), "tE5t")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "5tUP")) {
                    writeOne(KEY_START);
                } else if (TextUtils.equals(showStr.toString(), "r9Pr")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "PdtP")) {
                    writeOne(KEY_START);
                } else if (TextUtils.equals(showStr.toString(), "FrEE")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "PA4")) {
                    writeOne(KEY_START);
                } else {
                    writeOne(KEY_HIGH);
                }
                readAll();

                if (NumberUtils.isCreatable(showStr.toString())
                        && lamp_1 == LAMP_CLOSE
                        && lamp_2 == LAMP_CLOSE
                        && lamp_3 == LAMP_CLOSE) {
                    return true;
                }
            }
        }
    }

    private boolean price() throws IOException, InterruptedException {
        for (; ; ) {
            writeOne(KEY_SET);
            for (int i = 0; i < 3; i++) {
                writeOne(KEY_MED);
            }
            writeOne(KEY_START);
            readAll();
            if (!TextUtils.equals(showStr.toString(), "LgC1")) {
                continue;
            }

            for (; ; ) {
                if (TextUtils.equals(showStr.toString(), "LgC1")) {
                    writeOne(KEY_HIGH);
                } else if (TextUtils.equals(showStr.toString(), "tE5t")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "5tUP")) {
                    writeOne(KEY_START);
                } else if (TextUtils.equals(showStr.toString(), "r9Pr")) {
                    writeOne(KEY_START);
                } else {
                    writeOne(KEY_HIGH);
                }
                readAll();

                if (NumberUtils.isCreatable(showStr.toString())) {
                    break;
                }
            }


            for (Integer v; ; ) {
                readAll();
                try {
                    v = Integer.parseInt(showStr.toString()) % 100;
                } catch (Exception ex) {
                    break;
                }
                if (v < 1) {
                    writeOne(KEY_MED);
                } else if (v > 1) {
                    writeOne(KEY_LOW);
                } else {
                    writeOne(KEY_START);
                    return true;
                }
            }
        }
    }

    private boolean timeUnit() throws IOException, InterruptedException {
        for (; ; ) {
            for (; ; ) {
                if (!TextUtils.equals(showStr.toString(), "5tUP")) {
                    writeOne(KEY_HIGH);
                    readAll();
                } else {
                    break;
                }
            }

            writeOne(KEY_START);
            for (; ; ) {
                readAll();
                if (TextUtils.equals(showStr.toString(), "r9Pr")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "toPt")) {
                    writeOne(KEY_START);
                    break;
                } else {
                    writeOne(KEY_MED);
                }
            }

            for (; ; ) {
                readAll();
                int value;
                try {
                    value = Integer.parseInt(showStr.toString());
                } catch (Exception ex) {
                    break;
                }
                if (value < 10) {
                    writeOne(KEY_MED);
                } else if (value > 10) {
                    writeOne(KEY_LOW);
                } else {
                    writeOne(KEY_START);
                    break;
                }
            }

            readAll();
            if (TextUtils.equals(showStr.toString(), "bEEP")) {
                return true;
            }
        }
    }

    private boolean timeDefault() throws IOException, InterruptedException {
        for (; ; ) {
            for (; ; ) {
                if (!TextUtils.equals(showStr.toString(), "5tUP")) {
                    writeOne(KEY_HIGH);
                    readAll();
                } else {
                    break;
                }
            }

            writeOne(KEY_START);
            for (; ; ) {
                readAll();
                if (TextUtils.equals(showStr.toString(), "r9Pr")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "rPdC")) {
                    writeOne(KEY_START);
                    break;
                } else {
                    writeOne(KEY_MED);
                }
            }

            for (; ; ) {
                readAll();
                int value;
                try {
                    value = Integer.parseInt(showStr.toString());
                } catch (Exception ex) {
                    break;
                }
                if (value < 10) {
                    writeOne(KEY_MED);
                } else if (value > 10) {
                    writeOne(KEY_LOW);
                } else {
                    writeOne(KEY_START);
                    break;
                }
            }

            readAll();
            if (TextUtils.equals(showStr.toString(), "5PdC")) {
                return true;
            }
        }
    }

    private boolean coin1() throws IOException, InterruptedException {
        for (; ; ) {
            for (; ; ) {
                if (!TextUtils.equals(showStr.toString(), "5tUP")) {
                    writeOne(KEY_HIGH);
                    readAll();
                } else {
                    break;
                }
            }

            writeOne(KEY_START);
            for (; ; ) {
                readAll();
                if (TextUtils.equals(showStr.toString(), "r9Pr")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "Co1")) {
                    writeOne(KEY_START);
                    break;
                } else {
                    writeOne(KEY_MED);
                }
            }

            for (; ; ) {
                readAll();
                int value;
                try {
                    value = Integer.parseInt(showStr.toString());
                    if (value < 10) {
                        value *= 100;
                    }
                } catch (Exception ex) {
                    break;
                }
                if (value < 100) {
                    writeOne(KEY_MED);
                } else if (value > 100) {
                    writeOne(KEY_LOW);
                } else {
                    writeOne(KEY_START);
                    break;
                }
            }

            readAll();
            if (TextUtils.equals(showStr.toString(), "Co2")) {
                return true;
            }
        }
    }

    private boolean coin2() throws IOException, InterruptedException {
        for (; ; ) {
            for (; ; ) {
                if (lamp_1 == LAMP_CLOSE
                        && lamp_2 == LAMP_CLOSE
                        && lamp_3 == LAMP_CLOSE
                        && (TextUtils.equals(showStr.toString(), "1") || TextUtils.equals(showStr.toString(), "100"))) {
                    return true;
                } else if (!TextUtils.equals(showStr.toString(), "5tUP")) {
                    writeOne(KEY_HIGH);
                    readAll();
                } else {
                    break;
                }
            }

            writeOne(KEY_START);
            for (; ; ) {
                readAll();
                if (TextUtils.equals(showStr.toString(), "r9Pr")) {
                    writeOne(KEY_MED);
                } else if (TextUtils.equals(showStr.toString(), "Co2")) {
                    writeOne(KEY_START);
                    break;
                } else {
                    writeOne(KEY_MED);
                }
            }

            for (; ; ) {
                readAll();
                int value;
                try {
                    value = Integer.parseInt(showStr.toString());
                    if (value < 10) {
                        value *= 100;
                    }
                } catch (Exception ex) {
                    break;
                }
                if (value < 100) {
                    writeOne(KEY_MED);
                } else if (value > 100) {
                    writeOne(KEY_LOW);
                } else {
                    writeOne(KEY_START);
                    break;
                }
            }

            readAll();
            if (TextUtils.equals(showStr.toString(), "P1Po")) {
                for (; ; ) {
                    writeOne(KEY_HIGH);
                    readAll();
                    if (TextUtils.equals(showStr.toString(), "5tUP")) {
                        writeOne(KEY_HIGH);
                        readAll();
                        break;
                    }
                }
            }
        }
    }
}
