package com.canonical.democlient;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.ArrayAdapter;

import org.iotivity.base.ErrorCode;
import org.iotivity.base.OcConnectivityType;
import org.iotivity.base.OcException;
import org.iotivity.base.OcHeaderOption;
import org.iotivity.base.OcPlatform;
import org.iotivity.base.OcRepresentation;
import org.iotivity.base.OcResource;
import org.iotivity.base.OcResourceIdentifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by gerald on 2015/11/10.
 */
public class SensorResourceA implements
        OcPlatform.OnResourceFoundListener,
        OcResource.OnGetListener,
        OcResource.OnPutListener,
        OcResource.OnPostListener,
        OcResource.OnObserveListener {

    private Activity main_activity;
    private Context main_context;
    private OcPlatform.OnResourceFoundListener resource_found_listener;
    private ArrayList<String> main_list_item;
    private ArrayAdapter<String> main_list_adapter;
    private Map<OcResourceIdentifier, OcResource> mFoundResources = new HashMap<>();
    private OcResource mResource = null;
    private Thread find_thread = null;
    private boolean find_thread_running;
    private Thread update_thread = null;
    private boolean update_thread_running;
    private boolean sensor_thread_read_done;
    private double mTemp;
    private int mLight;
    private int mSound;
    private int mTempListIndex;
    private int mLightListIndex;
    private int mSoundListIndex;
    private final static String TAG = "Arduino Sensor";

    private final static String resource_type = "grove.sensor";
    private final static String resource_uri = "/grove/sensor";

    private static final String SENSOR_TEMPERATURE_KEY = "temperature";
    private static final String SENSOR_LIGHT_KEY = "light";
    private static final String SENSOR_SOUND_KEY = "sound";
    private final static String msg_read = "sensor_read_a";

    public final static String sensor_temp_display = "(Arduino) Temperature sensor: ";
    public final static String sensor_light_display = "(Arduino) Light sensor: ";
    public final static String sensor_sound_display = "(Arduino) Sound sensor: ";
    public final static String msg_found = "msg_found_resource";

    public SensorResourceA(Activity main, Context c, ArrayList<String> list_item,
                           ArrayAdapter<String> list_adapter) {
        main_activity = main;
        main_context = c;
        resource_found_listener = this;
        find_thread_running = true;
        update_thread_running = true;
        sensor_thread_read_done = true;

        main_list_item = list_item;
        main_list_adapter = list_adapter;

        mTemp = 0.0;
        mLight = 0;
        mSound = 0;

        mTempListIndex = -1;
        mLightListIndex = -1;
        mSoundListIndex = -1;

        LocalBroadcastManager.getInstance(main_activity).registerReceiver(mSensorReadReceiver,
                new IntentFilter(msg_read));
    }

    public void setOcRepresentation(OcRepresentation rep) throws OcException {
        mTemp = rep.getValue(SENSOR_TEMPERATURE_KEY);
        mLight = rep.getValue(SENSOR_LIGHT_KEY);
        mSound = rep.getValue(SENSOR_SOUND_KEY);
    }

    public OcRepresentation getOcRepresentation() throws OcException {
        OcRepresentation rep = new OcRepresentation();
        rep.setValue(SENSOR_TEMPERATURE_KEY, mTemp);
        rep.setValue(SENSOR_LIGHT_KEY, mLight);
        rep.setValue(SENSOR_SOUND_KEY, mSound);
        return rep;
    }

    public void setTempIndex(int index) {
        mTempListIndex = index;
    }

    public void setLightIndex(int index) {
        mLightListIndex = index;
    }

    public void setSoundIndex(int index) {
        mSoundListIndex = index;
    }

    public int getTempIndex() {
        return mTempListIndex;
    }

    public int getLightIndex() {
        return mLightListIndex;
    }

    public int getSoundIndex() {
        return mSoundListIndex;
    }

    public void find_resource() {
        find_thread = new Thread(new Runnable() {
            public void run() {
                String requestUri;

                Log.e(TAG, "Finding resources of type: " + resource_type);
                requestUri = OcPlatform.WELL_KNOWN_QUERY + "?rt=" + resource_type;

                while(mResource == null && !Thread.interrupted() && find_thread_running) {
                    try {
                        OcPlatform.findResource("",
                                requestUri,
                                EnumSet.of(OcConnectivityType.CT_DEFAULT),
                                resource_found_listener
                        );
                    } catch (OcException e) {
                        Log.e(TAG, e.toString());
                        Log.e(TAG, "Failed to invoke find resource API");
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        Log.e(TAG, "InterruptedException");
                        return;
                    }
                }
            }
        });

        find_thread.start();
    }

    public void getResourceRepresentation() {
        Log.e(TAG, "Getting Sensor Representation...");

        Map<String, String> queryParams = new HashMap<>();
        try {
            // Invoke resource's "get" API with a OcResource.OnGetListener event
            // listener implementation
            mResource.get(queryParams, this);
        } catch (OcException e) {
            Log.e(TAG, e.toString());
            Log.e(TAG, "Error occurred while invoking \"get\" API");
        }
    }

    public void start_update_thread() {
        update_thread = new Thread(new Runnable(){
            @Override
            public void run() {
                update_thread();
            }
        });
        update_thread.start();
    }

    public void stop_find_thread() {
        if(find_thread != null) {
            find_thread_running = false;
            find_thread.interrupt();
        }
    }

    public void stop_update_thread() {
        if(update_thread != null) {
            update_thread_running = false;
            update_thread.interrupt();
        }
    }

    private void update_thread() {
        Log.e(TAG, "Start update thread");
        while(update_thread_running) {
            Log.e(TAG, "Start update sensors: " + String.valueOf(sensor_thread_read_done));
            if(sensor_thread_read_done) {
                sensor_thread_read_done = false;
                getResourceRepresentation();
                try {
                    update_thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.toString());
                }
            }

            try {
                update_thread.sleep(1500);
            } catch (InterruptedException e) {
                e.printStackTrace();
                Log.e(TAG, e.toString());
            }
        }
    }

    public void reset() {
        stop_find_thread();
        stop_update_thread();
        mFoundResources.clear();
    }

    private BroadcastReceiver mSensorReadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            sensor_thread_read_done = intent.getBooleanExtra("read_done", false);
        }
    };

    private void update_list() {
        main_activity.runOnUiThread(new Runnable() {
            public synchronized void run() {
                main_list_item.set(mTempListIndex, sensor_temp_display + String.valueOf(mTemp));
                main_list_item.set(mLightListIndex, sensor_light_display + String.valueOf(mLight));
                main_list_item.set(mSoundListIndex, sensor_sound_display + String.valueOf(mSound));
                main_list_adapter.notifyDataSetChanged();

                Log.e(TAG, "Arduino Sensors:");
                Log.e(TAG, String.valueOf(mTemp));
                Log.e(TAG, String.valueOf(mLight));
                Log.e(TAG, String.valueOf(mSound));
            }
        });
    }

    private void sendBroadcastMessage(String type, String key, boolean b) {
        Intent intent = new Intent(type);

        intent.putExtra(key, b);
        Log.e(TAG, "Send sensor message");
        LocalBroadcastManager.getInstance(main_context).sendBroadcast(intent);
    }

    @Override
    public synchronized void onResourceFound(OcResource ocResource) {
        if (null == ocResource) {
            Log.e(TAG, "Found resource is invalid");
            return;
        }

        if (mFoundResources.containsKey(ocResource.getUniqueIdentifier())) {
            Log.e(TAG, "Found a previously seen resource again!");
        } else {
            Log.e(TAG, "Found resource for the first time on server with ID: " + ocResource.getServerId());
            mFoundResources.put(ocResource.getUniqueIdentifier(), ocResource);
        }

        // Get the resource URI
        String resourceUri = ocResource.getUri();
        // Get the resource host address
        String hostAddress = ocResource.getHost();
        Log.e(TAG, "\tURI of the resource: " + resourceUri);
        Log.e(TAG, "\tHost address of the resource: " + hostAddress);
        // Get the resource types
        Log.e(TAG, "\tList of resource types: ");
        for (String resourceType : ocResource.getResourceTypes()) {
            Log.e(TAG, "\t\t" + resourceType);
        }
        Log.e(TAG, "\tList of resource interfaces:");
        for (String resourceInterface : ocResource.getResourceInterfaces()) {
            Log.e(TAG, "\t\t" + resourceInterface);
        }
        Log.e(TAG, "\tList of resource connectivity types:");
        for (OcConnectivityType connectivityType : ocResource.getConnectivityTypeSet()) {
            Log.e(TAG, "\t\t" + connectivityType);
        }

        if (resourceUri.equals(resource_uri)) {
            if (mResource != null) {
                Log.e(TAG, "Found another Arduino sensor resource, ignoring");
                return;
            }

            //Assign resource reference to a global variable to keep it from being
            //destroyed by the GC when it is out of scope.
            mResource = ocResource;

            sendBroadcastMessage(msg_found, "sensor_found_resource_a", true);
        }
    }
    
    @Override
    public synchronized void onGetCompleted(List<OcHeaderOption> list,
                                            OcRepresentation ocRepresentation) {
        Log.e(TAG, "GET request was successful");
        Log.e(TAG, "Resource URI: " + ocRepresentation.getUri());

        try {
            setOcRepresentation(ocRepresentation);
            update_list();
            sendBroadcastMessage(msg_read, "read_done", true);
        } catch (OcException e) {
            Log.e(TAG, e.toString());
            Log.e(TAG, "Failed to set sensor representation");
        }
    }

    @Override
    public synchronized void onGetFailed(Throwable throwable) {
        if (throwable instanceof OcException) {
            OcException ocEx = (OcException) throwable;
            Log.e(TAG, ocEx.toString());
            ErrorCode errCode = ocEx.getErrorCode();
            //do something based on errorCode
            Log.e(TAG, "Error code: " + errCode);
        }
        
        Log.e(TAG, "Failed to get representation of a found sensor resource");
    }

    @Override
    public synchronized void onPutCompleted(List<OcHeaderOption> list, OcRepresentation ocRepresentation) {
        Log.e(TAG, "PUT request was successful");
    }

    @Override
    public synchronized void onPutFailed(Throwable throwable) {
        if (throwable instanceof OcException) {
            OcException ocEx = (OcException) throwable;
            Log.e(TAG, ocEx.toString());
            ErrorCode errCode = ocEx.getErrorCode();
            //do something based on errorCode
            Log.e(TAG, "Error code: " + errCode);
        }
        Log.e(TAG, "Failed to \"put\" a new representation");
    }

    @Override
    public synchronized void onPostCompleted(List<OcHeaderOption> list,
                                             OcRepresentation ocRepresentation) {
        Log.e(TAG, "POST request was successful");
        try {
            if (ocRepresentation.hasAttribute(OcResource.CREATED_URI_KEY)) {
                Log.e(TAG, "\tUri of the created resource: " +
                        ocRepresentation.getValue(OcResource.CREATED_URI_KEY));
            } else {
                setOcRepresentation(ocRepresentation);
            }
        } catch (OcException e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public synchronized void onPostFailed(Throwable throwable) {
        if (throwable instanceof OcException) {
            OcException ocEx = (OcException) throwable;
            Log.e(TAG, ocEx.toString());
            ErrorCode errCode = ocEx.getErrorCode();
            //do something based on errorCode
            Log.e(TAG, "Error code: " + errCode);
        }
        Log.e(TAG, "Failed to \"post\" a new representation");
    }

    @Override
    public synchronized void onObserveCompleted(List<OcHeaderOption> list,
                                                OcRepresentation ocRepresentation,
                                                int sequenceNumber) {
        if (OcResource.OnObserveListener.REGISTER == sequenceNumber) {
            Log.e(TAG, "Arduino sensor observe registration action is successful:");
        } else if (OcResource.OnObserveListener.DEREGISTER == sequenceNumber) {
            Log.e(TAG, "Arduino sensor observe De-registration action is successful");
        } else if (OcResource.OnObserveListener.NO_OPTION == sequenceNumber) {
            Log.e(TAG, "Arduino sensor observe registration or de-registration action is failed");
        }

        Log.e(TAG, "OBSERVE Result:");
        Log.e(TAG, "\tSequenceNumber:" + sequenceNumber);
        //update_list();
    }

    @Override
    public synchronized void onObserveFailed(Throwable throwable) {
        if (throwable instanceof OcException) {
            OcException ocEx = (OcException) throwable;
            Log.e(TAG, ocEx.toString());
            ErrorCode errCode = ocEx.getErrorCode();
            //do something based on errorCode
            Log.e(TAG, "Error code: " + errCode);
        }
        Log.e(TAG, "Observation of the found sensor resource has failed");
    }
}
