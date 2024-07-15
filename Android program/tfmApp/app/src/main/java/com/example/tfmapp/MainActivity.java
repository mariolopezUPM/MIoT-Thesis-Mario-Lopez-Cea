package com.example.tfmapp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import android.util.Base64;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.DHParameterSpec;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> deviceListAdapter;
    private ArrayList<String> deviceList;
    private ListView listView;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private InputStream inputStream;


    // defined to recieve the bluetooth devices discovered
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                if (!(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                    setNoPermisionsLayout();
                }

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceAddress = device.getAddress();
                String deviceInfo = deviceName + "\n" + deviceAddress;
                if (!deviceList.contains(deviceInfo)) {
                    // add the device to the list and update the UI
                    deviceList.add(deviceInfo);
                    deviceListAdapter.notifyDataSetChanged();
                    findViewById(R.id.no_devices_message).setVisibility(View.GONE);
                    findViewById(R.id.device_list).setVisibility(View.VISIBLE);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        SharedPreferences sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE);
        String s = sharedPrefEditor.getString("shared_key", null);
        // check if the key was already created
        if(s != null){
            //check if the configuration was already done
            s = sharedPrefEditor.getString("name", null);
            if(s != null){
                // when painrg and initial configuration was already done
                Intent i = new Intent(this, HomeActivity.class);
                startActivity(i);
            }else{
                // when only the pairing was done
                Intent i = new Intent(this, firstSetUpActivity.class);
                startActivity(i);
            }

        }


        findViewById(R.id.no_devices_message).setVisibility(View.VISIBLE);

        //Button to ask again for the permission that were denied before
        Button permissionButton = findViewById(R.id.buttonPermissions);
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                checkPermissions();
            }
        });

        //initilize the bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceList = new ArrayList<>();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceList);
        listView = findViewById(R.id.device_list);
        listView.setAdapter(deviceListAdapter);

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }


        //check if the correct permision are acepted
        checkPermissions();

        //the bluetooth must be turned ON
        if (!bluetoothAdapter.isEnabled()) {
            if (!(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                setNoPermisionsLayout();
            }

            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        }

        //button to restart the bluetooth devices search
        Button restartButton = findViewById(R.id.buttonStartDisc);
        restartButton.setOnClickListener(v -> {
            deviceList.clear();
            deviceListAdapter.notifyDataSetChanged();
            findViewById(R.id.no_devices_message).setVisibility(View.VISIBLE);
            findViewById(R.id.device_list).setVisibility(View.GONE);
            discoverDevices();

        });


        listView.setOnItemClickListener((parent, view, position, id) -> {
            String item = (String) parent.getItemAtPosition(position);
            String deviceAddress = item.split("\n")[1];
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connectToDevice(device);
        });
    }

    private void checkPermissions() {
        //request the permision if they werent acepted yet

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                //if the permision are already acepted then start the discovery
                discoverDevices();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQUEST_BLUETOOTH_PERMISSIONS);
            } else {
                discoverDevices();
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }


    }

    //function that handle the result of permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                findViewById(R.id.bluetooth_layout).setVisibility(View.VISIBLE);
                findViewById(R.id.no_permisions_layout).setVisibility(View.GONE);
                findViewById(R.id.buttonStartDisc).setEnabled(true);
                findViewById(R.id.buttonPermissions).setEnabled(false);

                //once the permision are acepted the discovered devices task is started
                discoverDevices();
            } else {
                Toast.makeText(this, "Permissions required for Bluetooth functionality", Toast.LENGTH_SHORT).show();

                setNoPermisionsLayout();

            }
        }
    }

    private void setNoPermisionsLayout() {
        findViewById(R.id.bluetooth_layout).setVisibility(View.GONE);
        findViewById(R.id.no_permisions_layout).setVisibility(View.VISIBLE);
        findViewById(R.id.buttonStartDisc).setEnabled(false);
        findViewById(R.id.buttonPermissions).setEnabled(true);
    }

    //function to initialize the devices discovery
    private void discoverDevices() {
        if (!(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            setNoPermisionsLayout();
        }
        if (bluetoothAdapter.isDiscovering()) {
            //if its already discovering stop it to start again
            bluetoothAdapter.cancelDiscovery();
        }
        //start the discovery of devices
        bluetoothAdapter.startDiscovery();

        //register the broadcast reciver to listen the broadcast of type defined in the filter
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    //function to handle the connection to the selected device
    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            if (!(ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
                setNoPermisionsLayout();
                return;
            }

            try {
                bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothSocket.connect();
                outputStream = bluetoothSocket.getOutputStream();
                inputStream = bluetoothSocket.getInputStream();

                String message = readBluetoothBuffer(inputStream, false);
                if (Objects.equals(message, "cmd-number?")) {
                    //check if the server ask for the pairing code number
                    runOnUiThread(this::showCodeDialog);
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Unexpected message: " + message, Toast.LENGTH_SHORT).show());
                    bluetoothSocket.close();
                }

                bluetoothAdapter.cancelDiscovery();
            } catch (IOException e) {
                Log.e("Bluetooth", "Socket connection failed", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Connection failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private String readBluetoothBuffer(InputStream inputStream, boolean inmediat) {
        byte[] buffer = new byte[1024];
        int bytes;
        StringBuilder accumulatedMessage = new StringBuilder();
        try {
            long startTime = System.currentTimeMillis();

            // Loop to wait for data with a timeout of 5 seconds
            while (inputStream.available() == 0) {
                if ((System.currentTimeMillis() - startTime > 10000) || inmediat) {
                    return null;
                }
                Thread.sleep(100);
            }
            bytes = inputStream.read(buffer);
            String receivedFragment = new String(buffer, 0, bytes);
            accumulatedMessage.append(receivedFragment);

            // check if a complete message has been received
            int endOfMessage = accumulatedMessage.indexOf("\n");
            if (endOfMessage != -1) {
                //take the message without the \n character
                String completeMessage = accumulatedMessage.substring(0, endOfMessage);
                Log.d("Bluetooth", "Received: " + completeMessage);
                return completeMessage;
            } else {
                return null;
            }
        } catch (IOException | InterruptedException e) {
            Log.e("Bluetooth", "Failed to read from InputStream", e);
            return null;
        }
    }

    private void showCodeDialog() {
        // to display the dialog where introduc the pairing code
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_bluetooth_code, null);

        EditText dialogEditText = dialogView.findViewById(R.id.dialogCodeEditText);
        TextView dialogMessage = dialogView.findViewById(R.id.dialogCodeError);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        AlertDialog dialog = builder.create();

        dialogView.findViewById(R.id.dialogCodePositiveButton).setOnClickListener(v -> {

            String enteredText = dialogEditText.getText().toString();

            String message = readBluetoothBuffer(inputStream, true);
            //check if has not been exceed the time out
            if (Objects.equals(message, "cmd-disconnect")) {
                //When the time to send the code ends -> close the bluetooth connection
                try {
                    bluetoothSocket.close();
                } catch (IOException e) {
                    Log.e("Bluetooth", "Failed to close socket", e);
                }
                dialog.dismiss();
                Toast.makeText(getApplicationContext(), "TIME OUT FOR PAIRING: Bluetooth connection deny", Toast.LENGTH_LONG).show();

            }else{

                if(!sendMessage(enteredText+"\n")){
                    //if the code cannot be sent
                    try {
                        bluetoothSocket.close();
                    } catch (IOException e) {
                        Log.e("Bluetooth", "Failed to close socket", e);
                    }
                }else{

                    message = readBluetoothBuffer(inputStream, false);
                    if(Objects.equals(message, "msg-codeMatched")){
                        //Code match
                        dialogView.findViewById(R.id.codeDialogLinearLayout).setVisibility(View.GONE);
                        dialogView.findViewById(R.id.codeDialogProgressBar).setVisibility(View.VISIBLE);

                        //Generate the shared key
                        if(generateSharedKey()){
                            //If it was well created then go to the configuration activity
                            dialog.dismiss();
                            Intent i = new Intent(this, firstSetUpActivity.class);
                            startActivity(i);

                        }else{
                            try {
                                bluetoothSocket.close();
                            } catch (IOException e) {
                                Log.e("Bluetooth", "Failed to close socket", e);
                            }

                            AlertDialog.Builder errorDialogBuilder = new AlertDialog.Builder(this);
                            errorDialogBuilder.setMessage("Bluetooth connection Error")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int id) {
                                        }
                                    });
                            errorDialogBuilder.create().show();
                        }




                    } else if (Objects.equals(message, "msg-codeNotMatched")){
                        //Code dont match
                        dialogMessage.setVisibility(View.VISIBLE);
                        dialogMessage.setTextColor(Color.parseColor("#DB5A6B"));
                        dialogEditText.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#DB5A6B")));

                    }

                }



            }



        });

        dialogView.findViewById(R.id.dialogCodeNegativeButton).setOnClickListener(v -> {
            dialog.dismiss();
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e("Bluetooth", "Failed to close socket", e);
            }
        });

        dialog.setView(dialogView);
        dialog.setCancelable(false);
        dialog.show();
    }

    //function to send messages via bluetooth
    private boolean sendMessage(String message) {
        if (outputStream != null) {
            try {
                outputStream.write(message.getBytes());
                outputStream.flush();
                return true;
            } catch (IOException e) {
                Log.e("Bluetooth", "Failed to send message", e);
                return false;
            }
        }
        return false;
    }


    //function that handle the key generation applying the Diffie-Hellman algorithm
    private boolean generateSharedKey(){
        try {

            // DH parameters
            BigInteger p = new BigInteger("124927412794759309073834418107862627584105708953213012394008459115592612333936335372211040881585927623411450022498348324629172444309528091354598075792839939082523124727759421649754798764826003093787345811704804823288379460767804149915361102817074284046061644059446880657931686339571298085000328811138941086319");
            BigInteger g = BigInteger.valueOf(2);
            DHParameterSpec dhParams = new DHParameterSpec(p, g);

            Log.d("SharedKey", "Generating keys");
            // Generate Diffie-Hellman key pair using the DH parameters
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DH");
            keyPairGenerator.initialize(dhParams);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            PublicKey publicKey = keyPair.getPublic();
            Log.d("SharedKey", "Public key" + new String(publicKey.getEncoded(), StandardCharsets.UTF_8));
            // key pair generated

            
            // send public key to the server
            outputStream.write(publicKey.getEncoded());
            outputStream.flush();
            Log.d("SharedKey", "Sent public key to server: " + publicKey.getEncoded().length);

            // receive server's public key in PEM format
            byte[] buffer = new byte[4096];
            int bytesRead = inputStream.read(buffer);
            Log.d("SharedKey", "Reciving key");
            byte[] serverPublicKeyBytes = new byte[bytesRead];
            System.arraycopy(buffer, 0, serverPublicKeyBytes, 0, bytesRead);
            Log.d("SharedKey", "Server Public Key: " + new String(buffer, StandardCharsets.UTF_8));

            String key= new String(buffer, StandardCharsets.UTF_8);
            // delete the extra charaecters to get only the key
            String publicKeyPEM = key
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replaceAll(System.lineSeparator(), "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replace("?", "");

            Log.d("SharedKey", publicKeyPEM);

            byte[] encoded = Base64.decode(publicKeyPEM,0);

            KeyFactory keyFactory = KeyFactory.getInstance("DH", "BC");
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
            PublicKey serverPublicKey = keyFactory.generatePublic(keySpec);

            // Generate shared key with the other's public key and its private key
            Log.d("SharedKey", "Generating secret key");
            KeyAgreement keyAgreement = KeyAgreement.getInstance("DH", "BC");
            keyAgreement.init(keyPair.getPrivate());
            keyAgreement.doPhase(serverPublicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();

            String sharedSecretHex = bytesToHex(sharedSecret);
            Log.d("SharedKey", "Shared key in hexadecimal\n" + formatHexToMultiline(sharedSecretHex, 68));

            String sharedSecretBase64 = Base64.encodeToString(sharedSecret, Base64.DEFAULT);
            Log.d("SharedKey", sharedSecretBase64);
            // store the shared secret in SharedPreferences
            SharedPreferences.Editor sharedPrefEditor = getSharedPreferences("application", Context.MODE_PRIVATE).edit();
            sharedPrefEditor.putString("shared_key", sharedSecretBase64);
            sharedPrefEditor.apply();

            //store also in internal storage for debugging
            String fileName = "shared_secret.txt";
            File file = new File(getApplicationContext().getFilesDir(), fileName);
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
                fos.write(sharedSecretBase64.getBytes());
                Log.d("SharedKey", "Shared secret saved to file: " + file.getAbsolutePath());
            } catch (IOException e) {
                Log.e("SharedKey", "Error writing shared secret to file", e);
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        Log.e("SharedKey", "Error closing FileOutputStream", e);
                    }
                }
            }

            return true;
        } catch (Exception e) {

            Log.e("SharedKey", "Error connecting to server", e);
            return false;
        }
    }

    private static String formatHexToMultiline(String hex, int chunkSize) {
        StringBuilder multilineHex = new StringBuilder();
        int index = 0;
        while (index < hex.length()) {
            int endIndex = Math.min(index + chunkSize, hex.length());
            multilineHex.append(hex, index, endIndex).append("\n");
            index = endIndex;
        }
        return multilineHex.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException e) {
            Log.e("Bluetooth", "Receiver was not registered", e);
        }
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e("Bluetooth", "Failed to close socket", e);
            }
        }
    }
}