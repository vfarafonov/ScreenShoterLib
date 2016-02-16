package com.weezlabs.libs.screenshoter;

import com.android.ddmlib.IDevice;
import com.weezlabs.libs.screenshoter.model.Device;
import com.weezlabs.libs.screenshoter.model.Mode;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vfarafonov on 09.02.2016.
 */
public class ScreenShooter {
	public static void main(String[] args) {

		final ScreenShooterManager screenShooterManager = ScreenShooterManager.getInstance();
		IDevice[] devices = screenShooterManager.getDevices();
		if (devices.length == 0) {
			System.out.println("No device connected");
			return;
		}
		ScreenShooterManager.getDeviceDisplayInfo(devices[0], new ScreenShooterManager.DeviceInfoListener() {
			@Override
			public void onDeviceInfoUpdated(Device device) {
				System.out.println("Success. Density: " + device.getPhysicalDpi().getDpiValue() + " Resolution: " + device.getPhysicalResolution());
				screenShooterManager.setDevice(device);
				List<Mode> excludedList = new ArrayList<Mode>();
				excludedList.add(new Mode(Device.Resolution.NORMAL_PLUS_1, Device.Dpi.HDPI));
				excludedList.add(new Mode(Device.Resolution.NORMAL_PLUS_1, Device.Dpi.MDPI));
				excludedList.add(new Mode(Device.Resolution.XXLARGE_1, Device.Dpi.HDPI));
				excludedList.add(new Mode(Device.Resolution.XXLARGE_1, Device.Dpi.MDPI));
				screenShooterManager.createScreenshotsForAllResolutions(null,
						null,
						null,
						excludedList,
						new ScreenShooterManager.ScreenShotJobProgressListener() {
							@Override
							public void onScreenshotJobFinished() {
								System.out.println("IT WORKS!!!!");
								screenShooterManager.resetDeviceDisplay(new ScreenShooterManager.CommandStatusListener() {
									@Override
									public void onCommandSentToDevice() {
										System.out.println("Display params were reset");
									}

									@Override
									public void onCommandExecutionFailed() {
										System.out.println("Display params reset failed");
									}
								});
							}

							@Override
							public void onScreenshotJobFailed() {
								System.out.println("Screenshot job failed");
							}

							@Override
							public void onScreenshotJobCancelled() {

							}
						}
				);
			}

			@Override
			public void onDeviceUpdateFailed(IDevice iDevice, Exception e) {
				System.out.println("Device update failed: " + e.getMessage());
			}
		});
	}
}
