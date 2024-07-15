package com.example.tfmapp.MQTT;

import static com.example.tfmapp.MQTT.TopicsMQTT.topicSubCommand;
import static com.example.tfmapp.MQTT.TopicsMQTT.topicSubContact;
import static com.example.tfmapp.MQTT.TopicsMQTT.topicSubData;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MqttService extends Service {

    private static final String TAG = "MqttService";
    private static final String BROKER = "ssl://fe16d2d4407449d097cd113952053567.s1.eu.hivemq.cloud:8883";
    private static final String ID = "ID_TFM";
    private static final String USER = "tfmBroker";
    private static final String PASSWORD = "102001mLc";



    private MqttClient mqttClient;
    private MqttConnectOptions mqttConnectOptions;
    private byte[] sharedKey = null;


    public class LocalBinder extends Binder {
        public MqttService getService(byte[] key) {
            sharedKey = key;
            return MqttService.this;
        }
    }

    private final IBinder binder = new LocalBinder();

    public void setKey(byte[] key){
        sharedKey=key;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeMqtt();
    }

    private void initializeMqtt() {
        try {
            MemoryPersistence persistence = new MemoryPersistence();
            mqttClient = new MqttClient(BROKER, ID, persistence);

            mqttConnectOptions = new MqttConnectOptions();
            mqttConnectOptions.setUserName(USER);
            mqttConnectOptions.setPassword(PASSWORD.toCharArray());
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setCleanSession(true);
            mqttConnectOptions.setKeepAliveInterval(60);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.d(TAG, "Connection lost", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try{
                        byte[] decryptedData = decryptData(message.getPayload(), sharedKey);
                        String payload = new String(decryptedData, "UTF-8");
                        Log.d(TAG, "Message arrived: Topic: " + topic + ", Message: " + payload);

                        if(Objects.equals(topic, topicSubContact)){
                            Intent intent = new Intent("MQTTContactMessage");
                            intent.putExtra("topic", topic);
                            intent.putExtra("message", payload);
                            sendBroadcast(intent);
                        }else{
                            Intent intent = new Intent("MQTTMessage");
                            intent.putExtra("topic", topic);
                            intent.putExtra("message", payload);
                            sendBroadcast(intent);
                        }


                    }catch (Exception e){
                        Log.e(TAG, "Error parsin message recieved");
                        e.printStackTrace();
                    }

                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    Log.d(TAG, "Delivery complete");
                }
            });

            connectAndSubscribe();

        } catch (MqttException e) {
            Log.e(TAG, "Error initializing MQTT", e);
        }
    }

    private void connectAndSubscribe() {
        try {
            mqttClient.connect(mqttConnectOptions);
            Log.d(TAG, "Connected to MQTT broker");

            mqttClient.subscribe(topicSubData, 1);
            mqttClient.subscribe(topicSubCommand, 1);


            Log.d(TAG, "Subscribed to topics");

        } catch (MqttException e) {
            Log.e(TAG, "Error connecting or subscribing to MQTT broker", e);
        }
    }

    public void publishMessage(String topic, String message, boolean retain) {
        try {
            MqttMessage mqttMessage = new MqttMessage();
            if(retain){
                mqttMessage.setRetained(true);
            }
            mqttMessage.setPayload(encryptData(message.getBytes(), sharedKey));
            mqttClient.publish(topic, mqttMessage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteRetainedMessage(String topic) {
        try {
            MqttMessage mqttMessage = new MqttMessage();
            mqttMessage.setPayload(new byte[0]); // Empty payload
            mqttMessage.setRetained(true);
            mqttClient.publish(topic, mqttMessage);
            Log.d(TAG, "Deleted retained message for topic: " + topic);
        } catch (Exception e) {
            Log.e(TAG, "Error deleting retained message", e);
        }
    }

    private byte[] encryptData(byte[] data, byte[] sharedKey) throws Exception {
        // create the cipher
        SecretKeySpec keySpec = new SecretKeySpec(sharedKey, 0, 16, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        // generate the IV
        byte[] iv = cipher.getIV();
        // encript
        byte[] encryptedData = cipher.doFinal(data);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // concatenate the iv and the ecrypted data
        outputStream.write(iv);
        outputStream.write(encryptedData);
        return outputStream.toByteArray();
    }

    private byte[] decryptData(byte[] encryptedData, byte[] sharedKey) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(sharedKey, 0, 16, "AES");
        // separate the iv and the encrypted data from the recieved message
        byte[] iv = Arrays.copyOfRange(encryptedData, 0, 16);
        byte[] actualEncryptedData = Arrays.copyOfRange(encryptedData, 16, encryptedData.length);
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
        // decrypt
        return cipher.doFinal(actualEncryptedData);
    }

}