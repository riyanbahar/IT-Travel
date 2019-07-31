package com.application;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.multidex.MultiDex;
import android.util.Base64;
import android.util.Log;

import com.config.Config;
import com.config.UIConfig;
import com.db.DbHelper;
import com.db.Queries;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.libraries.location.MGLocationManager;
import com.libraries.usersession.UserAccessSession;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.twitter.sdk.android.core.DefaultLogger;
import com.twitter.sdk.android.core.Twitter;
import com.twitter.sdk.android.core.TwitterAuthConfig;
import com.twitter.sdk.android.core.TwitterConfig;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;

/**
 * Created by mg on 19/07/16.
 */
public class StoreFinderApplication extends Application implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private static DisplayImageOptions options;
    private static ImageLoader imageLoader;
    private static DisplayImageOptions optionsThumb;
    public static Location currentLocation;
    private static Queries q;
    private static SQLiteDatabase db;
    private static DbHelper dbHelper;
    private LocationRequest mLocationRequest;
    private GoogleApiClient mGoogleApiClient;
    private OnLocationListener mCallbackLocation;
    private GetAddressTask getAddressTask;
    public static List<Address> address;
    private Activity mActivity;
    private MGLocationManager manager;

    public static DisplayImageOptions getDisplayImageOptionsThumbInstance() {
        if(optionsThumb == null) {
            optionsThumb = new DisplayImageOptions.Builder()
                    .showImageOnLoading(UIConfig.IMAGE_PLACEHOLDER_PROFILE_THUMB)
                    .showImageForEmptyUri(UIConfig.IMAGE_PLACEHOLDER_PROFILE_THUMB)
                    .showImageOnFail(UIConfig.IMAGE_PLACEHOLDER_PROFILE_THUMB)
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .considerExifParams(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build();
        }
        return optionsThumb;
    }

    public static Queries getQueriesInstance(Context context) {
        if(q == null) {
            dbHelper = new DbHelper(context);
            q = new Queries(db, dbHelper);
        }
        return q;
    }

    public static DisplayImageOptions getDisplayImageOptionsInstance() {
        if(options == null) {
            options = new DisplayImageOptions.Builder()
                    .showImageOnLoading(UIConfig.IMAGE_PLACEHOLDER)
                    .showImageForEmptyUri(UIConfig.IMAGE_PLACEHOLDER)
                    .showImageOnFail(UIConfig.IMAGE_PLACEHOLDER)
                    .cacheInMemory(true)
                    .cacheOnDisk(true)
                    .considerExifParams(true)
                    .bitmapConfig(Bitmap.Config.RGB_565)
                    .build();
        }
        return options;
    }

    public static ImageLoader getImageLoaderInstance(Context context) {

        if(imageLoader == null) {
            imageLoader = ImageLoader.getInstance();
            imageLoader.init(ImageLoaderConfiguration.createDefault(context));
        }

        return imageLoader;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        FacebookSdk.sdkInitialize(getApplicationContext());
        AppEventsLogger.activateApp(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            if(!mGoogleApiClient.isConnected())
                mGoogleApiClient.connect();
        }

        getDebugKey();
        UserAccessSession accessSession = UserAccessSession.getInstance(getApplicationContext());
        float radius = accessSession.getFilterDistance();
        if(radius == 0 && !Config.AUTO_ADJUST_DISTANCE) {
            accessSession.setFilterDistance(Config.DEFAULT_FILTER_DISTANCE_IN_KM);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StrictMode.VmPolicy.Builder newbuilder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(newbuilder.build());
        }

        TwitterConfig config = new TwitterConfig.Builder(this)
                .logger(new DefaultLogger(Log.DEBUG))
                .twitterAuthConfig(new TwitterAuthConfig(Config.TWITTER_CONSUMER_KEY, Config.TWITTER_CONSUMER_SECRET))
                .debug(true)
                .build();
        Twitter.initialize(config);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Update location every second
        mLocationRequest.setInterval(1000);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            allowFuseLocation();
    }

    public void allowFuseLocation() {
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
        catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i("GoogleApiClient", "GoogleApiClient connection has been suspend");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("onConnectionFailed", connectionResult.getErrorMessage());
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if(mGoogleApiClient != null && mGoogleApiClient.isConnected())
            mGoogleApiClient.disconnect();

        q.closeDatabase();
    }

    @Override
    public void onLocationChanged(Location loc) {

        if(Config.SHOW_LOCATION_COORDINATES_LOG || (currentLocation == null && loc != null) )
            Log.e("Location LOG", "Location Updated [" + loc.getLatitude() + "," + loc.getLongitude() + "]");

        if(mCallbackLocation != null)
            mCallbackLocation.onLocationChanged(currentLocation, loc);

        currentLocation = loc;
        if(Config.DEBUG_LOCATION) {
            currentLocation.setLatitude(Config.DEBUG_LATITUDE);
            currentLocation.setLongitude(Config.DEBUG_LONGITUDE);
        }
        if(address == null) {
            getAddressTask = new GetAddressTask(this);
            getAddressTask.execute(currentLocation);
        }
    }

    protected class GetAddressTask extends AsyncTask<Location, Void, String> {
        // Store the context passed to the AsyncTask when the system instantiates it.
        Context localContext;
        // Constructor called by the system to instantiate the task
        public GetAddressTask(Context context) {
            // Required by the semantics of AsyncTask
            super();
            // Set a Context for the background task
            localContext = context;
        }

        @Override
        protected String doInBackground(Location... params) {
            Geocoder geocoder = new Geocoder(localContext, Locale.getDefault());
            // Get the current location from the input parameter list
            Location location = params[0];
            // Create a list to contain the result address
            List<Address> addresses = null;
            // Try to get an address for the current location. Catch IO or network problems.
            try {
                addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                // Catch network or other I/O problems.
            } catch (IOException exception1) {
                // print the stack trace
                exception1.printStackTrace();
                // Catch incorrect latitude or longitude values
            } catch (IllegalArgumentException exception2) {
                exception2.printStackTrace();
            }
            // If the reverse geocode returned an address
            if (addresses != null && addresses.size() > 0) {
                // Get the first address
                address = addresses;
                Address _address = address.get(0);
                String locality = _address.getLocality();
                String countryName = _address.getCountryName();
                String addressStr = String.format("%s, %s", locality, countryName);
                Log.e("Location LOG", addressStr);
            }
            return null;
        }

        @Override
        protected void onPostExecute(String address) { }
    }

    public void getDebugKey() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    getApplicationContext().getPackageName(),
                    PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                Log.e("KeyHash:", "------------------------------------------");
                Log.e("KeyHash:", Base64.encodeToString(md.digest(), Base64.DEFAULT));
                Log.e("KeyHash:", "------------------------------------------");
            }

        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    // ====================================================================================
    // ====================================================================================
    // ====================================================================================
    public interface OnLocationListener {
        public void onLocationChanged(Location prevLoc, Location currentLoc);
        public void onLocationRequestDenied();
    }

    public void setOnLocationListener(OnLocationListener listener, Activity activity) {
        try {
            mCallbackLocation = (OnLocationListener) listener;
            mActivity = activity;
            checkLocationIsInit();
        } catch (ClassCastException e)  {
            throw new ClassCastException(this.toString() + " must implement OnLocationListener");
        }
    }

    private void checkLocationIsInit() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(manager == null && mActivity != null) {
                manager = new MGLocationManager(mActivity);
                manager.setOnLocationListenerM(new MGLocationManager.OnLocationListenerM() {
                    @Override
                    public void onLocationChanged(Location currentLoc) {
                        if(mCallbackLocation != null) {
                            updateLocation(currentLoc);
                        }
                    }

                    @Override
                    public void onLocationRequestDenied() {
                        if(mCallbackLocation != null)
                            mCallbackLocation.onLocationRequestDenied();
                    }

                    @Override
                    public void onLocationGranted() {
                        allowFuseLocation();
                    }
                });
            }
            else {
                manager.removeLocationUpdates();
            }

            Location location = manager.checkLocationPermission();
            if(location != null)
                updateLocation(location);
        }
    }

    public MGLocationManager getMGLocationManager() {
        return manager;
    }

    private void updateLocation(Location loc) {
        if(Config.SHOW_LOCATION_COORDINATES_LOG || (currentLocation == null && loc != null) )
            Log.e("Location LOG", "Location Updated [" + loc.getLatitude() + "," + loc.getLongitude() + "]");

        currentLocation = loc;
        if(Config.DEBUG_LOCATION) {
            currentLocation.setLatitude(Config.DEBUG_LATITUDE);
            currentLocation.setLongitude(Config.DEBUG_LONGITUDE);
        }

        if(mCallbackLocation != null)
            mCallbackLocation.onLocationChanged(currentLocation, loc);

        if(address == null) {
            getAddressTask = new GetAddressTask(this);
            getAddressTask.execute(currentLocation);
        }
    }
}
