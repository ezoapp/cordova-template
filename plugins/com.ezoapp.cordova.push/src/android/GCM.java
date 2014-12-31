package com.ezoapp.cordova.gcm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.http.AndroidHttpClient;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

public class GCM extends CordovaPlugin {
	public static final String TAG = "GCM";

	private String regid;
	private String serverUrl;
	private String senderId;
	private String id;

	private GoogleCloudMessaging gcm;
	private Activity activity;
	private Context context;
	private CallbackContext callbackContext;

	private JSONObject message;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);

		this.activity = cordova.getActivity();
		this.context = activity.getApplicationContext();
	}

	@Override
	public boolean execute(String action, CordovaArgs args,
			CallbackContext callbackContext) throws JSONException {

		this.callbackContext = callbackContext;

		if ("addListener".equals(action)) {
			message = getMessageFromIntent();
			if (message != null) {
				callbackContext.success(message);
			}
		} else if ("register".equals(action)) {
			// Check device for Play Services APK. If check succeeds, proceed
			// with GCM registration.
			if (checkPlayServices()) {
				gcm = GoogleCloudMessaging.getInstance(activity);
				regid = getRegistrationId(context);
				serverUrl = args.getString(0);
				senderId = args.getString(1).equals("") ? Constants.SENDER_ID
						: args.getString(1);
				id = args.getString(2);

				if (regid.isEmpty()) {
					registerInBackground();
				} else {
					callbackContext.success(regid);
				}
			} else {
				Log.i(TAG, "No valid Google Play Services APK found.");
			}
		} else if ("unregister".equals(action)) {
			regid = getRegistrationId(context);
			serverUrl = args.getString(0);
			id = args.getString(1);

			if (!regid.isEmpty()) {
				unregisterInBackground();
			} else {
				callbackContext.success();
			}
		} else {
			return false;
		}
		return true;
	}

	@Override
	public void onResume(boolean multitasking) {
		super.onResume(multitasking);
		// Check device for Play Services APK.
		checkPlayServices();
		message = getMessageFromIntent();
		if (message != null) {
			webView.loadUrl("javascript:gk.push._callback(" + message + ")");
		}
	}

	@Override
	public void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		activity.setIntent(intent);
	}

	private JSONObject getMessageFromIntent() {
		JSONObject message = null;
		Bundle bundle = activity.getIntent().getExtras();
		if (bundle != null && bundle.containsKey(Constants.EXTRA_GCM_DATA)) {
			message = new JSONObject();
			Bundle data = bundle.getBundle(Constants.EXTRA_GCM_DATA);
			for (String key : data.keySet()) {
				try {
					message.put(key, data.get(key));
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
		return message;
	}

	/**
	 * Check the device to make sure it has the Google Play Services APK. If it
	 * doesn't, display a dialog that allows users to download the APK from the
	 * Google Play Store or enable it in the device's system settings.
	 */
	private boolean checkPlayServices() {
		int resultCode = GooglePlayServicesUtil
				.isGooglePlayServicesAvailable(activity);
		if (resultCode != ConnectionResult.SUCCESS) {
			if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
				GooglePlayServicesUtil.getErrorDialog(resultCode, activity,
						Constants.PLAY_SERVICES_RESOLUTION_REQUEST).show();
			} else {
				Log.i(TAG, "This device is not supported.");
				activity.finish();
			}
			return false;
		}
		return true;
	}

	/**
	 * Gets the current registration ID for application on GCM service, if there
	 * is one.
	 * <p>
	 * If result is empty, the app needs to register.
	 *
	 * @return registration ID, or empty string if there is no existing
	 *         registration ID.
	 */
	private String getRegistrationId(Context context) {
		final SharedPreferences prefs = getGcmPreferences(context);
		String registrationId = prefs.getString(Constants.PROPERTY_REG_ID, "");
		if (registrationId.isEmpty()) {
			Log.i(TAG, "Registration not found.");
			return "";
		}
		// Check if app was updated; if so, it must clear the registration ID
		// since the existing regID is not guaranteed to work with the new
		// app version.
		int registeredVersion = prefs.getInt(Constants.PROPERTY_APP_VERSION,
				Integer.MIN_VALUE);
		int currentVersion = getAppVersion(context);
		if (registeredVersion != currentVersion) {
			Log.i(TAG, "App version changed.");
			return "";
		}
		return registrationId;
	}

	/**
	 * @return Application's {@code SharedPreferences}.
	 */
	private SharedPreferences getGcmPreferences(Context context) {
		// This sample app persists the registration ID in shared preferences,
		// but how you store the regID in your app is up to you.
		return activity.getSharedPreferences(GCM.class.getSimpleName(),
				Context.MODE_PRIVATE);
	}

	/**
	 * @return Application's version code from the {@code PackageManager}.
	 */
	private static int getAppVersion(Context context) {
		try {
			PackageInfo packageInfo = context.getPackageManager()
					.getPackageInfo(context.getPackageName(), 0);
			return packageInfo.versionCode;
		} catch (NameNotFoundException e) {
			// should never happen
			throw new RuntimeException("Could not get package name: " + e);
		}
	}

	/**
	 * Registers the application with GCM servers asynchronously.
	 * <p>
	 * Stores the registration ID and the app versionCode in the application's
	 * shared preferences.
	 */
	private void registerInBackground() {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				try {
					if (gcm == null) {
						gcm = GoogleCloudMessaging.getInstance(context);
					}
					regid = gcm.register(senderId);
					msg = "Device registered, registration ID=" + regid;

					// You should send the registration ID to your server over
					// HTTP, so it can use GCM/HTTP or CCS to send messages to
					// your app.
					sendRegistrationIdToBackend();

					storeRegistrationId(context, regid);
					callbackContext.success(regid);
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					// If there is an error, don't just keep trying to register.
					// Require the user to click a button again, or perform
					// exponential back-off.
					callbackContext.error(msg);
				}
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				Log.i(TAG, msg);
			}
		}.execute(null, null, null);
	}

	/**
	 * Sends the registration ID to your server over HTTP, so it can use
	 * GCM/HTTP or CCS to send messages to your app. Not needed for this demo
	 * since the device sends upstream messages to a server that echoes back the
	 * message using the 'from' address in the message.
	 * 
	 * @throws IOException
	 */
	private void sendRegistrationIdToBackend() throws IOException {
		int statusCode = executeBackendProcess();
		if (statusCode != HttpStatus.SC_OK) {
			throw new IOException(
					"Send registration id to backend fail, status code: "
							+ statusCode);
		}
	}

	private int executeBackendProcess() throws IOException {
		AndroidHttpClient client = AndroidHttpClient.newInstance("");
		HttpConnectionParams.setConnectionTimeout(client.getParams(),
				Constants.CONNECT_TIMEOUT);
		HttpConnectionParams.setSoTimeout(client.getParams(),
				Constants.CONNECT_TIMEOUT);

		HttpPost post = new HttpPost(serverUrl);
		List<NameValuePair> pairs = new ArrayList<NameValuePair>();
		pairs.add(new BasicNameValuePair("id", id));
		pairs.add(new BasicNameValuePair("regId", regid));
		post.setEntity(new UrlEncodedFormEntity(pairs));

		try {
			HttpResponse res = client.execute(post);
			int statusCode = res.getStatusLine().getStatusCode();
			Log.i(TAG, "Response status code: " + statusCode);
			return statusCode;
		} finally {
			client.close();
		}
	}

	/**
	 * Stores the registration ID and the app versionCode in the application's
	 * {@code SharedPreferences}.
	 *
	 * @param context
	 *            application's context.
	 * @param regId
	 *            registration ID
	 */
	private void storeRegistrationId(Context context, String regId) {
		final SharedPreferences prefs = getGcmPreferences(context);
		int appVersion = getAppVersion(context);
		Log.i(TAG, "Saving regId on app version " + appVersion);
		SharedPreferences.Editor editor = prefs.edit();
		editor.putString(Constants.PROPERTY_REG_ID, regId);
		editor.putInt(Constants.PROPERTY_APP_VERSION, appVersion);
		editor.commit();
	}

	private void unregisterInBackground() {
		new AsyncTask<Void, Void, String>() {
			@Override
			protected String doInBackground(Void... params) {
				String msg = "";
				try {
					msg = "Device unregistered";

					sendUnregistrationIdToBackend();
					removeRegistrationId(context);
					callbackContext.success();
				} catch (IOException ex) {
					msg = "Error :" + ex.getMessage();
					// If there is an error, don't just keep trying to register.
					// Require the user to click a button again, or perform
					// exponential back-off.
					callbackContext.error(msg);
				}
				return msg;
			}

			@Override
			protected void onPostExecute(String msg) {
				Log.i(TAG, msg);
			}
		}.execute(null, null, null);
	}

	private void sendUnregistrationIdToBackend() throws IOException {
		int statusCode = executeBackendProcess();
		if (statusCode != HttpStatus.SC_OK) {
			throw new IOException(
					"Send unregistration id to backend fail, status code: "
							+ statusCode);
		}
	}

	private void removeRegistrationId(Context context) {
		final SharedPreferences prefs = getGcmPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		editor.clear();
		editor.commit();
	}
}
