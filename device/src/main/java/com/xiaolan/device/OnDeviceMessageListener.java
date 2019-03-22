package com.xiaolan.device;

public interface OnDeviceMessageListener {
    //洗衣机 valid 报文是否有效
    //烘干机 valid 状态是否改变
    void onMsgChange(boolean valid, byte[] msg);
}
