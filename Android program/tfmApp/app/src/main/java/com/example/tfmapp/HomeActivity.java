package com.example.tfmapp;

import static com.example.tfmapp.MQTT.TopicsMQTT.topicSubCommand;
import static com.example.tfmapp.MQTT.TopicsMQTT.topicSubData;
import static com.example.tfmapp.Token.tokenString;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.tfmapp.MQTT.MqttService;
import com.example.tfmapp.MQTT.SingletonMQTTService;
import com.example.tfmapp.REST.RestRequests;
import com.example.tfmapp.REST.ServiceGenerator;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;


import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class HomeActivity extends AppCompatActivity {

    private byte[] sharedKey = null;

    private boolean isBound = false;
    SingletonMQTTService mqttInstance = null;

    //Set for receive and handle the mqtt messages
    private BroadcastReceiver mqttReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String topic = intent.getStringExtra("topic");
            String message = intent.getStringExtra("message");

            // handle the received MQTT message
            if (topic != null && message != null) {
                Log.d("MqttService", "Received message: " + message + " on topic: " + topic);

                if(Objects.equals(topic, topicSubData)){
                    //message from the tfm/data topic
                    try {
                        mqttMsgFromData(message);
                    } catch (JSONException e) {
                        Log.e("TAG", "Error parsing Json");
                        e.printStackTrace();
                    }
                }else if (Objects.equals(topic, topicSubCommand)){
                    //message from the tfm/commandsPython topic
                    mqttMsgFromCommand(message);

                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        /*
        //MUST BE DELETED:
        //set the shared vakues by default
        SharedPreferences.Editor sharedPrefEditorr = getSharedPreferences("application", Context.MODE_PRIVATE).edit();
        sharedPrefEditorr.putString("shared_key", "kAmgArFeQhBG7FVQN1pZ7N8q2MxAs9OLKhE3R4cfTmMviGc6AMU1rH6aZc2EeOL0iH8wujFhZC3u\n" +
                "by/kbW28j6rSJZUY1k1Wro0xmnkSyCpJOreBFlyK1LP/nQjxRsyuKT33a70TqrwSbc7tgBr+gL1/\n" +
                "Eo2p3fJkWtLxfCjxCb8=\n");


         */

        /*
        SharedPreferences.Editor sharedPrefEditorr = getSharedPreferences("application", Context.MODE_PRIVATE).edit();
        sharedPrefEditorr.putString("name", "Mario");
        sharedPrefEditorr.putString("phone", "647586927");
        sharedPrefEditorr.putString("license", "9100BHG");
        sharedPrefEditorr.putString("brand", "Seat");
        sharedPrefEditorr.putString("model", "Leon");
        sharedPrefEditorr.putString("color", "White");
        sharedPrefEditorr.apply();

         */


        Toolbar toolbar = (Toolbar) findViewById(R.id.home_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("App"); //No title
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));

        Button varSysButton = findViewById(R.id.systemButtom);
        varSysButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(HomeActivity.this, SetSystemVariablesActivity.class);
                startActivity(i);
            }
        });


        Button startTrackButton = findViewById(R.id.buttonStartTrack);
        startTrackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(HomeActivity.this, CarTrackingActivity.class);
                startActivity(i);
            }
        });

        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);

        String sharedKey64 = sharedPrefEditor.getString("shared_key", null);
        if(sharedKey64 != null){
            sharedKey = Base64.decode(sharedKey64, Base64.DEFAULT);
        }else{
            finish();
        }

        String s = sharedPrefEditor.getString("name", null);
        if(s!=null){
            TextView tv = findViewById(R.id.textHome);
            tv.setText("Wellcome, " + s);
        }

        String brand = sharedPrefEditor.getString("brand", null);
        String model = sharedPrefEditor.getString("model", null);
        if(brand!=null && model!=null){
            TextView tv = findViewById(R.id.carHome);
            tv.setText(brand + ", " + model);
        }


        displayCarData();

        //IF THE MQTT CANNOT BE STABLISH UNTIL THE WATING TIME RUNS OF TIME DO NOT DIAPLAY THE UI OF THE ACTIVITY
        //SO IMPORTANT TO USE A NETWORK THAT DO NOT BLOCK MQTT COMMUNICATION AS THE EDUROAM DOES

        mqttInstance = SingletonMQTTService.getInstance(sharedKey);
        Intent serviceIntent = new Intent(this, MqttService.class);
        bindService(serviceIntent, mqttInstance.serviceConnection, Context.BIND_AUTO_CREATE);
        registerReceiver(mqttReceiver, new IntentFilter("MQTTMessage"));




    }


    private void displayCarData(){

        SharedPreferences.Editor sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE).edit();
        sharedPrefEditor.putString("token", tokenString);
        sharedPrefEditor.apply();

        getLastestTelemetry();
        setSystemVariables();










        /*METHOD TO DIPLAY THE DATA COLLECTED BY THE SENSORS REGARDING CAR STATUS (TEMPERATURE, HUMIDITY, AUTOMATIC VENTILATION (ON/OFF),
        AUTOMATIC LIGHTS (ON/OFF)), AND IF THE ACCIDENT DETECTION IS ENABLED AND IF THE CAR IS POWER ON OR OFF.

        TO KNOW IF THE CAR IS ON, THE APP WILL DO A REQUEST TO THE ARDUINO USING AN SPECIFIC TOPIC, IF IT RECEIBES AN ANSWERS WILL CHANGE
        THE CAR STATUS TU ON AND GET THE CURRENT DATA VALUES.
        WHEN THE ACTIVITY OPENS GET THE LATEST DATA (FOR EXAMPLE REQUESTING TO THINGSBOARD). IF THE STATUS OF THE CAR STATUS CHANGE (TO ON OR TO OFF)
        THE CAR WILL LET THE PHONE APP KNOW BY SENDING A MESSAGE USING THE MQTT TOPIC, SO THE APP WILL KNOW IT AUTOMATICALLI AND ASYNCRONOUSLY AND WILL UPDATE
        THE STATUS OF THE CAR AND THE VALUES.
        TO SIMULATE THE POWER OFF OF THE CAR CAN BE USED THE BUTTOM OF THE BOARD (?) SO THE POWER OFF MESSAGE CAN BE SENT. IF NOT ONLY THE POWER ON MESSAGE
        CAN BE SENT TO THE TOPIC
         */


    }

    private void getLastestTelemetry(){

        RestRequests rest = ServiceGenerator.createService(RestRequests.class);
        // Execute the REST function to get the threshold values from ThingsBoard
        Call<JsonObject> resp = rest.getLastMonitoring("Bearer " + tokenString);
        resp.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JSONObject latestTelemetry = null;
                if (response.code() == 200) {
                    // Successfully received response
                    try {

                        latestTelemetry = new JSONObject(response.body().toString());

                        float temp;
                        float hum;
                        long time=0;
                        try{
                            if (latestTelemetry != null){
                                if(latestTelemetry.has("temperature")) {
                                    temp = Float.parseFloat(latestTelemetry.getJSONArray("temperature").getJSONObject(0).getString("value"));
                                    time = latestTelemetry.getJSONArray("temperature").getJSONObject(0).getLong("ts");
                                    Log.d("REST", "Temperature: " + temp);

                                    TextView tv = findViewById(R.id.tempValue);
                                    tv.setText(String.format("%.1f", temp) + " ºC");
                                }

                                if(latestTelemetry.has("humidity")) {
                                    hum = Float.parseFloat(latestTelemetry.getJSONArray("humidity").getJSONObject(0).getString("value"));
                                    time = latestTelemetry.getJSONArray("humidity").getJSONObject(0).getLong("ts");
                                    Log.d("REST", "Humidity: " + hum);

                                    TextView tv = findViewById(R.id.humValue);
                                    tv.setText(String.format("%.0f", hum) + " %");
                                }


                                if(time != 0){

                                    Date date = new Date(time);
                                    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                    String formattedDateTime = formatter.format(date);
                                    Log.d("REST", date.toString());


                                    TextView tvLastTime = findViewById(R.id.sensorDataTime);
                                    tvLastTime.setText("Last data update: " + formattedDateTime);
                                    tvLastTime.setVisibility(View.VISIBLE);


                                }


                            }else{
                                Log.d("REST", "LATEST TELEMETRY IS NULL");
                            }
                        }catch (JSONException e){
                            e.printStackTrace();
                        }


                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    Log.d("REST", "Error receiving telemetry: " + response.message());

                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.d("REST", "Error receiving telemetry: " + t.getMessage());
                Toast.makeText(getApplicationContext(), "Failure pulling data: check internet connection", Toast.LENGTH_SHORT).show();

            }
        });
    }


    private void setSystemVariables(){

        RestRequests rest = ServiceGenerator.createService(RestRequests.class);
        // Execute the REST function to get the threshold values from ThingsBoard
        Call<JsonObject> resp = rest.getAttributes("Bearer " + tokenString);

        resp.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JSONObject latestTelemetry = null;
                if (response.code() == 200) {
                    // Successfully received response
                    try {

                        JSONObject attributesJSON = new JSONObject(response.body().toString());

                        JSONObject shared = attributesJSON.getJSONObject("shared");

                        // Extract the values from the 'shared' object
                        runOnUiThread(() -> {
                            try {
                                if (shared != null){


                                    if(shared.has("automaticVentilation")) {
                                        boolean automaticVentilation = shared.getBoolean("automaticVentilation");

                                        TextView tv = findViewById(R.id.autoventilationStatus);
                                        tv.setText(String.valueOf(automaticVentilation));
                                        if(automaticVentilation){
                                            tv.setText("ON");
                                        }else{
                                            tv.setText("OFF");
                                        }

                                        if(shared.has("temperatureThreshold")) {
                                            int temperatureThreshold = shared.getInt("temperatureThreshold");
                                            tv = findViewById(R.id.thresholdTemp);
                                            TextView statusTV = findViewById(R.id.automaticVentilationStatus);

                                            if(automaticVentilation){
                                                tv.setText("Threshold temp: " + temperatureThreshold);
                                                tv.setVisibility(View.VISIBLE);
                                                statusTV.setVisibility(View.VISIBLE);
                                                statusTV.setText("Status: OFF");
                                            }else{
                                                tv.setVisibility(View.GONE);
                                                statusTV.setVisibility(View.GONE);
                                            }
                                            //SET DATA ON UI TEXTVIEW
                                        }
                                    }

                                    if(shared.has("startVentilation")) {
                                        boolean startVentilation = shared.getBoolean("startVentilation");

                                        TextView tv = findViewById(R.id.ventilationStatus);
                                        tv.setText(String.valueOf(startVentilation));
                                        if(startVentilation){
                                            tv.setText("ON");
                                        }else{
                                            tv.setText("OFF");
                                        }
                                    }

                                    if(shared.has("startBuzzer")) {
                                        boolean startBuzzer = shared.getBoolean("startBuzzer");

                                        TextView tv = findViewById(R.id.buzzerStatus);
                                        tv.setText(String.valueOf(startBuzzer));
                                        if(startBuzzer){
                                            tv.setText("ON");
                                        }else{
                                            tv.setText("OFF");
                                        }
                                    }

                                    if(shared.has("accidentDetection")) {
                                        boolean accidentDetection = shared.getBoolean("accidentDetection");

                                        TextView tv = findViewById(R.id.accidentDetectionStatus);
                                        tv.setText(String.valueOf(accidentDetection));
                                        if(accidentDetection){
                                            tv.setText("ON");
                                        }else{
                                            tv.setText("OFF");
                                        }
                                    }





                                }else{
                                    Log.d("REST", "LATEST TELEMETRY IS NULL");
                                }


                            }catch (JSONException e){
                                e.printStackTrace();
                            }



                        });


                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    Log.d("REST", "Error receiving telemetry: " + response.message());

                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.d("REST", "Error receiving Attributes: " + t.getMessage());
                Toast.makeText(getApplicationContext(), "Failure pulling data: check internet connection", Toast.LENGTH_SHORT).show();

            }
        });


    }



    private void mqttMsgFromData(String msg) throws JSONException {

        JSONObject jsonObject = new JSONObject(msg);


        runOnUiThread(() -> {

            try {
                Date date = new Date();
                SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                String formattedDateTime = formatter.format(date);

                if (jsonObject.has("temperature")) {
                    TextView tv = findViewById(R.id.tempValue);
                    tv.setText(String.format("%.1f", jsonObject.getDouble("temperature")) + " ºC");

                    TextView tvLastTime = findViewById(R.id.sensorDataTime);
                    tvLastTime.setText("Last data update: " + formattedDateTime);
                    tvLastTime.setVisibility(View.VISIBLE);
                }
                if (jsonObject.has("humidity")) {
                    TextView tv = findViewById(R.id.humValue);
                    tv.setText(String.format("%.0f", jsonObject.getDouble("humidity")) + " %");

                    TextView tvLastTime = findViewById(R.id.sensorDataTime);
                    tvLastTime.setText("Last data update: " + formattedDateTime);
                    tvLastTime.setVisibility(View.VISIBLE);
                }
            } catch (JSONException e) {
                Log.d("TAG", "Cannot get json attributes");
                e.printStackTrace();
            }
        });
    }


    private void mqttMsgFromCommand(String msg){
        if(Objects.equals(msg, "cmd-fanON")){
            TextView statusTV = findViewById(R.id.automaticVentilationStatus);
            statusTV.setText("Status: ON");

        }else if(Objects.equals(msg, "cmd-fanOFF")){
            TextView statusTV = findViewById(R.id.automaticVentilationStatus);
            statusTV.setText("Status: OFF");
        }else if(Objects.equals(msg, "cmd-buzzerON")){

            TextView statusTV = findViewById(R.id.buzzerStatus);
            statusTV.setText("ON");
        }

    }




    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.home_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.contactsHomeMenu) {
            Intent i = new Intent(HomeActivity.this, ContactManagementActivity.class);
            startActivity(i);
            return true;
        } else if (id == R.id.settingsHomeMenu) {
            Intent i = new Intent(HomeActivity.this, firstSetUpActivity.class);
            startActivity(i);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onResume() {
        super.onResume();
        setSystemVariables();
    }
}