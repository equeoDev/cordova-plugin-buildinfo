/*
The MIT License (MIT)

Copyright (c) 2016 Mikihiro Hayashi

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

package org.apache.cordova.buildinfo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

/**
 * BuildInfo Cordova Plugin
 *
 * @author Mikihiro Hayashi
 * @since 1.0.0
 */
public class BuildInfo extends CordovaPlugin {
	private static final String TAG = "BuildInfo";

	/**
	 * Cache of result JSON
	 */
	private static JSONObject mBuildInfoCache;

	/**
	 * Constructor
	 */
	public BuildInfo() {
	}

	/**
	 * execute
	 * @param action          The action to execute.
	 * @param args            The exec() arguments.
	 * @param callbackContext The callback context used when calling back into JavaScript.
	 * @return
	 * @throws JSONException
     */
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

		if ("init".equals(action)) {
			String buildConfigClassName = null;
			if (1 < args.length()) {
				buildConfigClassName = args.getString(0);
			}

			init(buildConfigClassName, callbackContext);
			return true;
		}

		return false;
	}

	/**
	 * init
	 * @param buildConfigClassName null or specified BuildConfig class name
	 * @param callbackContext
	 */
	private void init(String buildConfigClassName, CallbackContext callbackContext) {
		// Cached check
		if (null != mBuildInfoCache) {
			callbackContext.success(mBuildInfoCache);
			return;
		}

		// Load PackageInfo
		Activity activity = cordova.getActivity();
		String packageName = activity.getPackageName();
		String basePackageName = packageName;
		CharSequence displayName = "";

		PackageManager pm = activity.getPackageManager();

		try {
			PackageInfo pi = pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);

			if (null != pi.applicationInfo) {
				displayName = pi.applicationInfo.loadLabel(pm);
			}
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		// Load BuildConfig class
		Class c = null;

		if (null == buildConfigClassName) {
			buildConfigClassName = packageName + ".BuildConfig";
		}

		try {
			c = Class.forName(buildConfigClassName);
		} catch (ClassNotFoundException e) {
		}

		if (null == c) {
			basePackageName = activity.getClass().getPackage().getName();
			buildConfigClassName = basePackageName + ".BuildConfig";

			try {
				c = Class.forName(buildConfigClassName);
			} catch (ClassNotFoundException e) {
				callbackContext.error("BuildConfig ClassNotFoundException: " + e.getMessage());
				return;
			}
		}

		// Create result
		mBuildInfoCache = new JSONObject();
		try {
			boolean debug = getClassFieldBoolean(c, "DEBUG", false);

			mBuildInfoCache.put("packageName"    , packageName);
			mBuildInfoCache.put("basePackageName", basePackageName);
			mBuildInfoCache.put("displayName"    , displayName);
			mBuildInfoCache.put("name"           , displayName); // same as displayName
			mBuildInfoCache.put("version"        , getClassFieldString(c, "VERSION_NAME", ""));
			mBuildInfoCache.put("versionCode"    , getClassFieldInt(c, "VERSION_CODE", 0));
			mBuildInfoCache.put("debug"          , debug);
			mBuildInfoCache.put("buildTime"      , getAppTimeStamp(activity));
			mBuildInfoCache.put("buildType"      , getClassFieldString(c, "BUILD_TYPE", ""));
			mBuildInfoCache.put("flavor"         , getClassFieldString(c, "FLAVOR", ""));

			if (debug) {
				Log.d(TAG, "packageName    : \"" + mBuildInfoCache.getString("packageName") + "\"");
				Log.d(TAG, "basePackageName: \"" + mBuildInfoCache.getString("basePackageName") + "\"");
				Log.d(TAG, "displayName    : \"" + mBuildInfoCache.getString("displayName") + "\"");
				Log.d(TAG, "name           : \"" + mBuildInfoCache.getString("name") + "\"");
				Log.d(TAG, "version        : \"" + mBuildInfoCache.getString("version") + "\"");
				Log.d(TAG, "versionCode    : " + mBuildInfoCache.getInt("versionCode"));
				Log.d(TAG, "debug          : " + (mBuildInfoCache.getBoolean("debug") ? "true" : "false"));
				Log.d(TAG, "buildTime      : \"" + mBuildInfoCache.getString("buildTime") + "\"");
				Log.d(TAG, "buildType      : \"" + mBuildInfoCache.getString("buildType") + "\"");
				Log.d(TAG, "flavor         : \"" + mBuildInfoCache.getString("flavor") + "\"");
			}
		} catch (JSONException e) {
			e.printStackTrace();
			callbackContext.error("JSONException: " + e.getMessage());
			return;
		}

		callbackContext.success(mBuildInfoCache);
	}

	/**
	 * Get boolean of field from Class
	 * @param c
	 * @param fieldName
	 * @param defaultReturn
     * @return
     */
	private static boolean getClassFieldBoolean(Class c, String fieldName, boolean defaultReturn) {
		boolean ret = defaultReturn;
		Field field = getClassField(c, fieldName);

		if (null != field) {
			try {
				ret = field.getBoolean(c);
			} catch (IllegalAccessException iae) {
				iae.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Get string of field from Class
	 * @param c
	 * @param fieldName
	 * @param defaultReturn
     * @return
     */
	private static String getClassFieldString(Class c, String fieldName, String defaultReturn) {
		String ret = defaultReturn;
		Field field = getClassField(c, fieldName);

		if (null != field) {
			try {
				ret = (String)field.get(c);
			} catch (IllegalAccessException iae) {
				iae.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Get int of field from Class
	 * @param c
	 * @param fieldName
	 * @param defaultReturn
     * @return
     */
	private static int getClassFieldInt(Class c, String fieldName, int defaultReturn) {
		int ret = defaultReturn;
		Field field = getClassField(c, fieldName);

		if (null != field) {
			try {
				ret = field.getInt(c);
			} catch (IllegalAccessException iae) {
				iae.printStackTrace();
			}
		}

		return ret;
	}

	/**
	 * Get field from Class
	 * @param c
	 * @param fieldName
     * @return
     */
	private static Field getClassField(Class c, String fieldName) {
		Field field = null;

		try {
			field = c.getField(fieldName);
		} catch (NoSuchFieldException nsfe) {
			nsfe.printStackTrace();
		}

		return field;
	}

	private static String getAppTimeStamp(Context context) {
		String timeStamp = "";

		try {
			ApplicationInfo appInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
			String appFile = appInfo.sourceDir;
			long time = new File(appFile).lastModified();

			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			timeStamp = formatter.format(time);

		} catch (Exception e) {
            e.printStackTrace();
		}

		return timeStamp;

	}
}
