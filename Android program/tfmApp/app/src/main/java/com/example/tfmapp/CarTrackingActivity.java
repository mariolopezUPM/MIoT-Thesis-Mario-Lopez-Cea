package com.example.tfmapp;

import static com.example.tfmapp.MQTT.TopicsMQTT.topicPubCommand;
import static com.example.tfmapp.MQTT.TopicsMQTT.topicSubData;
import static com.example.tfmapp.Token.tokenString;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.net.Uri;
import com.example.tfmapp.MQTT.MqttService;
import com.example.tfmapp.MQTT.SingletonMQTTService;
import com.example.tfmapp.REST.RestRequests;
import com.example.tfmapp.REST.ServiceGenerator;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.gson.JsonObject;
import org.json.JSONException;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CarTrackingActivity extends AppCompatActivity implements OnMapReadyCallback {

    private byte[] sharedKey = null;
    SingletonMQTTService mqttInstance = null;
    LatLng latlng;
    private GoogleMap googleMap;


    private BroadcastReceiver mqttReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String topic = intent.getStringExtra("topic");
            String message = intent.getStringExtra("message");

            // handle the received MQTT message
            if (topic != null && message != null) {
                Log.d("MqttService", "Received message: " + message + " on topic: " + topic);

                if(Objects.equals(topic, topicSubData)){
                    try {
                        mqttMsgFromData(message);
                    } catch (JSONException e) {
                        Log.e("TAG", "Error parsing Json");
                        e.printStackTrace();
                    }
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_car_tracking);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.tracking_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Tracking");
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                onBackPressed();
            }
        });


        String sharedKey64 = sharedPrefEditor.getString("shared_key", null);
        if(sharedKey64 != null){
            sharedKey = Base64.decode(sharedKey64, Base64.DEFAULT);
        }else{
            finish();
        }

        //get lat from thingsboard
        latlng= new LatLng(0, 0);
        getLastestTelemetry();

        setMap();

        Button mapButton = findViewById(R.id.buttonMaps);
        mapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                Uri gmmIntentUri = Uri.parse("geo:" + latlng.latitude + "," + latlng.longitude +"?q=" + latlng.latitude + "," + latlng.longitude);
                Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                mapIntent.setPackage("com.google.android.apps.maps");
                if (mapIntent.resolveActivity(getPackageManager()) != null) {
                    startActivity(mapIntent);
                }


            }
        });

        //button to tho the blinky patterns with the actuators to find the car
        Button findButton = findViewById(R.id.buttonFindCar);
        findButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mqttInstance.mqttService.publishMessage(topicPubCommand, "cmd-findCar", false);


            }
        });

        //stablish mqtt connection ans subscribe to data topic
        mqttInstance = SingletonMQTTService.getInstance(sharedKey);
        Intent serviceIntent = new Intent(this, MqttService.class);
        bindService(serviceIntent, mqttInstance.serviceConnection, Context.BIND_AUTO_CREATE);
        registerReceiver(mqttReceiver, new IntentFilter("MQTTMessage"));


    }






    private void setMap(){
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (supportMapFragment != null) {
            supportMapFragment.getMapAsync(this);
        } else {
            Log.e("Car tracking", "SupportMapFragment is null");
        }


        if((latlng.longitude == 0.0) & (latlng.latitude == 0.0)){
            //if no coordinates were found
            findViewById(R.id.mapLinearLayout).setVisibility(View.GONE);
            findViewById(R.id.noLocationText).setVisibility(View.VISIBLE);

        }else{
            findViewById(R.id.mapLinearLayout).setVisibility(View.VISIBLE);
            findViewById(R.id.noLocationText).setVisibility(View.GONE);
        }




    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap; // Store the reference
        updateMap();
    }

    //set map with the coordinates
    private void updateMap() {
        if (googleMap != null) {
            googleMap.clear();
            googleMap.addMarker(new MarkerOptions().position(latlng).title("Your car"));
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 14));
        }
    }

    // get the last position from the thingsboard
    private void getLastestTelemetry(){

        RestRequests rest = ServiceGenerator.createService(RestRequests.class);
        Call<JsonObject> resp = rest.getLastLocation("Bearer " + tokenString);
        resp.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JSONObject latestTelemetry = null;
                if (response.code() == 200) {
                    // Successfully received response
                    try {

                        latestTelemetry = new JSONObject(response.body().toString());

                        double latitude;
                        double longitude;
                        long time =0;
                        try{
                            if (latestTelemetry != null){
                                if(latestTelemetry.has("latitude") && latestTelemetry.has("longitude")) {
                                    latitude = Double.parseDouble(latestTelemetry.getJSONArray("latitude").getJSONObject(0).getString("value"));
                                    longitude = Double.parseDouble(latestTelemetry.getJSONArray("longitude").getJSONObject(0).getString("value"));
                                    latlng= new LatLng(latitude, longitude);

                                    time = latestTelemetry.getJSONArray("latitude").getJSONObject(0).getLong("ts");
                                    Log.d("REST", "Latitude: " + latitude + " Longitude: " + longitude);

                                    TextView tv = findViewById(R.id.latitudeText);
                                    tv.setText(String.valueOf(latitude));
                                    tv = findViewById(R.id.longitudeText);
                                    tv.setText(String.valueOf(longitude));

                                    Date date = new Date(time);
                                    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                                    String formattedDateTime = formatter.format(date);
                                    Log.d("REST", date.toString());


                                    TextView tvLastTime = findViewById(R.id.sensorDataTime);
                                    tvLastTime.setText("Last data update: " + formattedDateTime);
                                    tvLastTime.setVisibility(View.VISIBLE);

                                    findViewById(R.id.mapLinearLayout).setVisibility(View.VISIBLE);
                                    findViewById(R.id.noLocationText).setVisibility(View.GONE);
                                    updateMap();
                                }

                            }else{
                                Log.d("REST", "LATEST TELEMETRY IS NULL");
                                findViewById(R.id.mapLinearLayout).setVisibility(View.GONE);
                                findViewById(R.id.noLocationText).setVisibility(View.VISIBLE);

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

    //get the coordinates from the mqtt message sent by the car server
    private void mqttMsgFromData(String msg) throws JSONException {

        JSONObject jsonObject = new JSONObject(msg);


        runOnUiThread(() -> {
            double latitude;
            double longitude;
            try {
                if(jsonObject.has("latitude") && jsonObject.has("longitude")) {

                    latitude = jsonObject.getDouble("latitude");
                    longitude = jsonObject.getDouble("longitude");
                    latlng= new LatLng(latitude, longitude);

                    Log.d("TAG", "Latitude: " + latitude + " Longitude: " + longitude);

                    TextView tv = findViewById(R.id.latitudeText);
                    tv.setText(String.valueOf(latitude));
                    tv = findViewById(R.id.longitudeText);
                    tv.setText(String.valueOf(longitude));

                    Date date = new Date();
                    SimpleDateFormat formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
                    String formattedDateTime = formatter.format(date);
                    Log.d("REST", date.toString());


                    TextView tvLastTime = findViewById(R.id.sensorDataTime);
                    tvLastTime.setText("Last data update: " + formattedDateTime);
                    tvLastTime.setVisibility(View.VISIBLE);

                    //update map with the new coordinates
                    updateMap();

                }
            } catch (JSONException e) {
                Log.d("TAG", "Cannot get json attributes");
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        getLastestTelemetry();
    }
}