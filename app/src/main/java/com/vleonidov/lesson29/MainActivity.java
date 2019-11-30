package com.vleonidov.lesson29;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE = 101;
    private static final String TAG = "MainActivity";

    private static final int REQUEST_CHECK_SETTINGS = 102;

    private FusedLocationProviderClient mFusedLocationProviderClient;

    private LocationCallback mLocationCallback = new MainLocationCallback();

    private Geocoder mGeocoder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mGeocoder = new Geocoder(this, Locale.getDefault());
    }

    @Override
    protected void onResume() {
        super.onResume();

        checkGooglePlayServices();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (mFusedLocationProviderClient != null) {
            mFusedLocationProviderClient.removeLocationUpdates(mLocationCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 1) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    startLocationService();
                } else {
                    finish();
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        final LocationSettingsStates states = LocationSettingsStates.fromIntent(data);
        switch (requestCode) {
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        startLocationService();
                        break;
                    case Activity.RESULT_CANCELED:
                        finish();
                        break;
                    default:
                        break;
                }
                break;
        }
    }

    private void checkGooglePlayServices() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();

        int statusCode = googleApiAvailability.isGooglePlayServicesAvailable(this);

        if (statusCode != ConnectionResult.SUCCESS) {
            Dialog errorDialog = googleApiAvailability.getErrorDialog(this, statusCode,
                    0, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            finish();
                        }
                    });

            errorDialog.show();
        } else {
            checkPermission();
        }
    }

    private void checkPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermission();
        } else {
            checkDeviceSettings();
        }
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_CODE);
    }

    private void checkDeviceSettings() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(getLocationRequest());

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task<LocationSettingsResponse> task = client.checkLocationSettings(builder.build());

        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                Log.d(TAG, "onSuccess() called with: locationSettingsResponse = [" + locationSettingsResponse + "]");

                startLocationService();
            }
        });

        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                if (e instanceof ResolvableApiException) {
                    try {
                        Log.d(TAG, "onFailure() called with: e = [" + e + "]");
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MainActivity.this,
                                REQUEST_CHECK_SETTINGS);
                    } catch (IntentSender.SendIntentException sendEx) {
                        // Ignore the error.
                    }
                }
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void startLocationService() {
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        mFusedLocationProviderClient.requestLocationUpdates(getLocationRequest(), mLocationCallback, null);
    }

    private LocationRequest getLocationRequest() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000L);
        locationRequest.setFastestInterval(5000L);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        return locationRequest;
    }

    private static class GeocodingAsyncTask extends AsyncTask<Location, Void, List<Address>> {

        private final Geocoder mGeocoder;

        private GeocodingAsyncTask(Geocoder geocoder) {
            mGeocoder = geocoder;
        }

        @Override
        protected List<Address> doInBackground(Location... locations) {
            Location location = locations[0];

            try {
                List<Address> addressList = mGeocoder.getFromLocation(location.getLatitude(),
                        location.getLongitude(), 100);

                return addressList;
            } catch (IOException e) {
                e.printStackTrace();
            }

            return new ArrayList<>();
        }

        @Override
        protected void onPostExecute(List<Address> addressList) {
            super.onPostExecute(addressList);

            for (Address address : addressList) {
                Log.i(TAG, "Address = " + address);
            }
        }
    }

    private class MainLocationCallback extends LocationCallback {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            Log.d(TAG, "onLocationResult() called with: locationResult = [" + locationResult + "]");

            if (locationResult == null) {
                return;
            }

            for (Location location : locationResult.getLocations()) {
                Log.i(TAG, "Location from LocationResuls = " + location);

                GeocodingAsyncTask geocodingAsyncTask = new GeocodingAsyncTask(mGeocoder);
                geocodingAsyncTask.execute(location);
            }
        }

        ;
    }

}
