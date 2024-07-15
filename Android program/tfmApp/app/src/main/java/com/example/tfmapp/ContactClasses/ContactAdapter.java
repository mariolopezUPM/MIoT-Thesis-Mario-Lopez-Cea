package com.example.tfmapp.ContactClasses;

import static com.example.tfmapp.MQTT.TopicsMQTT.topicPubContact;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.example.tfmapp.R;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends BaseAdapter {

    private Context context;
    private List<EmergencyContact> contacts;
    private ContactAdapterListener listener;
    private boolean buttonsVisible = false;

    public ContactAdapter(Context context, List<EmergencyContact> contacts, ContactAdapterListener listener) {
        this.context = context;
        this.contacts = contacts;
        this.listener = listener;
    }

    @Override
    public int getCount() {
        return contacts.size();
    }

    @Override
    public Object getItem(int position) {
        return contacts.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false);
        }

        TextView contactName = convertView.findViewById(R.id.contactName);
        TextView contactId = convertView.findViewById(R.id.contactId);
        Button deleteButton = convertView.findViewById(R.id.deleteButton);

        final EmergencyContact contact = contacts.get(position);
        contactName.setText(contact.getName());
        contactId.setText(contact.getId());

        //set vivisbility
        deleteButton.setVisibility(buttonsVisible ? View.VISIBLE : View.GONE);


        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDeleteContact(contact, position);
            }
        });


        return convertView;
    }

    public void setDeleteButtonVisible(boolean visible) {
        buttonsVisible = visible;
        notifyDataSetChanged(); //refresh the view
    }
    public void changeDeleteButtonVisibility() {
        buttonsVisible = (!buttonsVisible);
        notifyDataSetChanged(); //refresh the view
    }


    public interface ContactAdapterListener {
        void onDeleteContact(EmergencyContact contact, int position);
    }
}