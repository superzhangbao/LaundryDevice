package com.xiaolan.laundrydevice;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HexUtils {
    // -------------------------------------------------------
    // 判断奇数或偶数，位运算，最后一位是1则为奇数，为0是偶数
    static public int isOdd(int num) {
        return num & 0x1;
    }

    // -------------------------------------------------------
    static public int HexToInt(String inHex)// Hex字符串转int
    {
        return Integer.parseInt(inHex, 16);
    }

    // -------------------------------------------------------
    static public byte HexToByte(String inHex)// Hex字符串转byte
    {
        return (byte) Integer.parseInt(inHex, 16);
    }

    // -------------------------------------------------------
    static public String Byte2Hex(Byte inByte)// 1字节转2个Hex字符
    {
        return String.format("%02x", inByte).toUpperCase();
    }

    // -------------------------------------------------------
    static public String ByteArrToHex(byte[] inBytArr)// 字节数组转转hex字符串
    {
        StringBuilder strBuilder = new StringBuilder();
        int j = inBytArr.length;
        for (int i = 0; i < j; i++) {
            strBuilder.append(Byte2Hex(inBytArr[i]));
            strBuilder.append(" ");
        }
        return strBuilder.toString();
    }

    // -------------------------------------------------------
    static public String ByteArrToHex(byte[] inBytArr, int offset, int byteCount)// 字节数组转转hex字符串，可选长度
    {
        StringBuilder strBuilder = new StringBuilder();
        int j = byteCount;
        for (int i = offset; i < j; i++) {
            strBuilder.append(Byte2Hex(inBytArr[i]));
        }
        return strBuilder.toString();
    }

    // -------------------------------------------------------
    // 转hex字符串转字节数组
    static public byte[] HexToByteArr(String inHex)// hex字符串转字节数组
    {
        int hexlen = inHex.length();
        byte[] result;
        if (isOdd(hexlen) == 1) {// 奇数
            hexlen++;
            result = new byte[(hexlen / 2)];
            inHex = "0" + inHex;
        } else {// 偶数
            result = new byte[(hexlen / 2)];
        }
        int j = 0;
        for (int i = 0; i < hexlen; i += 2) {
            result[j] = HexToByte(inHex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    public static char bitsToHex(int bit) {
        if (bit >= 0 && bit <= 9) {
            return (char) ((int) '0' + bit);
        } else if (bit >= 10 && bit <= 15) {
            return (char) ((int) 'A' + bit - 10);
        }
        return '-';
    }

    public static String string2HexString(String strPart) {
        StringBuffer hexString = new StringBuffer();
        for (int i = 0; i < strPart.length(); i++) {
            int ch = (int) strPart.charAt(i);
            String strHex = Integer.toHexString(ch);
            hexString.append(strHex);
        }
        return hexString.toString();
    }

    public static String hexString2String(String src) {
        String temp = "";
        for (int i = 0; i < src.length() / 2; i++) {
            temp = temp
                    + (char) Integer.valueOf(src.substring(i * 2, i * 2 + 2),
                    16).byteValue();
        }
        return temp;
    }

    public static byte[] hexStr2Bytes(String src) {
        int m = 0, n = 0;
        int l = src.length() / 2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++) {
            m = i * 2 + 1;
            n = m + 1;
            ret[i] = Byte.decode("0x" + src.substring(i * 2, m)
                    + src.substring(m, n));
        }
        return ret;
    }

    public static String bytesToHex(byte[] bs) {
        if (bs == null)
            return "null";

        StringBuilder sb = new StringBuilder();
        for (byte b : bs) {
            sb.append(bitsToHex((b >> 4) & 0x0F));
            sb.append(bitsToHex(b & 0x0F));
            sb.append(" ");
        }
        return sb.toString();
    }

    public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }

    public static String bytesToHex(byte[] bs, int start, int count) {
        if (bs == null)
            return "null";

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < count; i++) {
            final byte b = bs[i];
            sb.append(bitsToHex((b >> 4) & 0x0F));
            sb.append(bitsToHex(b & 0x0F));
            sb.append(" ");
        }

        if (sb.length() > 0)
            return sb.toString();

        return "null";
    }

    public static String hex2String(String hex) throws Exception {
        String r = bytes2String(hexString2Bytes(hex));
        return r;
    }

    public static byte[] hexString2Bytes(String hex) {
        String value = hex.replace(" ", "");
        if ((value == null) || (value.equals(""))) {
            return null;
        } else if (value.length() % 2 != 0) {
            value = "0" + value;
        }
        value = value.toUpperCase();
        int len = value.length() / 2;
//            int len = hex.split(" ").length;
        byte[] b = new byte[len];
        char[] hc = value.toCharArray();
        for (int i = 0; i < len; i++) {
            int p = 2 * i;
            b[i] = (byte) (charToByte(hc[p]) << 4 | charToByte(hc[p + 1]));
        }
        return b;

    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static String bytes2String(byte[] b) throws Exception {
        String r = new String(b, "UTF-8");
        return r;
    }

    public static int valueFromHex(char hex) throws Exception {
        if (hex >= '0' && hex <= '9')
            return (int) (hex - '0');
        if (hex >= 'a' && hex <= 'f')
            return (int) (hex - 'a' + 10);
        if (hex >= 'A' && hex <= 'F')
            return (int) (hex - 'A' + 10);
        throw new Exception("failed to convert hex.");
    }

    public static byte[] bytesFromHex(String str, int maxSize) throws Throwable {
        ByteBuffer bb = ByteBuffer.allocate(maxSize);
        // fix : order bug
        bb.order(ByteOrder.LITTLE_ENDIAN);

        char[] src = str.toCharArray();
        // mLogger.addLog(Utils.bytesToHex(src));

        for (int i = 0; i < src.length; i++) {
            if (src[i] == 0x20)
                continue;
            if (i + 1 < src.length) {
                int hi = valueFromHex(src[i]);
                int lo = valueFromHex(src[i + 1]);
                bb.put((byte) (hi * 16 + lo));
                i++;
            } else {
                throw new Exception("failed to convert hex string.");
            }
        }

        if (bb.hasArray())
            return bb.array();
        return null;
    }

    public static String byteToHexString(byte src) {
        StringBuilder ret = new StringBuilder("");

        String hex = Integer.toHexString(src & 0xFF);
        if (hex.length() < 2) {
            hex = "0" + hex;
        }
        ret.append(hex);
        ret.append(" ");
        return ret.toString().toUpperCase();
//		return "0x" + ret.toUpperCase();
    }

    public static String bytes2HexString(byte[] b) {
        String ret = "";
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
//            ret += " 0x" + hex.toUpperCase();
            ret += hex.toUpperCase() + " ";
        }
        return ret;
    }

    public static String tenToHexStr(int Num) {
        String Hex = Integer.toHexString(Num);
        if (Hex.length() % 2 == 0) {
            return Hex;
        } else {
            return "0" + Hex;
        }
    }
}
