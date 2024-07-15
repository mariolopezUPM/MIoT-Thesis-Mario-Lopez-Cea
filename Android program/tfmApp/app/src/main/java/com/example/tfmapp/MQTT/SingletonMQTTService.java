package com.example.tfmapp.MQTT;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.bouncycastle.util.Bytes;

public final class SingletonMQTTService {

    private static SingletonMQTTService instance;
    public String value;

    public ServiceConnection serviceConnection = null;
    public MqttService mqttService = null;

    public boolean isBound = false;

    private SingletonMQTTService(byte[] sharedKey) {
        serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                MqttService.LocalBinder binder = (MqttService.LocalBinder) service;
                mqttService = binder.getService(sharedKey);
                mqttService.setKey(sharedKey);
                isBound = true;

            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                isBound = false;
            }
        };



    }

    public static SingletonMQTTService getInstance(byte[] sharedKey) {
        // if was not already created the instance will be created
        if (instance == null) {
            instance = new SingletonMQTTService(sharedKey);
        }
        return instance;
    }
}
