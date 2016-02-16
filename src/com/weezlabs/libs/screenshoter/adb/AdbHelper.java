package com.weezlabs.libs.screenshoter.adb;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.weezlabs.libs.screenshoter.ScreenShooterManager;

/**
 * Creates adb bridge when instantiated. Adb path picked up based on ANDROID_HOME environment variable
 * <p/>
 * Created by vfarafonov on 09.02.2016.
 */
public class AdbHelper {
	private static volatile AdbHelper adbHelper_;
	private AndroidDebugBridge adb_;

	private AdbHelper() {}

	private AdbHelper(String adbPath) {
		AndroidDebugBridge.init(false);
		if (!ScreenShooterManager.checkForAdbInPath(adbPath)){
			throw new IllegalArgumentException("Adb not found. Check path with getSystemAdbLocation");
		}
		adb_ = AndroidDebugBridge.createBridge(adbPath, true);
	}

	public static AdbHelper getInstance(String adbPath) {
		if (adbHelper_ == null) {
			synchronized (AdbHelper.class) {
				if (adbHelper_ == null) {
					adbHelper_ = new AdbHelper(adbPath);
				}
			}
		}
		return adbHelper_;
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
