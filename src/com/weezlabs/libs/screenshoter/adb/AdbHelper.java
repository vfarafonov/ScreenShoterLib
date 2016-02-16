package com.weezlabs.libs.screenshoter.adb;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Creates adb bridge when instantiated. Adb path picked up based on ANDROID_HOME environment variable
 * <p/>
 * Created by vfarafonov on 09.02.2016.
 */
public class AdbHelper {
	private static final String ADB_PATH_RELATIVE_TO_SDK_ROOT = "/platform-tools/adb";
	private static volatile AdbHelper adbHelper_;
	private AndroidDebugBridge adb_;

	private AdbHelper() {
		AndroidDebugBridge.init(false);
		adb_ = AndroidDebugBridge.createBridge(getAdbLocation(), true);
	}

	public static AdbHelper getInstance() {
		if (adbHelper_ == null) {
			synchronized (AdbHelper.class) {
				if (adbHelper_ == null) {
					adbHelper_ = new AdbHelper();
				}
			}
		}
		return adbHelper_;
	}

	private static String getAdbLocation() {
		String android_home = System.getenv("ANDROID_HOME");
		if (android_home != null) {
			String sdkPath = android_home + ADB_PATH_RELATIVE_TO_SDK_ROOT;
			if (checkForAdbInPath(sdkPath)) {
				return sdkPath;
			}
		}
		return null;
	}

	private static boolean checkForAdbInPath(String adbPath) {
		File adbDir = new File(adbPath).getParentFile();
		return adbDir.exists() &&
				adbDir.isDirectory() &&
				adbDir.list(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.indexOf("adb") == 0;
					}
				}).length > 0;
	}

	public IDevice[] getDevices() {
		return adb_.getDevices();
	}

	public boolean isConnected() {
		return adb_.isConnected();
	}

	public void restartAdb() {
		adb_.restart();
	}

	public void addDeviceListener(AndroidDebugBridge.IDeviceChangeListener listener) {
		AndroidDebugBridge.addDeviceChangeListener(listener);
	}
}
