package com.xiaolan.device.wash;

import com.xiaolan.device.OnDeviceStateListener;

import java.io.IOException;

public interface DeviceInterface {

    boolean check() throws IOException;

    boolean push(int action) throws IOException;

    int poll(OnDeviceStateListener handler) throws IOException;

}
