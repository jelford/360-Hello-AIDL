package com.vodafone360.thirdparty;

import java.lang.reflect.Field;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.vodafone360.people.service.aidl.IDatabaseSubscriber;
import com.vodafone360.people.service.aidl.IDatabaseSubscriptionService;

/**
 * <p>A quick and dirty demo for talking to the
 * Vodafone360 People services via AIDL.
 * 
 * <p>This is a reasonable way to drop head-first
 * into making your own processes which will
 * make use of the new IPC APIs from
 * Vodafone 360 People. The steps taken in this
 * program are pretty much the steps that any
 * program will have to take in order to make use
 * of these APIs, so it's worth having a good look
 * through.
 * 
 * <p>Things to note for all applications using these APIs:
 * <li> You need to link against clientDepends.jar
 * <li> You need to add the following line to your
 * AndroidManifest.xml file:
 * 
 * <uses-permission android:name="com.vodafone360.people.service.aidl.permission.FullAccess"></uses-permission>
 * 
 *
 */
public class HelloAidl extends Activity {
    
    private static final String TAG = "HelloAndroid";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // We won't be putting anything onto the user interface.
        setContentView(R.layout.main);
        
        // Ask to get callbacks from the database
        registerForEventCallbacks();
        
        // Demo querying the database
        queryTheDatabase();
    }

    /**
     * Demonstrate the usage of the ContentProvider - access
     * the 360 People database.
     */
    private void queryTheDatabase(){
        // This is how we communicate with the 360 People ContentProvider
        final Uri PROVIDER_URI = Uri.parse(com.vodafone360.people.service.aidl.Intents.DATABASE_URI);

        // We are interested in the Activities table from the database
        Uri activitiesUri = Uri.withAppendedPath(PROVIDER_URI, "Activities");

        // Simply query the database, as we would with any content provider
        Cursor activitiesCursor = managedQuery(activitiesUri, null, null, null, null);

        // If the database returns Null, we're not going to have any luck at all.
        if (activitiesCursor == null) {
            Log.e(TAG, "Shouldn't have got a null pointer back from the database;" +
            		" address this!");
            return;
        }

        /*
         * Now we'll simply loop through the cursor and dump its contents
         * to the log. It's crude, but it demonstrates what's available.
         */
        if (activitiesCursor.moveToFirst()) {
            do {
                StringBuilder record = new StringBuilder("[");
                for (int i=0; i < activitiesCursor.getColumnCount(); i++) {
                    record.append(activitiesCursor.getColumnName(i))
                    .append(":")
                    .append(activitiesCursor.getString(i))
                    .append(", ");
                }
                record.append("]");
                Log.i(TAG, record.toString());
            } while (activitiesCursor.moveToNext());
        }
    }

    /**
     * Demonstrates how to connect to the database so that
     * we can receive event callbacks & send commands.
     * 
     * Below this function live some member variables that
     * are required to make this work.
     */
    private void registerForEventCallbacks(){
        Intent serviceIntent = 
            com.vodafone360.people.service.aidl.Intents.SERVICE_INTENT;

        /* 
         * Start the service before we try to bind to it
         * as as this is less likely to fail without telling us.
         */
        startService(serviceIntent);

        /*
         * Ask Android to bind us to this service. mServiceConnection
         * will get called when we have successfully bound, where we
         * will be given a handle to the bits of code we can call in
         * the server. 
         */
        if (!bindService(serviceIntent, mServiceConnection, 0)){
            // This would be bad.
            Log.e(TAG, "Failed to bind to the IPC Service!");
        }
    }

    /* 
     * This will be our handle into the engine code. Anything
     * we want to do, we'll do it with this.
     */
    private IDatabaseSubscriptionService mPeopleService;

    /*
     * We use this to uniquely identify ourselves to the
     * 360 People client. If you use it again, you'll
     * override the first entry.
     */
    private final String SUBSCRIPTION_IDENTIFIER = 
        "com.vodafone360.thirdparty.HelloAidl";

    /*
     * This is the "client-side" stuff: it's the handle the 360 People Client
     * gets on our code. The People Client directly calls code in here to let
     * us know what's going on.
     */
    IDatabaseSubscriber mSubscriber = new IDatabaseSubscriber.Stub() {

        @Override
        public void handleEvent(Message msg) throws RemoteException {
            Log.i(TAG, "The 360 People Client is telling us about an event!");
        }

        @Override
        public void onServiceReady() throws RemoteException {
            Log.i(TAG, "The 360 People Client is ready to " +
            		"receive our incoming calls");
        }

    };


    /**
     * When you bind to a remote service on the Android platform, you have to
     * give it an object which extends the ServiceConnection class to act as
     * a "manager" to your connection. You'll receive notification when the
     * connection is established (onServiceConnected), and when the connection
     * is shut down (onServiceDisconnected)
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            /* 
             * "service" is the IBinder returned by the IPC code within the
             * engine. We turn this into an IDatabaseSubscriptionService
             * which gives us a handle into the engine
             */
            mPeopleService = IDatabaseSubscriptionService.Stub.asInterface(service);

            /*
             * All calls to the mPeopleService have to be wrapped in
             * try/catch(RemoteException) blocks.
             */
            try{
                if (mPeopleService.subscribe(SUBSCRIPTION_IDENTIFIER, mSubscriber)) {
                    /*
                     *  If the call to subscribe returns true, then the service is
                     *  ready for us to make calls.
                     */
                    mSubscriber.onServiceReady();
                } else {
                    /*
                     *  If the call to subscribe returns false, then the service is
                     *  not yet ready to receive calls. It will call 
                     *  mSubscriber.onServiceReady() when it is. 
                     */
                    Log.i(TAG, "The 360 People Client isn't yet ready for us " +
                    		"to make calls yet - we'll get a callback when it is.");
                }
            } catch (RemoteException e){
                Log.e(TAG, "Got a RemoteException trying to subscribe" +
                		" to the service; uh oh!");
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            unbindService(this);
            mPeopleService = null;
        }

    };
}