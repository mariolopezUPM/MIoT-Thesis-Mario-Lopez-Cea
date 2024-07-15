package com.example.tfmapp;


import static com.example.tfmapp.MQTT.TopicsMQTT.topicPubContact;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.content.SharedPreferences;
import com.example.tfmapp.ContactClasses.ContactAdapter;
import com.example.tfmapp.ContactClasses.EmergencyContact;
import com.example.tfmapp.MQTT.SingletonMQTTService;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ContactManagementActivity extends AppCompatActivity implements ContactAdapter.ContactAdapterListener {


    private ListView contactListView;
    private ContactAdapter adapter;
    private List<EmergencyContact> contactList;

    private byte[] sharedKey = null;

    SingletonMQTTService mqttInstance = null;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_contact_management);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Toolbar toolbar = (Toolbar) findViewById(R.id.contacts_toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Emergency Contacts"); //No title
        toolbar.setTitleTextColor(getResources().getColor(R.color.white));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        contactListView = findViewById(R.id.contactListView);

        contactList = getContacts();
        adapter = new ContactAdapter(this, contactList, this);
        contactListView.setAdapter(adapter);

        Button addContact = findViewById(R.id.addContactButton);
        addContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showContactDialog();
            }
        });


        Button deleteContact = findViewById(R.id.deleteContactButton);
        deleteContact.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adapter.changeDeleteButtonVisibility();
            }
        });



        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);

        String sharedKey64 = sharedPrefEditor.getString("shared_key", null);
        if(sharedKey64 != null){
            sharedKey = Base64.decode(sharedKey64, Base64.DEFAULT);
        }else{
            finish();
        }
        //get mqtt instance
        mqttInstance = SingletonMQTTService.getInstance(sharedKey);


    }

    //dialog to add a contact
    private void showContactDialog() {

        AlertDialog.Builder builder = new AlertDialog.Builder(ContactManagementActivity.this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_contact, null);
        builder.setView(dialogView);

        //entries to be filled
        final EditText editText1 = dialogView.findViewById(R.id.editText1); //contact name
        final EditText editText2 = dialogView.findViewById(R.id.editText2); //contact telegram ID


        builder.setPositiveButton("Save", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = editText1.getText().toString();
                String id = editText2.getText().toString();

                EmergencyContact contact = new EmergencyContact(name, id);

                saveContact(contact);

                contactList.add(contact);
                adapter.notifyDataSetChanged();

                Toast.makeText(ContactManagementActivity.this, "Saved: " + name + ", " + id, Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        // Create and show the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    //save contact on shared prefference and send the list to the car server via mqtt
    private void saveContact(EmergencyContact contact) {


        List<EmergencyContact> contacts = getContacts();

        //add the new contact to the list
        contacts.add(contact);
        String contactsJson = new Gson().toJson(contacts);
        Log.d("Contact", contactsJson);

        //save on shared preferences
        SharedPreferences prefs = getSharedPreferences("application", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("emergencyContacts", contactsJson);
        editor.apply();

        //send the list to the car server
        mqttInstance.mqttService.publishMessage(topicPubContact, contactsJson, false);
    }

    private List<EmergencyContact> getContacts() {
        SharedPreferences prefs = getSharedPreferences("application", MODE_PRIVATE);
        String contactsJson = prefs.getString("emergencyContacts", null);

        if (contactsJson != null) {
            Type type = new TypeToken<List<EmergencyContact>>() {}.getType();
            return new Gson().fromJson(contactsJson, type);
        } else {
            return new ArrayList<>();
        }
    }





    @Override
    public void onDeleteContact(EmergencyContact contact, int position) {
        // delete from shared preferences and send the new list to the car server
        removeContact(contact, position);

        // update the UI list
        contactList.remove(contact);
        adapter.setDeleteButtonVisible(false);
        adapter.notifyDataSetChanged();

        Toast.makeText(ContactManagementActivity.this, "Contact Deleted: " + contact.getName(), Toast.LENGTH_LONG).show();
    }

    //delete from shared preferences and send the new list to the car server
    private void removeContact(EmergencyContact contact, int position) {
        SharedPreferences prefs = getSharedPreferences("application", MODE_PRIVATE);
        String contactsJson = prefs.getString("emergencyContacts", null);

        List<EmergencyContact> contacts;
        if (contactsJson != null) {
            Type type = new TypeToken<List<EmergencyContact>>() {}.getType();
            contacts = new Gson().fromJson(contactsJson, type);

            contacts.remove(position);

            contactsJson = new Gson().toJson(contacts);
            //save the new list
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("emergencyContacts", contactsJson);
            editor.apply();

            //send the contact list to the car system
            mqttInstance.mqttService.publishMessage(topicPubContact, contactsJson, false);
        }
    }


}