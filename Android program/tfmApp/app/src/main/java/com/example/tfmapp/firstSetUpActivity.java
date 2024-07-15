package com.example.tfmapp;

import static com.example.tfmapp.MQTT.TopicsMQTT.topicPubSetUp;
import static com.example.tfmapp.Token.tokenString;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.tfmapp.MQTT.SingletonMQTTService;
import com.example.tfmapp.REST.RestRequests;
import com.example.tfmapp.REST.ServiceGenerator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.json.JSONObject;
import java.util.Arrays;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class firstSetUpActivity extends AppCompatActivity {

    TextInputLayout nameLayout;
    TextInputEditText nameEntry;
    TextInputLayout phoneLayout;
    TextInputEditText phoneEntry;
    TextInputLayout licenseLayout;
    TextInputEditText licenseEntry;
    TextInputLayout brandLayout;
    TextInputEditText brandEntry;
    TextInputLayout modelLayout;
    TextInputEditText modelEntry;
    TextInputLayout colorLayout;
    AutoCompleteTextView colorSelector;
    String carColor = null;

    SingletonMQTTService mqttInstance = null;

    private byte[] sharedKey = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_first_set_up);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        //retreive shared key
        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);
        String sharedKey64 = sharedPrefEditor.getString("shared_key", null);
        if(sharedKey64 != null){
            sharedKey = Base64.decode(sharedKey64, Base64.DEFAULT);
        }else{
            finish();
        }

        mqttInstance = SingletonMQTTService.getInstance(sharedKey);

        Toolbar toolbar = (Toolbar) findViewById(R.id.setUp_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Configuration"); //No title
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        nameEntry = findViewById(R.id.nameConfTV);
        phoneEntry = findViewById(R.id.phoneConfTV);
        licenseEntry = findViewById(R.id.licenseConfTV);
        brandEntry = findViewById(R.id.brandConfTV);
        modelEntry = findViewById(R.id.modelConfTV);
        colorSelector = findViewById(R.id.colorConfTV);

        nameLayout = findViewById(R.id.nameConf);
        phoneLayout = findViewById(R.id.phoneConf);
        licenseLayout = findViewById(R.id.licenseConf);
        brandLayout = findViewById(R.id.brandConf);
        modelLayout = findViewById(R.id.modelConf);
        colorLayout = findViewById(R.id.colorConf);

        //posible car colors to be selected
        List<String> items = Arrays.asList("Black", "White", "Gray", "Blue", "Green", "Yellow", "Red", "Orange", "Brown", "Pink");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.car_color_list_layout, items);
        if (colorSelector != null) {
            colorSelector.setAdapter(adapter);
        }

        colorSelector.setOnItemClickListener( new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View arg1, int pos, long id) {
                carColor = adapter.getItem(pos);
                Toast.makeText(getApplicationContext(), " selected: " + adapter.getItem(pos), Toast.LENGTH_LONG).show();

            }
        });

        setEntriesFromStorage();

        findViewById(R.id.submitConfButton).setOnClickListener(v -> {

            if(checkFieldsFilled()){

                //Change activity
                Intent i = new Intent(this, HomeActivity.class);
                startActivity(i);
            }
        });



    }


    //check if all fields where filled
    private boolean checkFieldsFilled(){
        String name = nameEntry.getText().toString();
        String phone = phoneEntry.getText().toString();
        String license = licenseEntry.getText().toString();
        String brand = brandEntry.getText().toString();
        String model = modelEntry.getText().toString();

        boolean filled = true;


        if(name.isEmpty()){
            nameLayout.setError("User name field must be filled");
            filled = false;

        }else{
            nameLayout.setError(null);
        }

        if(phone.isEmpty()){
            //Toast.makeText(this, "Phone field must be filled", Toast.LENGTH_SHORT).show();
            phoneLayout.setError("User name field must be filled");
            filled = false;

        }else{
            phoneLayout.setError(null);
        }

        if(license.isEmpty()){
            licenseLayout.setError("License number field must be filled");
            filled = false;

        }else{
            licenseLayout.setError(null);
        }

        if(brand.isEmpty()){
            brandLayout.setError("Brand field must be filled");
            filled = false;

        }else{
            brandLayout.setError(null);
        }

        if(model.isEmpty()){
            modelLayout.setError("Model field must be filled");
            filled = false;

        }else{
            modelLayout.setError(null);
        }

        if(carColor == null){
            colorLayout.setError("Color must be selected");
            filled = false;

        }else{
            colorLayout.setError(null);
        }

        //Save on shared prefferences
        SharedPreferences.Editor sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE).edit();
        sharedPrefEditor.putString("name", name);
        sharedPrefEditor.putString("phone", phone);
        sharedPrefEditor.putString("license", license);
        sharedPrefEditor.putString("brand", brand);
        sharedPrefEditor.putString("model", model);
        sharedPrefEditor.putString("color", carColor);
        sharedPrefEditor.apply();


        JSONObject setUpInfo = new JSONObject();

        try {
            setUpInfo.put("userName", name);
            setUpInfo.put("userPhone", phone);
            setUpInfo.put("carLicense", license);
            setUpInfo.put("carBrand", brand);
            setUpInfo.put("carModel", model);
            setUpInfo.put("carColor", carColor);

            String jsonString = setUpInfo.toString();

            //mqttInstance.mqttService.deleteRetainedMessage(topicPubSetUp);
            mqttInstance.mqttService.publishMessage(topicPubSetUp, jsonString, false); // Adjust topic as needed

            //send to thingsboard
            Gson gson = new Gson();
            RestRequests rest = ServiceGenerator.createService(RestRequests.class);
            Call<JsonObject> resp = rest.setAttributes(gson.fromJson(jsonString, JsonObject.class),"Bearer " + tokenString);
            resp.enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                    JSONObject latestTelemetry = null;
                    if (response.code() == 200) {

                    }
                }

                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {
                    Log.d("REST", "Error posting attributes: " + t.getMessage());

                }
            });




        } catch (Exception e) {
            e.printStackTrace();
        }


        return filled;

    }

    //to set the already configured values
    private void setEntriesFromStorage(){

        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);
        String s = null;

        s = sharedPrefEditor.getString("name", null);
        if(s != null){
            nameEntry.setText(s);
        }

        s = sharedPrefEditor.getString("phone", null);
        if(s != null){
            phoneEntry.setText(s);
        }

        s = sharedPrefEditor.getString("license", null);
        if(s != null){
            licenseEntry.setText(s);
        }

        s = sharedPrefEditor.getString("brand", null);
        if(s != null){
            brandEntry.setText(s);
        }

        s = sharedPrefEditor.getString("model", null);
        if(s != null){
            modelEntry.setText(s);
        }

        s = sharedPrefEditor.getString("color", null);
        if(s != null){
            colorSelector.setText(s, false);
            carColor=s;
        }



    }



}