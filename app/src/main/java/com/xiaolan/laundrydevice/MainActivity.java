package com.xiaolan.laundrydevice;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import com.xiaolan.device.OnDeviceMessageListener;
import com.xiaolan.device.OnDeviceStateListener;
import com.xiaolan.device.dryer.DryDevice;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import android_serialport_api.SerialPort;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.reactivex.Observable;
import io.reactivex.ObservableSource;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {

    private DryDevice dryDevice;
    private SerialPort serialPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        try {
//            String path = "/dev/ttyAMA0";
            String path = "/dev/ttyS3";
            serialPort = new SerialPort(new File(path), 9600, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (serialPort != null) {
            dryDevice = new DryDevice(serialPort.getInputStream(), serialPort.getOutputStream(), new OnDeviceMessageListener() {
                @Override
                public void onMsgChange(boolean valid, byte[] msg) {
                    Log.e("222", HexUtils.bytesToHex(msg) + "  " + valid);
                }
            });

            Observable
                    .interval(0, TimeUnit.MILLISECONDS)
                    .delay(400, TimeUnit.MILLISECONDS)
                    .observeOn(Schedulers.io())
                    .flatMap(new Function<Long, ObservableSource<Long>>() {
                        @Override
                        public ObservableSource<Long> apply(Long aLong) throws Exception {
                            dryDevice.poll(new OnDeviceStateListener() {
                                @Override
                                public void onDeviceState(int state, String text) {
                                    Log.e("111", "状态：" + state + "  显示：" + text);
                                }
                            });
                            return Observable.just(aLong);
                        }
                    })
                    .retry(new Predicate<Throwable>() {
                        @Override
                        public boolean test(@NonNull Throwable throwable) throws Exception {
                            return true;
                        }
                    })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<Long>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(Long aLong) {

                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }
    }

    @OnClick({R.id.dry_kill, R.id.dry_noheart, R.id.dry_set})
    public void onClickDry(View view) {
        if (view.getId() == R.id.dry_kill) {
            try {
                dryDevice.kill();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        } else if (view.getId() == R.id.dry_noheart) {
            try {
                dryDevice.push(DryDevice.ACTION_NOHEAT, 6);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        } else {
            try {
                boolean result = dryDevice.initSet();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serialPort != null) {
            serialPort.close();
        }
    }
}
