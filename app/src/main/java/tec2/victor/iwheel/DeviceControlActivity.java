package tec2.victor.iwheel;

import android.app.ActionBar;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.IBinder;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;
import tec2.victor.iwheel.barcode.BarcodeCaptureActivity;
import tec2.victor.iwheel.R;
import tec2.victor.iwheel.joystick.JoyStickClass;


/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements SensorEventListener {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    private static final int BARCODE_READER_REQUEST_CODE = 1;
    private final int REQ_CODE_SPEECH_INPUT = 100;

    private TextView mResultTextView;
    private Button btnSpeak;
    byte[] text = new byte[0];
    private String password = "";

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private int[] RGBFrame = {0, 0, 0};
    private TextView isSerial;
    private TextView mConnectionState;
    private String mDeviceName;
    private String mDeviceAddress;
    //  private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

    private SensorManager mSensorManager;
    private Sensor mAccel;
    private SeekBar seekBar;
    private RadioButton backward;
    private int prevent = 0;
    private int MaxSeek = 200;
    private int MinSeek = 0;
    private int xAxis = 0;
    private int yAxis = 0;
    private int motorLeft = 0;
    private int motorRight = 0;
    private int xMax;
    private int yMax;
    private int yThreshold;
    private int pwmMax;
    private int xR;
    private int justone =0;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    RelativeLayout layout_joystick,layout_movement_controller,layout_speech_controller;
    LinearLayout layout_menu,layout_security,layout_joystick_controller;
    JoyStickClass js;


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                setGattServices(mBluetoothLeService.getSupportedGattServices());

            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {


                setKey(intent.getStringExtra(BluetoothLeService.EXTRA_DATA.toString()));


            }
        }
    };


    private void promptSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, new Long(2000));
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT,
               getString(R.string.speech_prompt));
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(getApplicationContext(),
                   getString(R.string.speech_not_supported),
                    Toast.LENGTH_SHORT).show();
        }
    }



    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        layout_joystick = (RelativeLayout)findViewById(R.id.layout_joystick);
        layout_joystick_controller = (LinearLayout) findViewById(R.id.layout_joystick_controller);
        layout_movement_controller = (RelativeLayout) findViewById(R.id.layout_movement_controller);
        layout_speech_controller = (RelativeLayout) findViewById(R.id.layout_speech_controller);

        layout_menu = (LinearLayout)findViewById(R.id.controller_menu);
        layout_security = (LinearLayout)findViewById(R.id.security);
        btnSpeak = (Button) findViewById(R.id.btnSpeak);
        ActionBar bar = getActionBar();
        bar.setBackgroundDrawable(new ColorDrawable(Color.parseColor("#3F51B5")));
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(this.getResources().getColor(R.color.colorPrimaryDark));

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        // is serial present?
        isSerial = (TextView) findViewById(R.id.isSerial);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        js = new JoyStickClass(getApplicationContext()
                , layout_joystick, R.drawable.image_button);
        js.setStickSize(150, 150);
        js.setLayoutSize(620, 620);
        js.setLayoutAlpha(150);
        js.setStickAlpha(120);
        js.setOffset(90);
        js.setMinimumDistance(0);

        layout_joystick.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View arg0, MotionEvent arg1) {
                js.drawStick(arg1);
                if(arg1.getAction() == MotionEvent.ACTION_DOWN
                        || arg1.getAction() == MotionEvent.ACTION_MOVE) {

                    int direction = js.get8Direction();
                    if(direction == JoyStickClass.STICK_UP) {
                        sendDataToBLE("w");
                    } else if(direction == JoyStickClass.STICK_UPRIGHT) {
                        sendDataToBLE("e");
                    } else if(direction == JoyStickClass.STICK_RIGHT) {
                        sendDataToBLE("d");
                    } else if(direction == JoyStickClass.STICK_DOWNRIGHT) {
                        sendDataToBLE("c");
                    } else if(direction == JoyStickClass.STICK_DOWN) {
                        sendDataToBLE("s");
                    } else if(direction == JoyStickClass.STICK_DOWNLEFT) {
                        sendDataToBLE("z");
                    } else if(direction == JoyStickClass.STICK_LEFT) {
                        sendDataToBLE("a");
                    } else if(direction == JoyStickClass.STICK_UPLEFT) {
                        sendDataToBLE("q");
                    } else if(direction == JoyStickClass.STICK_NONE) {
                    }

                } else if(arg1.getAction() == MotionEvent.ACTION_UP) {
                    sendDataToBLE("n");
                }
                return true;
            }
        });




        mResultTextView = (TextView) findViewById(R.id.result_textview);

        Button scanBarcodeButton = (Button) findViewById(R.id.scan_barcode_button);
        scanBarcodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
                startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
            }
        });
        Button joystick = (Button) findViewById(R.id.joystick);
        joystick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_menu.setVisibility(View.GONE);
                layout_security.setVisibility(View.GONE);
                layout_joystick_controller.setVisibility(View.VISIBLE);


            }
        });
        Button close_joystick = (Button) findViewById(R.id.close_joystick);
        close_joystick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_menu.setVisibility(View.VISIBLE);
                layout_security.setVisibility(View.GONE);
                layout_joystick_controller.setVisibility(View.GONE);


            }
        });

        Button movement = (Button) findViewById(R.id.movement);
        movement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_menu.setVisibility(View.GONE);
                layout_security.setVisibility(View.GONE);
                layout_movement_controller.setVisibility(View.VISIBLE);


            }
        });

        Button close_movement = (Button) findViewById(R.id.close_movement);
        close_movement.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_menu.setVisibility(View.VISIBLE);
                layout_security.setVisibility(View.GONE);
                layout_movement_controller.setVisibility(View.GONE);


            }
        });

        Button speech = (Button) findViewById(R.id.speech);
        speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_menu.setVisibility(View.GONE);
                layout_security.setVisibility(View.GONE);
                layout_speech_controller.setVisibility(View.VISIBLE);


            }
        });

        Button close_speech = (Button) findViewById(R.id.close_speech);
        close_speech.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                layout_menu.setVisibility(View.VISIBLE);
                layout_security.setVisibility(View.GONE);
                layout_speech_controller.setVisibility(View.GONE);


            }
        });

        btnSpeak.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                promptSpeechInput();
            }
        });

        xMax = Integer.parseInt((String) getResources().getText(R.string.default_xMax));
        xR = Integer.parseInt((String) getResources().getText(R.string.default_xR));
        yMax = Integer.parseInt((String) getResources().getText(R.string.default_yMax));
        yThreshold = Integer.parseInt((String) getResources().getText(R.string.default_yThreshold));
        pwmMax = Integer.parseInt((String) getResources().getText(R.string.default_pwmMax));
        seekBar = (SeekBar) findViewById(R.id.customSeekBar);
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccel = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        backward = (RadioButton) findViewById(R.id.backward);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            int progressChangedValue = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressChangedValue = progress;
                if (!backward.isChecked()) {
                    if (progress > 1 && prevent!=1) {
                        sendDataToBLE("w");
                        prevent = 1;
                    } else {
                        if (progress < 1 ) {
                            prevent = 0;
                            sendDataToBLE("n");
                        }
                    }
                } else {

                    if (backward.isChecked()&& progress > 1 && prevent!=2 ) {
                        sendDataToBLE("s");
                        prevent = 2;
                    } else {

                        if (progress < 1 ) {
                            prevent = 0;
                            sendDataToBLE("n");
                            backward.setChecked(false);
                        }

                    }


                }


            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                seekBar.setProgress(0);
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        passwordQuery();
        switch (requestCode) {

            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {

                    ArrayList<String> result = data
                            .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);

                    SpeechCommandListener(result.get(0));
                }
                break;
            }

            case BARCODE_READER_REQUEST_CODE: {
                if (resultCode == CommonStatusCodes.SUCCESS) {
                    if (data != null) {
                        Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);
                        Point[] p = barcode.cornerPoints;
                        //mResultTextView.setText(barcode.displayValue);

                        accessQuery(barcode.displayValue);
                    } else mResultTextView.setText(R.string.no_barcode_captured);
                } else Log.e(TAG, String.format(getString(R.string.barcode_error_format),
                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
            default:{
                super.onActivityResult(requestCode, resultCode, data);
                break;
            }

        }

    }


    public  void sendDataToBLE(String str) {
        Log.d(TAG, "Sending result=" + str);
        final byte[] tx = str.getBytes();
        if (mConnected) {
            characteristicTX.setValue(tx);
            mBluetoothLeService.writeCharacteristic(characteristicTX);
            mBluetoothLeService.setCharacteristicNotification(characteristicRX, true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
        mSensorManager.registerListener(this, mAccel, SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
        mSensorManager.unregisterListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void passwordQuery() {
        sendDataToBLE("p");
    }

    private void setKey(String key) {
        password = key;

    }

    private void accessQuery(String key) {

        if(key.equals(password)){
            mResultTextView.setText("valid password");
            layout_security.setVisibility(View.GONE);
            layout_menu.setVisibility(View.VISIBLE);
        }else{
            mResultTextView.setText("invalid password");

        }

    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void setGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();


        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));

            // If the service exists for HM 10 Serial, say so.
            if (SampleGattAttributes.lookup(uuid, unknownServiceString) == "HM 10 Serial") {
                isSerial.setText("Yes");
                // get characteristic when UUID matches RX/TX UUID
                characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
                characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
                mBluetoothLeService.setCharacteristicNotification(
                        characteristicRX, true);

                break;
            } else {
                isSerial.setText("No");
            }
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

        }

    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    private void SpeechCommandListener(String command){
    switch (command){
        case "adelante":
            sendDataToBLE("w");
            break;
        case "detente":
            sendDataToBLE("n");
            break;
        case "atrás":
            sendDataToBLE("s");
            break;
        case "izquirda":
            sendDataToBLE("a");
            break;
        case "derecha":
            sendDataToBLE("d");
            break;
    }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {

        xAxis = Math.round(e.values[0]*pwmMax/xR);
        yAxis = Math.round(e.values[1]*pwmMax/yMax);

            if(yAxis >= 0 && yAxis < yThreshold) yAxis = 0;
        else if(yAxis < 0 && yAxis > -yThreshold) yAxis = 0;

        if(xAxis > 0) {
            motorRight = yAxis;
            if(Math.abs(Math.round(e.values[0])) > xR){
                motorLeft = Math.round((e.values[0]-xR)*pwmMax/(xMax-xR));
                motorLeft = Math.round(-motorLeft * yAxis/pwmMax);
            }
            else motorLeft = yAxis - yAxis*xAxis/pwmMax;
        }
        else if(xAxis < 0) {
            motorLeft = yAxis;
            if(Math.abs(Math.round(e.values[0])) > xR){
                motorRight = Math.round((e.values[0])-xR)*pwmMax/(xMax-xR);
                motorRight = Math.round(-motorRight * yAxis/pwmMax);
            }
            else motorRight = yAxis - yAxis*xAxis/pwmMax;
        }

        if(motorRight <80 && motorLeft < 80 && justone!=1 && prevent==1){
            sendDataToBLE("w");
            justone=1;
        }else if(motorRight <80 && motorLeft < 80 && justone!=10 && prevent==2){
            sendDataToBLE("2");
            justone=10;
        }

        if(motorRight <= 200 && motorRight > 120&& prevent == 1 && justone!=3){
            sendDataToBLE("e");
            justone=3;
        }else if(motorRight > 300 && prevent == 1 && justone!=4){
            sendDataToBLE("d");
            justone=4;
        }else if(motorRight <= 200 && motorRight > 120&& prevent == 2 && justone!=8){
            sendDataToBLE("c");
            justone=8;
        }

        if(motorLeft <= 200 && motorLeft > 120&& prevent == 1 && justone!=5){
            sendDataToBLE("q");
            justone=5;
        }else if(motorLeft > 300 && prevent == 1 && justone!=6){
            sendDataToBLE("a");
            justone=6;
        }else if(motorLeft <= 200 && motorLeft > 120&& prevent == 2 && justone!=7){
            sendDataToBLE("z");
            justone=7;
        }

    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

}