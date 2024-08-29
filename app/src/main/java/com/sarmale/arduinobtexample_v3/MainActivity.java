package com.sarmale.arduinobtexample_v3;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

//import com.google.api.client.json.jackson2.JacksonFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "FrugalLogs";
    private static final int REQUEST_ENABLE_BT = 1;
    public static Handler handler;
    private final static int ERROR_READ = 0; // used in bluetooth handler to identify message update
    BluetoothDevice arduinoBTModule = null;
    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private FileOutputStream fos;

    String dateTime;
    Calendar calendar;
    SimpleDateFormat simpleDateFormat;
    public String fileName;
    public String filePath;

    private MyGoogleDriveService myDriveService;

    public boolean connectionStatus=true;

    private boolean stopEventInitiated=false;

    public MainActivity() throws IOException {
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        TextView btReadings = findViewById(R.id.btReadings);
        TextView btDevices = findViewById(R.id.btDevices);
        Button connectToDevice = (Button) findViewById(R.id.connectToDevice);
        Button seachDevices = (Button) findViewById(R.id.seachDevices);
        Button clearValues = (Button) findViewById(R.id.refresh);
        Log.d(TAG, "Begin Execution");

        calendar=Calendar.getInstance();
        simpleDateFormat = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        dateTime = simpleDateFormat.format(calendar.getTime()).toString();

        fileName= dateTime+"_csi_imu_data.txt";

        try {
            myDriveService=new MyGoogleDriveService(MainActivity.this);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        System.out.println(dateTime);

        requestPermissions();

        initializeFileOutputStream();

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ERROR_READ:
                        String arduinoMsg = msg.obj.toString();
                        //btReadings.setText(arduinoMsg);
                        saveDataToFile(arduinoMsg);
                        break;
                    default:
                        String receivedData = (String) msg.obj;
                        btReadings.append(receivedData + "\n");
                        break;
                }
            }
        };

        clearValues.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String dataToSave = btReadings.getText().toString();
                stopEventInitiated=true;
                if (connectedThread != null) {
                    connectedThread.cancel();
                    connectedThread = null;
                }
                if (connectThread != null) {
                    connectThread.cancel();
                    connectThread = null;
                }
                if(connectionStatus) {
                    System.out.println("File can be uploaded");
                    uploadFileToDriveInBackground(filePath, fileName);
                }else {
                    closeFileStream();
                }
               //new UploadFileTask().doInBackground(filePath,"1NH1IAPIkZlyN9MvMWdW4v6MqkYl_cCi7",fileName);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "Data Collection stopped!!!", Toast.LENGTH_SHORT).show();
                        Toast.makeText(MainActivity.this,"Data being uploaded....",Toast.LENGTH_LONG).show();
                        // Terminate the app gracefully
                        finish(); // Close the activity
                        System.exit(0); // Terminate the app process
                    }
                }, 60000);
            }
        });

        final Observable<String> connectToBTObservable = Observable.create(emitter -> {
            Log.d(TAG, "Calling connectThread class");
            ConnectThread connectThread = new ConnectThread(arduinoBTModule, arduinoUUID, handler);
            connectThread.run();
            connectionStatus=connectThread.connectionStatus;
            if (connectThread.getMmSocket().isConnected()) {
                Log.d(TAG, "Calling ConnectedThread class");
                ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket(), handler);
                connectedThread.run();
                if (connectedThread.getValueRead() != null) {
                    emitter.onNext(connectedThread.getValueRead());
                }
                connectedThread.cancel();
            }
            connectThread.cancel();
            emitter.onComplete();
        });

        connectToDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(connectionStatus) {
                    if (arduinoBTModule != null) {
                        Toast.makeText(MainActivity.this,"Data Collection Started",Toast.LENGTH_SHORT).show();
                        connectToBTObservable.
                                observeOn(AndroidSchedulers.mainThread()).
                                subscribeOn(Schedulers.io()).
                                subscribe(valueRead -> {
                                    //btReadings.setText(valueRead);
                                });
                    }
                }else{
                    Toast.makeText(MainActivity.this,"Device Not Connected!!!",Toast.LENGTH_SHORT).show();
                }
            }
        });

        seachDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bluetoothAdapter == null) {
                    Log.d(TAG, "Device doesn't support Bluetooth");
                } else {
                    Log.d(TAG, "Device support Bluetooth");
                    if (!bluetoothAdapter.isEnabled()) {
                        Log.d(TAG, "Bluetooth is disabled");
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            Log.d(TAG, "We don't have BT Permissions");
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                            Log.d(TAG, "Bluetooth is enabled now");
                        } else {
                            Log.d(TAG, "We have BT Permissions");
                            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                            Log.d(TAG, "Bluetooth is enabled now");
                        }
                    } else {
                        Log.d(TAG, "Bluetooth is enabled");
                    }
                    String btDevicesString = "";
                    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

                    if (!pairedDevices.isEmpty()) {
                        for (BluetoothDevice device : pairedDevices) {
                            String deviceName = device.getName();
                            String deviceHardwareAddress = device.getAddress();
                            Log.d(TAG, "deviceName:" + deviceName);
                            Log.d(TAG, "deviceHardwareAddress:" + deviceHardwareAddress);
                            btDevicesString = btDevicesString + deviceName + " || " + deviceHardwareAddress + "\n";
                            if (deviceName.equals("HC-05")) {
                                Log.d(TAG, "HC-05 found");
                                arduinoUUID = device.getUuids()[0].getUuid();
                                arduinoBTModule = device;
                                connectToDevice.setEnabled(true);
                            }
                            btDevices.setText(btDevicesString);
                        }
                    }
                }
                Log.d(TAG, "Button Pressed");
            }
        });
    }

    private void saveDataToFile(String data) {
        if (fos == null) {
            Log.e(TAG, "FileOutputStream is null, data will not be saved");
            return;
        }
        try {
            fos.write((data + "\n").getBytes());
            fos.flush();
            Log.d(TAG, "Data saved to file");
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to save data to file");
        }
    }

    private void closeFileStream() {
        try {
            if (fos != null) {
                fos.close();
                Log.d(TAG, "File stream closed");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to close file stream");
        }
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    private void initializeFileOutputStream() {
        try {
            File file = new File(getExternalFilesDir(null), fileName);
            filePath=file.getAbsolutePath();
            System.out.println("Filepath----->"+filePath);
            fos = new FileOutputStream(file, true);
            Log.d(TAG, "FileOutputStream initialized: " + file.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Failed to initialize FileOutputStream");
            fos = null;
        }
    }

    private void uploadFileToDriveInBackground(String filePath, String fileName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(() -> {
            try {
                System.out.println("Service----->" + myDriveService.getDriveService().toString());
                UploadFileToDrive uploadFileToDrive = new UploadFileToDrive(myDriveService.getDriveService());
                uploadFileToDrive.uploadFile(filePath, "1NH1IAPIkZlyN9MvMWdW4v6MqkYl_cCi7", fileName);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e(TAG, "Failed to upload file to Drive");
            }
        });

        // Optionally wait for the task to complete before moving forward
        try {
            future.get();  // Waits for the task to finish
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "Error waiting for file upload task");
        }
    }
}
