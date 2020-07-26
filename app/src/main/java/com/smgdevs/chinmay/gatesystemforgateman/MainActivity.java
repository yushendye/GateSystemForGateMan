package com.smgdevs.chinmay.gatesystemforgateman;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements MessageListener{
    String communication_id;
    String gateman_pn;
    String entry_date;
    String gate_man_pn_time;
    String gate_close_time;

    String final_commit_str = "";

    EditText inc_train, sm_pn, sm_pn_time;
    EditText gm_pn_txt, pn_time_txt, time_train_passed;
    TextView staus;

    EditText login_id, password;

    Button send_btn;
    Button final_cmmit;

    Dialog login_dialog;
    boolean msg_rec = false;
    String final_msg = "error has occurred";

    boolean is_gate_closed = false;

    String[] arr = new String[6];
    boolean should_enable = false;
    boolean write = false;
    boolean write2 = false;
    byte[] read_buffer;
    volatile boolean stop_worker;

    private final String FINAL_COMMIT_URL = "https://smgdevelopers.000webhostapp.com/final_commit.php";
    private final String GATE_MAN_PN_URL = "https://smgdevelopers.000webhostapp.com/insert_gateman_pn.php";
    private final String GATE_MAN_REC_LOG = "https://smgdevelopers.000webhostapp.com/gateman_rec_log.php";
    private final String GATE_CLOSE_LOG = "https://smgdevelopers.000webhostapp.com/gate_close_log.php";
    private final String GATE_OPEN_LOG = "https://smgdevelopers.000webhostapp.com/gate_open_log.php";
    private final String LOGIN_URL = "https://smgdevelopers.000webhostapp.com/gateman_login.php";

    private String GATE_ID = "LC_21";
    private String CONTACT = "7276125198";

    //bluetooth required classes
    BluetoothAdapter bluetoothAdapter;
    BluetoothSocket bluetoothSocket;
    Set<BluetoothDevice> paired_device_list;
    private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    String bt_address = null;
    String bt_name = null;
    String str_gate_close_time = "";

    InputStream inputStream;
    OutputStream outputStream;

    private int SMS_PERMISSION_CODE = 1;

    public void bluetooth_connect(){
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bt_address = bluetoothAdapter.getAddress();
            paired_device_list = bluetoothAdapter.getBondedDevices();

            if(paired_device_list.size() > 0){
                for(BluetoothDevice device : paired_device_list){
                    bt_address = device.getAddress();
                    bt_name = device.getName();
                }
            }
        }catch (Exception e){
            staus.setText(e.getLocalizedMessage());
        }
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(bt_address);
        try {
            bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(uuid);
        }catch (Exception e){
            staus.setText("RF connect failed " + e.getLocalizedMessage());
        }
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
        try {
            bluetoothSocket.connect();
            staus.setText("Connection to bluetooth successful!!");
            inputStream = bluetoothSocket.getInputStream();
            outputStream = bluetoothSocket.getOutputStream();
        }catch (Exception e){
            staus.setText("error ");
        }
    }

    public void readBluetoothData(){
        request_permissions();
        if(bluetoothAdapter == null) {
            staus.setText(R.string.try_again);
        }
        final Handler handler = new Handler();
        stop_worker = false;
        read_buffer = new byte[1024];
        Thread thread  = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stop_worker)
                {
                    try
                    {
                        int byteCount = inputStream.available();
                        if(byteCount > 0)
                        {
                            final byte[] rawBytes = new byte[byteCount];
                            int[] ints = new int[byteCount];
                            inputStream.read(rawBytes);
                            final String string = String.valueOf(rawBytes[0]);
                            final int gate_state = Integer.parseInt(string);
                            handler.post(new Runnable() {
                                public void run() {
                                    staus.setText(string);
                                    if((char)gate_state == '1' && msg_rec && write) {
                                        write = false;
                                        str_gate_close_time = getTime();
                                        send_btn.setEnabled(true);
                                        send_btn.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.round_btn_ack));
                                        StringRequest request = new StringRequest(StringRequest.Method.POST, GATE_CLOSE_LOG, new Response.Listener<String>() {
                                            @Override
                                            public void onResponse(String response) {

                                            }
                                        }, new Response.ErrorListener() {
                                            @Override
                                            public void onErrorResponse(VolleyError error) {
                                                staus.setText(error.toString());
                                            }
                                        }){
                                            @Override
                                            protected Map<String, String> getParams() throws AuthFailureError {
                                                Map<String, String> map = new HashMap<>();
                                                map.put("gate_no", GATE_ID);
                                                map.put("close_time", str_gate_close_time);
                                                map.put("close_date", getDate());
                                                map.put("gateman_id", GATE_ID);
                                                return map;
                                            }
                                        };
                                        write2 = true;
                                        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                                        queue.add(request);
                                    }
                                    if((char)gate_state == '0' && write2) {
                                        write = false;
                                        send_btn.setEnabled(false);
                                        send_btn.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.disabled));
                                        StringRequest request = new StringRequest(StringRequest.Method.POST, GATE_OPEN_LOG, new Response.Listener<String>() {
                                            @Override
                                            public void onResponse(String response) {

                                            }
                                        }, new Response.ErrorListener() {
                                            @Override
                                            public void onErrorResponse(VolleyError error) {
                                            }
                                        }){
                                            @Override
                                            protected Map<String, String> getParams() throws AuthFailureError {
                                                Map<String, String> map = new HashMap<>();
                                                map.put("gate_no", GATE_ID);
                                                map.put("open_date", getDate());
                                                map.put("open_time", getTime());
                                                map.put("gateman_id", GATE_ID);
                                                return map;
                                            }
                                        };
                                        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
                                        queue.add(request);
                                        write2 = false;
                                    }
                                }
                            });

                        }
                    }
                    catch (Exception ex)
                    {
                        staus.setText(ex.getMessage());
                        stop_worker = true;
                    }
                }
            }
        });
        thread.start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        send_btn = findViewById(R.id.btn_final_ack);
        send_btn.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.disabled));
        final_cmmit = findViewById(R.id.final_commit);

        inc_train = findViewById(R.id.incoming_train);
        sm_pn = findViewById(R.id.sm_pn);
        sm_pn_time = findViewById(R.id.sm_pn_time);

        gm_pn_txt = findViewById(R.id.gm_pn_ret);
        pn_time_txt = findViewById(R.id.pn_time_ret);
        time_train_passed = findViewById(R.id.time_train_passed);

        staus = findViewById(R.id.status);
        send_btn.setEnabled(false);

        check_message_read_permission();
        check_message_receive_permission();
        check_message_send_permission();

        request_permissions();
        bluetooth_connect();

        popup_login();

        if(bluetoothSocket != null && bluetoothAdapter != null)
            readBluetoothData();
        else
            staus.setText("Bluetooth device not connected");
    }

    public void check_message_send_permission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
        } else {
            requestSendSMSPermission();
        }
    }

    public void check_message_read_permission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED) {
        } else {
            requestReadSMSPermission();
        }
    }
    public void check_message_receive_permission(){
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
        } else {
            requestReceiveSMSPermission();
        }
    }

    public void requestSendSMSPermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.SEND_SMS)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed for reading bluetooth signals")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[] {Manifest.permission.SEND_SMS}, SMS_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.READ_SMS}, SMS_PERMISSION_CODE);
        }
    }

    public void requestReadSMSPermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.READ_SMS)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed for reading bluetooth signals")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[] {Manifest.permission.READ_SMS}, SMS_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.READ_SMS}, SMS_PERMISSION_CODE);
        }
    }

    public void requestReceiveSMSPermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.RECEIVE_SMS)) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission needed")
                    .setMessage("This permission is needed for reading bluetooth signals")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this,
                                    new String[] {Manifest.permission.RECEIVE_SMS}, SMS_PERMISSION_CODE);
                        }
                    })
                    .setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .create().show();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[] {Manifest.permission.RECEIVE_SMS}, SMS_PERMISSION_CODE);
        }
    }

    @Override
    public void messageReceived(String msg) {
        //This means gate man has received the PN
        Vibrator vb = (Vibrator)getSystemService(VIBRATOR_SERVICE);
        vb.vibrate(2000);
        arr = msg.split(",");
        inc_train.setText(arr[1]);
        sm_pn.setText(arr[4]);
        sm_pn_time.setText(arr[2]);
        msg_rec = true;
        write = true;
        final_commit_str = msg;

        //String msg = communication_id + "," + my_train_no + "," + getTime()  + "," + STATION_MASTER_ID + "," + station_master_pn + "," + date + "," + GM_ID;
        StringRequest request = new StringRequest(StringRequest.Method.POST, GATE_MAN_REC_LOG, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                staus.setText(error.toString());
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<>();
                map.put("gate_no", GATE_ID);
                map.put("rec_date", getDate());
                map.put("rec_time", getTime());
                map.put("sm_pn", arr[4]);
                map.put("train_no", arr[1]);
                map.put("gm_id", arr[6]);
                return map;
            }
        };
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        queue.add(request);
    }

    public void print(String msg){
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
    }

    public void request_permissions(){
        //ask user to turn on bluetooth first
        Intent i1 = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(i1, 1);
        //ask for other permissions
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, 1);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, 1);
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.VIBRATE}, 1);
    }

    public String getTime(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String time = simpleDateFormat.format(ts);
        return time;
    }

    public String getGateClosedTime(){
        if(is_gate_closed)
            return getTime();
        else
            return "";
    }

    public String getDate(){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy.MM.dd");
        Timestamp ts = new Timestamp(System.currentTimeMillis());
        String date = simpleDateFormat.format(ts);
        return  date;
    }

    public int generateRandom(){
        Random r = new Random();
        int random_no = r.nextInt(100);
        return  random_no;
    }

    public int generateCommRandom(){
        Random r = new Random();
        int random_no = r.nextInt(1000);
        return  random_no;
    }


    public void setParameters(){
        communication_id = "kr_comm_" + generateCommRandom();
        gateman_pn = generateRandom() + "";
        entry_date = getDate();
        gate_man_pn_time = getTime();
        gate_close_time = getGateClosedTime();
    }

    public  void temp(){
        inc_train.setText("11211");
        sm_pn.setText("44");
        sm_pn_time.setText("21:17:00");
        msg_rec = true;
        write = true;
    }

    public void sendPNAndReport(View view){
        setParameters();
        send_btn.setEnabled(false);
        send_btn.setBackground(ContextCompat.getDrawable(getApplicationContext(), R.drawable.disabled));

        gm_pn_txt.setText(gateman_pn);
        pn_time_txt.setText(gate_man_pn_time);

        StringRequest request = new StringRequest(StringRequest.Method.POST, GATE_MAN_PN_URL, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                should_enable = true;
                final_cmmit.setEnabled(should_enable);
                sendSMS(CONTACT, final_msg);
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                staus.setText("Volley Exception : " + error.toString());
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<>();
                map.put("train_no",arr[1]);
                map.put("communication_id", communication_id);
                map.put("gm_id", GATE_ID);
                map.put("gm_pn", gateman_pn + "");
                map.put("entry_date", getDate());
                map.put("gm_pn_time", getTime());
                map.put("gate_close_time", getTime());

                final_msg =  communication_id + "," + GATE_ID + "," + gateman_pn + "," + entry_date + "," + gate_man_pn_time + "," + gate_close_time;
                final_commit_str = final_commit_str + "," + final_msg;
                return map;
            }
        };
        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);
        queue.add(request);
    }

    public void sendSMS(String number, String msg){
        SmsManager manager = SmsManager.getDefault();
        manager.sendTextMessage(number, null, msg, null, null);
    }

    public void commit(View view){
        //seq is comm_id, train_no, sm_pn_time, sm_id, sm_pn, date, gm_id, comm_id, gate_man_id,  gate_man_pn, entry_date, gm_pn_time, gate_close_time
        //        0        1          2            3     4     5       6        7           8           9            10      11           12
        final String[] array = final_commit_str.split(",");

        StringRequest request = new StringRequest(StringRequest.Method.POST, FINAL_COMMIT_URL, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                staus.setText("Volley Error : " + error);
                clear();
            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<>();
                map.put("communication_id", array[0]);
                map.put("train_no", array[1]);
                map.put("sm_pn_time", array[2]);
                map.put("sm_id", array[3]);
                map.put("sm_pn", array[4]);
                map.put("sm_pn_date", array[5]);
                map.put("gm_id", array[6]);
                map.put("gm_pn", array[9]);
                map.put("gm_entry_date", array[10]);
                map.put("gm_pn_time", array[11]);
                map.put("gate_close_time", str_gate_close_time);
                map.put("train_pass_time", time_train_passed.getText().toString());
                return map;
            }
        };
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        queue.add(request);
    }

    public void clear(){
        inc_train.setText("");
        sm_pn.setText("");
        sm_pn_time.setText("");
        gm_pn_txt.setText("");
        pn_time_txt.setText("");
        time_train_passed.setText("");
    }

    public void popup_login(){
        login_dialog = new Dialog(this);
        login_dialog.setContentView(R.layout.popup_login);
        login_dialog.setCancelable(false);
        login_dialog.show();
    }

    public void login(View view){
        login_id = login_dialog.findViewById(R.id.login_id);
        password = login_dialog.findViewById(R.id.password);

        final String id = login_id.getText().toString();
        final String pwd = password.getText().toString();

        StringRequest request = new StringRequest(StringRequest.Method.POST, LOGIN_URL, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try{
                    JSONArray array = new JSONArray(response);
                    JSONObject object = array.getJSONObject(0);

                    GATE_ID = object.getString("gateman_id");
                    CONTACT = object.getString("sm_contact");
                    MessageChecker.bindListener(MainActivity.this);
                    print(GATE_ID + " LOGGED IN SUCCESSFULLY!");
                    login_dialog.dismiss();
                }catch (Exception e){
                    staus.setText(e.getLocalizedMessage());
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        }){
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> map = new HashMap<>();
                map.put("gm_login", id);
                map.put("gm_pass", pwd);
                return map;
            }
        };
        RequestQueue queue = Volley.newRequestQueue(getApplicationContext());
        queue.add(request);
    }
}
