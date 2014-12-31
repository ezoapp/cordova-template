package com.ezoapp.cordova.gcm;

public interface Constants {

	/**
	 * Substitute you own sender ID here. This is the project number you got
	 * from the API Console, as described in "Getting Started."
	 */
	public static final String SENDER_ID = "866229432699";

	public static final String PROPERTY_REG_ID = "registration_id";

	public static final String PROPERTY_APP_VERSION = "appVersion";

	public static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

	public static final int CONNECT_TIMEOUT = 10 * 1000;

	public static final String EXTRA_GCM_DATA = "gcm_data";

	public static final String EXTRA_TITLE = "title";

	public static final String EXTRA_MESSAGE = "message";
}
