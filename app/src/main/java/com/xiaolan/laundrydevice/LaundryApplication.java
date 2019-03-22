package com.xiaolan.laundrydevice;

import android.app.Application;

import timber.log.Timber;

public class LaundryApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Timber.plant(new Timber.DebugTree());
    }
}
