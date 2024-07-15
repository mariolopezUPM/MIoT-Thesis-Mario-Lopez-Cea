package com.example.tfmapp;


import static com.example.tfmapp.MQTT.TopicsMQTT.topicPubCommand;
import static com.example.tfmapp.Token.tokenString;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.gson.JsonParser;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SetSystemVariablesActivity extends AppCompatActivity {


    private byte[] sharedKey = null;
    private SingletonMQTTService mqttInstance = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_set_system_variables);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });



        Toolbar toolbar = (Toolbar) findViewById(R.id.systemVar_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("System Variables"); //No title
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                
                onBackPressed();
            }
        });

        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);

        String sharedKey64 = sharedPrefEditor.getString("shared_key", null);
        if(sharedKey64 != null){
            sharedKey = Base64.decode(sharedKey64, Base64.DEFAULT);
        }else{
            finish();
        }


        mqttInstance = SingletonMQTTService.getInstance(sharedKey);
        Intent serviceIntent = new Intent(this, MqttService.class);
        bindService(serviceIntent, mqttInstance.serviceConnection, Context.BIND_AUTO_CREATE);

        //button to save the configuration
        Button buttomConf = findViewById(R.id.buttomsaveConf);
        buttomConf.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //save and send to thingsboard
                SwitchCompat switchVentilation = findViewById(R.id.ventilacionSwitch);
                SwitchCompat switchStartVentilation = findViewById(R.id.startVentilationSwitch);
                SwitchCompat switchBuzzer = findViewById(R.id.buzzerSwitch);
                SwitchCompat switchAccident = findViewById(R.id.accidentSwitch);
                EditText temperatureThresholdET = findViewById(R.id.tempThresholdET);

                int temperatureThreshold;
                boolean automaticVentilation = switchVentilation.isChecked();
                boolean startVentilation = switchStartVentilation.isChecked();
                boolean startBuzzer = switchBuzzer.isChecked();
                boolean accidentDetection = switchAccident.isChecked();

                JSONObject jsonObject = new JSONObject();
                if(temperatureThresholdET.getText().toString().isEmpty()){
                    try {
                        jsonObject.put("automaticVentilation", false);
                        jsonObject.put("startVentilation", startVentilation);
                        jsonObject.put("startBuzzer", startBuzzer);
                        jsonObject.put("accidentDetection", accidentDetection);
                    } catch (JSONException e) {
                        Log.e("REST", "Error parsing data into the json");
                        e.printStackTrace();
                    }

                }else{
                    temperatureThreshold = Integer.valueOf(temperatureThresholdET.getText().toString());
                    try {
                        jsonObject.put("temperatureThreshold", temperatureThreshold);
                        jsonObject.put("automaticVentilation", automaticVentilation);
                        jsonObject.put("startVentilation", startVentilation);
                        jsonObject.put("startBuzzer", startBuzzer);
                        jsonObject.put("accidentDetection", accidentDetection);
                    } catch (JSONException e) {
                        Log.e("REST", "Error parsing data into the json");
                        e.printStackTrace();
                    }
                }

                setAttributesIoTPlatform(jsonObject);

                //sent to car server using mqtt
                JSONObject mqttJson = new JSONObject();
                try {
                    mqttJson.put("setVariables", jsonObject);
                    Log.d("MqttService", "Variables sent by mqtt: "+ mqttJson.toString());
                    mqttInstance.mqttService.publishMessage(topicPubCommand, mqttJson.toString(), false);
                } catch (JSONException e) {
                    Log.e("MqttService", "Error parsing data into the json");
                    e.printStackTrace();
                }




            }
        });

        SwitchCompat switchVentilation = findViewById(R.id.ventilacionSwitch);
        switchVentilation.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                EditText temperatureThresholdET = findViewById(R.id.tempThresholdET);
                if(isChecked && temperatureThresholdET.getText().toString().isEmpty()){
                    switchVentilation.setChecked(false);
                    Toast.makeText(getApplicationContext(), "A threshold temperature must be filled", Toast.LENGTH_SHORT).show();

                }
            }
        });


        EditText temperatureThresholdET = findViewById(R.id.tempThresholdET);
        temperatureThresholdET.addTextChangedListener(new TextWatcher() {

            @Override
            public void afterTextChanged(Editable s) {
                //only can be anable the automatic venntilation if it is a threshold set
                if(s.toString().isEmpty()){
                    switchVentilation.setChecked(false);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                                          int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                                      int before, int count) {

            }
        });

        //get values from thingsboard to set the switches
        getAttributesIoTPlatform();
    }




    private void getAttributesIoTPlatform(){

        RestRequests rest = ServiceGenerator.createService(RestRequests.class);
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


                        runOnUiThread(() -> {
                            try {
                                if (shared != null){

                                    SwitchCompat switchVentilation = findViewById(R.id.ventilacionSwitch);
                                    SwitchCompat switchStartVentilation = findViewById(R.id.startVentilationSwitch);
                                    SwitchCompat switchBuzzer = findViewById(R.id.buzzerSwitch);
                                    SwitchCompat switchAccident = findViewById(R.id.accidentSwitch);
                                    EditText temperatureThresholdET = findViewById(R.id.tempThresholdET);


                                    //initilize the UI considering the current status of the variables
                                    if(shared.has("temperatureThreshold")) {
                                        int temperatureThreshold = shared.getInt("temperatureThreshold");
                                        temperatureThresholdET.setText(String.valueOf(temperatureThreshold));

                                    }

                                    if(shared.has("automaticVentilation")) {
                                        boolean automaticVentilation = shared.getBoolean("automaticVentilation");
                                        switchVentilation.setChecked(automaticVentilation);
                                    }

                                    if(shared.has("startVentilation")) {
                                        boolean startVentilation = shared.getBoolean("startVentilation");
                                        switchStartVentilation.setChecked(startVentilation);
                                    }

                                    if(shared.has("startBuzzer")) {
                                        boolean startBuzzer = shared.getBoolean("startBuzzer");
                                        switchBuzzer.setChecked(startBuzzer);
                                    }

                                    if(shared.has("accidentDetection")) {
                                        boolean accidentDetection = shared.getBoolean("accidentDetection");
                                        switchAccident.setChecked(accidentDetection);
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


    private void setAttributesIoTPlatform(JSONObject attributesJSON){

        JsonParser parser = new JsonParser();
        JsonObject gsonObject = parser.parse(attributesJSON.toString()).getAsJsonObject();

        RestRequests rest = ServiceGenerator.createService(RestRequests.class);
        Call<JsonObject> resp = rest.setAttributes(gsonObject, "Bearer " + tokenString);

        resp.enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                JSONObject latestTelemetry = null;
                if (response.code() == 200) {
                    // Successfully post
                    Log.d("REST", "Successfuly post");
                } else {
                    Log.d("REST", "Error ponting attributes: " + response.message());

                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.d("REST", "Error posting Attributes: " + t.getMessage());

            }
        });

    }


}