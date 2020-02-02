package com.example.tommi.coordinates;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 1000;
    TextView info_box;
    Button btn_start, btn_stop;
    EditText interval_box, user_box, comment_box, ip_port_box;
    Spinner dropdown;
    FusedLocationProviderClient fusedLocationProviderClient;
    LocationRequest locationRequest;
    LocationCallback locationCallback;
    Location PREV_LOCATION, CUR_LOCATION;
    Intent serviceIntent;
    static InetAddress RESOLVED_IP;
    long UNIX1, UNIX2, HOUR, MIN, SEC, INTERVAL_TIMER = 0;
    String USER, COMMENT, IP_PORT = "";
    String[] IP_PORT_SPLIT;
    double ALT, LAT1, LAT2, LON1, LON2, SPEED, DISTANCE, TOTAL_DIST, AVG_SPEED, MESSAGES, HELPER_SPEED = 0;
    static int PORT = 0;
    static String MSG;
    boolean started = false;
    SharedPreferences myPrefs;
    String infostring = "LAT: \nLON: \nALT: \nDistance: \nSpeed: \nMessages Sent: \nTotal Time: \nTotal Dist.: \nAvg. Speed: ";

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CODE: {
                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    }
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Init view
        info_box = findViewById(R.id.info_box);
        btn_start = findViewById(R.id.btn_start_updates);
        btn_stop = findViewById(R.id.btn_stop_updates);
        interval_box = findViewById(R.id.interval_box);
        user_box = findViewById(R.id.user_box);
        comment_box = findViewById(R.id.comment_box);
        ip_port_box = findViewById(R.id.ip_port_box);
        dropdown = findViewById(R.id.dropdown);
        String[] items = new String[]{"Seconds", "Minutes", "Hours"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, items);
        dropdown.setAdapter(adapter);

        myPrefs = getSharedPreferences("prefID", Context.MODE_PRIVATE);
        INTERVAL_TIMER = myPrefs.getLong("INTERVAL_TIMER", 0);
        USER = myPrefs.getString("USER", "");
        COMMENT = myPrefs.getString("COMMENT", "");
        IP_PORT = myPrefs.getString("IP_PORT", "");

        if (INTERVAL_TIMER != 0) {
            interval_box.setText(String.valueOf(INTERVAL_TIMER));
        }
        user_box.setText(USER);
        comment_box.setText(COMMENT);
        ip_port_box.setText(IP_PORT);

        info_box.setText(infostring + "\nStatus: Inactive");

        //Check permission runtime
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
        }
    }

    public void startActivity(View v) {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
            return;
        }
        if (!started && interval_box.length() != 0 && user_box.length() != 0 && comment_box.length() != 0
                && ip_port_box.length() != 0) {
            buildLocationCallBack();
            fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
            IP_PORT = ip_port_box.getText().toString();
            IP_PORT_SPLIT = IP_PORT.split(":");
            PORT = Integer.parseInt(IP_PORT_SPLIT[1]);
            INTERVAL_TIMER = Long.parseLong(interval_box.getText().toString());
            USER = user_box.getText().toString();
            COMMENT = comment_box.getText().toString();
            info_box.setText(infostring + "\nStatus: Initializing...");
            //Save input fields
            SharedPreferences.Editor editor = myPrefs.edit();
            editor.putLong("INTERVAL_TIMER", INTERVAL_TIMER);
            editor.putString("USER", USER);
            editor.putString("COMMENT", COMMENT);
            editor.putString("IP_PORT", IP_PORT);
            editor.apply();
            String spinneritem = dropdown.getSelectedItem().toString();
            if (spinneritem == "Seconds") {
                INTERVAL_TIMER *= 1000;
            } else if (spinneritem == "Minutes") {
                INTERVAL_TIMER *= 60000;
            } else if (spinneritem == "Hours") {
                INTERVAL_TIMER *= 3600000;
            }
            buildLocationRequest();
            new DNSResolve().execute();
        } else if (!started) {
            info_box.setText(infostring + "\nPlease fill out all fields!");
        } else if (started) {
            started = false;
            btn_start.setText("Start");
            info_box.setText(infostring + "\nStatus: Inactive");
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
            stopService(serviceIntent);
            LAT1 = 0;
            LAT2 = 0;
        }
    }

    public void resetActivity(View v) {
        LAT1 = LAT2 = MESSAGES = TOTAL_DIST = HELPER_SPEED = AVG_SPEED = SEC = MIN = HOUR = 0;
        infostring = "LAT: \nLON: \nALT: \nDistance: \nSpeed: \nMessages Sent: \nTotal Time: \nTotal Dist.: \nAvg. Speed: ";
        if (started) {
            info_box.setText(infostring + "\nStatus: Active");
        } else if (!started) {
            info_box.setText(infostring + "\nStatus: Inactive");
        }
    }

    public void startUpdates() {
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.myLooper());
        serviceIntent = new Intent(this, MyService.class);
        startForegroundService(serviceIntent);
        started = true;
        btn_start.setText("Stop");
    }

    public void dnsfail() {
        info_box.setText(infostring + "\nDNS Resolve failed!");
    }

    private void buildLocationCallBack() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (LAT1 == 0) { //Get initial position
                        PREV_LOCATION = location;
                        LAT1 = location.getLatitude();
                        LON1 = location.getLongitude();
                        UNIX1 = System.currentTimeMillis() / 1000;
                        info_box.setText(infostring + "\nStatus: Active");
                    } else { //Get new position
                        if (LAT2 != 0) { //Save new position
                            PREV_LOCATION = CUR_LOCATION;
                            LAT1 = LAT2;
                            LON1 = LON2;
                            UNIX1 = UNIX2;
                        }
                        MESSAGES++;
                        CUR_LOCATION = location;
                        LAT2 = location.getLatitude();
                        LON2 = location.getLongitude();
                        ALT = location.getAltitude();
                        UNIX2 = System.currentTimeMillis() / 1000;
                        DISTANCE = location.distanceTo(PREV_LOCATION);
                        TOTAL_DIST += (DISTANCE / 1000);
                        SPEED = (DISTANCE / 1000) / ((UNIX2 - UNIX1) / 1000f) * 3.6f;
                        HELPER_SPEED += SPEED;
                        AVG_SPEED = HELPER_SPEED / MESSAGES;
                        SEC += (UNIX2 - UNIX1);
                        while (SEC >= 60) {
                            SEC -= 60;
                            MIN++;
                            while (MIN >= 60) {
                                MIN -= 60;
                                HOUR++;
                            }
                        }
                        //String everything
                        String ALTS = String.format(Locale.US, "%.1f", ALT);
                        String DISTS = String.format(Locale.US, "%.3f", DISTANCE);
                        String SPEEDS = String.format(Locale.US, "%.3f", SPEED);
                        String AVG_SPEEDS = String.format(Locale.US, "%.3f", AVG_SPEED);
                        String TOTAL_DISTS = String.format(Locale.US, "%.3f", TOTAL_DIST);
                        String MSGS = String.format(Locale.US, "%.0f", MESSAGES);
                        //Set info_box string and send message
                        infostring = "LAT: " + LAT2 + "\nLON: " + LON2 + "\nALT: " + ALTS + "\nDistance: " + DISTS + " m\nSpeed: " + SPEEDS +
                                " km/h\nMessages Sent: " + MSGS + "\nTotal Time: " + HOUR + ":" + MIN + ":" + SEC + "\nTotal Dist.: " + TOTAL_DISTS
                                + " km\nAvg. Speed: " + AVG_SPEEDS + " km/h";
                        info_box.setText(infostring + "\nStatus: Active");
                        MSG = "TIMESTAMP:" + UNIX2 + "\tUSER:" + USER + "\tLOCATION:" + LAT2 + "," + LON2 + "\tALTITUDE: " + ALTS + "\tSPEED: " + SPEEDS
                                + "\tAVG_SPEED: " + AVG_SPEEDS + "\tDISTANCE: " + DISTS +
                                "\tTOTAL_DISTANCE: " + TOTAL_DISTS + "\tTOTAL_TIME: " + HOUR + ":" + MIN + ":" + SEC + "\tCOMMENT: " + COMMENT;
                        new SendMSG().execute();
                    }
                }
            }
        };
    }

    private void buildLocationRequest() {
        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(INTERVAL_TIMER);
        locationRequest.setFastestInterval(INTERVAL_TIMER / 2);
        locationRequest.setSmallestDisplacement(0);
    }

    class DNSResolve extends AsyncTask<Void, Void, Void> {
        String FAILURE = "READY";

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                RESOLVED_IP = InetAddress.getByName(IP_PORT_SPLIT[0]);
                String s = RESOLVED_IP.getHostAddress();
                RESOLVED_IP = InetAddress.getByName(s);
                FAILURE = "READY";
            } catch (Exception ex) {
                FAILURE = "FAILURE";
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            if (FAILURE == "READY") {
                startUpdates();
            } else if (FAILURE == "FAILURE") {
                dnsfail();
            }
        }
    }
}

class SendMSG extends AsyncTask<Void, Void, Void> {
    @Override
    protected Void doInBackground(Void... params) {
        try {
            int msg_length = MainActivity.MSG.length();
            byte[] message = MainActivity.MSG.getBytes();
            DatagramSocket s = new DatagramSocket();
            DatagramPacket p = new DatagramPacket(message, msg_length, MainActivity.RESOLVED_IP, MainActivity.PORT);
            s.send(p);
        } catch (Exception ex) {
        }
        return null;
    }

}