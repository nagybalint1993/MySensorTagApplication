package kpwhrj.mysensortagapp;


import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import kpwhrj.mysensortagapp.MQTT.MqttAndroidClient;
import kpwhrj.mysensortagapp.Connection.ConnectionStatus;
import  kpwhrj.mysensortagapp.ActionListener.Action;


public class MainActivity extends ActionBarActivity {

    private static final int RESULT_SETTINGS = 1;
    private static final int REQUEST_ENABLE_BT = 1 ;
    private static final long SCAN_PERIOD = 10000;

    public static Connection connection= null;
    private String clientHandle;

    private ChangeListener changeListener = new ChangeListener();
    private Handler mHandler;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private static BluetoothDevice mDevice;
    private boolean mScanning;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mHandler = new Handler();
        isBluetoothEnabled();
        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();



        Button btnConnServ= (Button) findViewById(R.id.btnConnServ);
        Button btnConnDev= (Button) findViewById(R.id.btnConnDev);
        btnConnDev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanLeDevice(true);
            }
        });
        btnConnServ.setOnClickListener(new BtnOnClickListener());


        Button btnPublish = (Button) findViewById(R.id.btnpublish);
        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publish("test","tesztelgetek :)");
            }
        });


    }

    private void isBluetoothEnabled() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                }
            }, SCAN_PERIOD);

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice device, int rssi,
                                     byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(((String)device.getName()).equals("SensorTag")){
                                final Intent intent = new Intent(MainActivity.this, DeviceControlActivity.class);
                                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_NAME, device.getName());
                                intent.putExtra(DeviceControlActivity.EXTRAS_DEVICE_ADDRESS, device.getAddress());
                                mDevice= device;

                                if (mScanning) {
                                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                                    mScanning = false;
                                }
                                startActivity(intent);
                            }
                        }
                    });
                }
            };

    public static BluetoothDevice getmDevice() {
        return mDevice;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (item.getItemId()) {

            case R.id.action_settings:
                Intent i = new Intent(this, MQTTSettings.class);
                startActivityForResult(i, RESULT_SETTINGS);
                break;

        }
        return super.onOptionsItemSelected(item);
    }

    public void publish(String topic, String message)
    {
        int checked = 0;
        int qos = ActivityConstants.defaultQos;


        boolean retained = false;

        String[] args = new String[2];
        args[0] = message;
        args[1] = topic+";qos:"+qos+";retained:"+retained;



        try {
            connection.getClient()
                    .publish(topic, message.getBytes(), qos, retained, null, new ActionListener(this, Action.PUBLISH, clientHandle, args));
        }
        catch (MqttSecurityException e) {
            Log.e(this.getClass().getCanonicalName(), "Failed to publish a messged from the client with the handle " + clientHandle, e);
        }
        catch (MqttException e) {
            Log.e(this.getClass().getCanonicalName(), "Failed to publish a messged from the client with the handle " + clientHandle, e);
        }

    }



    class BtnOnClickListener implements View.OnClickListener{

        @Override
        public void onClick(View v) {
            if(checkParameters(v)){
                MqttConnectOptions conOpt = new MqttConnectOptions();

                // The basic client information
                String server = PreferenceManager.getDefaultSharedPreferences(v.getContext()).getString("server", "empty");
                String clientId = PreferenceManager.getDefaultSharedPreferences(v.getContext()).getString("clientid", "empty");
                int port = Integer.parseInt(PreferenceManager.getDefaultSharedPreferences(v.getContext()).getString("port", ""));
                boolean cleanSession = PreferenceManager.getDefaultSharedPreferences(v.getContext()).getBoolean("csession",false);
                boolean ssl=false;

                String uri= "tcp://" + server + ":" + port;

                MqttAndroidClient client;
                client = new MqttAndroidClient(v.getContext(),uri,clientId);


                // create a client handle
                clientHandle = uri + clientId;

                // last will message
                String message = new String();
                String topic = new String();
                Integer qos = 0;
                Boolean retained = false;

                // connection options

                String username = new String();

                String password = new String();

                int timeout = ActivityConstants.defaultTimeOut;
                int keepalive = ActivityConstants.defaultKeepAlive;

                connection = new Connection(clientHandle, clientId, server, port,
                        v.getContext(), client, ssl);



                connection.registerChangeListener(changeListener);

               // connection.registerChangeListener(changeListener);
                // connect client

                String[] actionArgs = new String[1];
                actionArgs[0] = clientId;
                connection.changeConnectionStatus(ConnectionStatus.CONNECTING);

                conOpt.setCleanSession(cleanSession);
                conOpt.setConnectionTimeout(timeout);
                conOpt.setKeepAliveInterval(keepalive);
                if (!username.equals(ActivityConstants.empty)) {
                    conOpt.setUserName(username);
                }
                if (!password.equals(ActivityConstants.empty)) {
                    conOpt.setPassword(password.toCharArray());
                }


                final ActionListener callback = new ActionListener(v.getContext(),
                        ActionListener.Action.CONNECT, clientHandle, actionArgs);

                boolean doConnect = true;

                if ((!message.equals(ActivityConstants.empty))
                        || (!topic.equals(ActivityConstants.empty))) {
                    // need to make a message since last will is set
                    try {
                        conOpt.setWill(topic, message.getBytes(), qos.intValue(),
                                retained.booleanValue());
                    }
                    catch (Exception e) {
                        Log.e(this.getClass().getCanonicalName(), "Exception Occured", e);
                        doConnect = false;
                        callback.onFailure(null, e);
                    }
                }
                client.setCallback(new MqttCallbackHandler(v.getContext(), clientHandle));


                //set traceCallback
                client.setTraceCallback(new MqttTraceCallback());

                connection.addConnectionOptions(conOpt);

                if (doConnect) {
                    try {
                        client.connect(conOpt, v.getContext(), callback);
                        Toast.makeText(v.getContext(),"Connecting",Toast.LENGTH_SHORT).show();
                    }
                    catch (MqttException e) {
                        Log.e(this.getClass().getCanonicalName(),
                                "MqttException Occured", e);
                    }
                }


                    if (client.isConnected()) {
                        Toast.makeText(v.getContext(), "Connected", Toast.LENGTH_SHORT).show();

                    }
                    else{
                        Toast.makeText(v.getContext(), "Can't connect", Toast.LENGTH_SHORT).show();
                    }



            }
        }

        private boolean checkParameters(View view) {
            if(PreferenceManager.getDefaultSharedPreferences(view.getContext()).getString("clientid","empty").equals("empty")){
                Toast.makeText(view.getContext(),"Please set the client ID in the settings.",Toast.LENGTH_LONG).show();
                Log.d("checkParameters","no clientid");
                return false;
            }
            if (PreferenceManager.getDefaultSharedPreferences(view.getContext()).getString("server","empty").equals("empty")){
                Toast.makeText(view.getContext(),"Please set the Server name in the settings.",Toast.LENGTH_LONG).show();
                return false;
            }
            if (PreferenceManager.getDefaultSharedPreferences(view.getContext()).getString("port","").equals("")){
                Toast.makeText(view.getContext(),"Please set the port number in the settings.",Toast.LENGTH_LONG).show();
                return false;
            }
            return true;
        }
    }



    private class ChangeListener implements PropertyChangeListener {

        /**
         * @see PropertyChangeListener#propertyChange(PropertyChangeEvent)
         */
        @Override
        public void propertyChange(PropertyChangeEvent event) {

            if (!event.getPropertyName().equals(ActivityConstants.ConnectionStatusProperty)) {
                return;
            }


        }

    }
}


