package com.weezlabs.libs.screenshoter;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.sun.javafx.beans.annotations.NonNull;
import com.weezlabs.libs.screenshoter.Model.Device;
import com.weezlabs.libs.screenshoter.adb.AdbHelper;
import com.weezlabs.libs.screenshoter.adb.DeviceShellHelper;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Manager to interact with Adb. <br>
 * Typical flow:<br>
 * 1. Get IDevices list<br>
 * 2. Request parameters for selected IDevice and set created Device object to manager's instance<br>
 * 3. Start screenshots job with {@link ScreenShooterManager#createScreenshotsForAllResolutions(ScreenShotJobProgressListener)}<br>
 * 4. Reset display parameters with {@link ScreenShooterManager#resetDeviceDisplay(CommandStatusListener)}<br>
 * <p/>
 * Created by vfarafonov on 12.02.2016.
 */
public class ScreenShooterManager {
	private static final String TEXT_PHYSICAL_DENSITY = "Physical density: ";
	private static final String TEXT_PHYSICAL_SIZE = "Physical size: ";

	private static final String DEFAULT_SCREENSHOTS_DIR = "screenshots";
	private static final String DEFAULT_SCREENSHOTS_PREFIX = "output_";
	private static final int DEFAULT_SLEEP_TIME_MS = 1000;

	private static volatile ScreenShooterManager instance_;

	private AdbHelper adbHelper_;
	private DeviceShellHelper shellHelper_;
	private Device device_;

	private ScreenShooterManager() {
		adbHelper_ = AdbHelper.getInstance();
		if (!adbHelper_.isConnected()) {
			adbHelper_.restartAdb();
		}
		shellHelper_ = DeviceShellHelper.getInstance();
	}

	public static ScreenShooterManager getInstance() {
		if (instance_ == null) {
			synchronized (ScreenShooterManager.class) {
				if (instance_ == null) {
					instance_ = new ScreenShooterManager();
				}
			}
		}
		return instance_;
	}

	/**
	 * Requests device's display parameters
	 */
	public static void getDeviceDisplayInfo(@NonNull final IDevice iDevice, @NonNull final ScreenShooterManager.DeviceInfoListener deviceInfoListener) {
		final Device device = new Device(iDevice);
		try {
			// Request device density
			iDevice.executeShellCommand(DeviceShellHelper.COMMAND_WM_DENSITY, new IShellOutputReceiver() {
				@Override
				public void addOutput(byte[] bytes, int i, int i1) {
					try {
						Device.Dpi dpi = getDpiFromOutput(new String(bytes, i, i1, "UTF-8"));
						if (dpi != null) {
							device.setPhysicalDpi(dpi);
							device.setCurrentDpi(dpi);
						} else {
							deviceInfoListener.onDeviceUpdateFailed(iDevice, new Exception("Can't parse density output"));
						}
					} catch (UnsupportedEncodingException e) {
						deviceInfoListener.onDeviceUpdateFailed(iDevice, e);
						e.printStackTrace();
					}
				}

				@Override
				public void flush() {
					try {
						// Request device display size
						iDevice.executeShellCommand(DeviceShellHelper.COMMAND_WM_SIZE, new IShellOutputReceiver() {
							@Override
							public void addOutput(byte[] bytes, int i, int i1) {
								try {
									Device.Resolution resolution = getResolutionFromOutput(new String(bytes, i, i1, "UTF-8"));
									if (resolution != null) {
										device.setPhysicalResolution(resolution);
										device.setCurrentResolution(resolution);
									}
								} catch (UnsupportedEncodingException e) {
									e.printStackTrace();
								}
							}

							@Override
							public void flush() {
								// All done. Sending info to listener
								if (device.getPhysicalResolution() != null) {
									deviceInfoListener.onDeviceInfoUpdated(device);
								} else {
									deviceInfoListener.onDeviceUpdateFailed(iDevice, new Exception("Can't parse size output"));
								}
							}

							@Override
							public boolean isCancelled() {
								return false;
							}
						});
					} catch (TimeoutException | AdbCommandRejectedException | IOException | ShellCommandUnresponsiveException e) {
						e.printStackTrace();
					}
				}

				@Override
				public boolean isCancelled() {
					return false;
				}
			});
		} catch (TimeoutException | AdbCommandRejectedException | IOException | ShellCommandUnresponsiveException e) {
			e.printStackTrace();
			deviceInfoListener.onDeviceUpdateFailed(iDevice, e);
		}
	}

	/**
	 * Parses terminal output and picks up resolution
	 */
	private static Device.Resolution getResolutionFromOutput(String output) {
		int index = output.indexOf(TEXT_PHYSICAL_SIZE);
		if (index != -1) {
			int lineEndingIndex = output.lastIndexOf("\n");
			String resolutionString = output.substring(
					index + TEXT_PHYSICAL_SIZE.length(),
					lineEndingIndex != -1 ? lineEndingIndex - 1 : output.length()
			);
			String width = resolutionString.substring(0, resolutionString.indexOf('x'));
			String height = resolutionString.substring(resolutionString.indexOf('x') + 1);
			return Device.Resolution.fromSize(Integer.valueOf(width), Integer.valueOf(height));
		} else {
			return null;
		}
	}

	/**
	 * Parses terminal output and picks up density
	 */
	private static Device.Dpi getDpiFromOutput(String output) {
		int index = output.indexOf(TEXT_PHYSICAL_DENSITY);
		if (index != -1) {
			int lineEndingIndex = output.lastIndexOf("\n");
			String densityString = output.substring(
					index + TEXT_PHYSICAL_DENSITY.length(),
					lineEndingIndex != -1 ? lineEndingIndex - 1 : output.length()
			);
			return Device.Dpi.fromDensity(Integer.valueOf(densityString));
		} else {
			return null;
		}
	}

	public IDevice[] getDevices() {
		return adbHelper_.getDevices();
	}

	public void setDevice(@NonNull Device device) {
		device_ = device;
		shellHelper_.setIDevice(device_.getIDevice());
	}

	public void resetDeviceDisplay(CommandStatusListener statusListener) {
		shellHelper_.resetDeviceDisplay(statusListener);
	}

	/**
	 * Goes through all possible display params and makes a screenshots
	 */
	public void createScreenshotsForAllResolutions(final ScreenShooterManager.ScreenShotJobProgressListener progressListener) {
		if (device_ == null) {
			throw new RuntimeException("Device must be set up");
		}
		// TODO: add mode selection feature
		if (device_.getPhysicalDpi() == null || device_.getPhysicalResolution() == null) {
			throw new IllegalArgumentException("Device's physical dpi and resolution cannot be null");
		}
		// TODO: add custom dir / output prefix feature
		final File dir = new File(DEFAULT_SCREENSHOTS_DIR);
		if (!dir.exists() && !dir.mkdirs()) {
			throw new RuntimeException("Cannot create screenshots dir");
		}

		ScreenShooterManager.CommandStatusListener commandSentListener = new ScreenShooterManager.CommandStatusListener() {
			@Override
			public void onCommandSentToDevice() {
				// Display params changed. Need to wait activity re-initialising and then to make a screenshot
				sleepAndMakeScreenshot();

				// Setting up next display params
				device_.setCurrentDpi(device_.getCurrentDpi().getNext());
				if (device_.getCurrentDpi() == null) {
					// Went through all Dpis for current resolution. Setting up next resolution
					device_.setCurrentResolution(device_.getCurrentResolution().getNext());
					if (device_.getCurrentResolution() != null) {
						// Set current Dpi as min between Physical dpi and max dpi for the new resolution
						device_.setCurrentDpi(device_.getPhysicalDpi().getDensity() < device_.getCurrentResolution().getMaxDpi().getDensity() ?
								device_.getPhysicalDpi() : device_.getCurrentResolution().getMaxDpi());
					}
				}

				if (device_.getCurrentResolution() == null) {
					// Job is done
					if (progressListener != null) {
						progressListener.onScreenshotJobFinished();
					}
				} else {
					shellHelper_.setResolutionAndDensity(device_.getCurrentResolution(), device_.getCurrentDpi(), this);
				}
			}

			private void sleepAndMakeScreenshot() {
				System.out.println("Resolution changed, sleeping");
				try {
					// TODO: add custom sleep time feature
					Thread.sleep(DEFAULT_SLEEP_TIME_MS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				File output = new File(dir, DEFAULT_SCREENSHOTS_PREFIX + device_.getCurrentResolution() + "_" + device_.getCurrentDpi() + ".png");
				System.out.println("Woke up.. making a screenshot: " + output);
				try {
					output.createNewFile();
					if (shellHelper_.makeScreenshot(output)) {
						System.out.println("Success making a screenshot: " + output);
					} else {
						System.out.println("FAIL making a screenshot");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			@Override
			public void onCommandExecutionFailed() {
				if (progressListener != null) {
					progressListener.onScreenshotJobFailed();
				}
			}
		};
		shellHelper_.setResolutionAndDensity(device_.getCurrentResolution(), device_.getCurrentDpi(), commandSentListener);
	}

	public interface DeviceInfoListener {
		void onDeviceInfoUpdated(Device device);

		void onDeviceUpdateFailed(IDevice iDevice, Exception e);
	}

	public interface CommandStatusListener {
		void onCommandSentToDevice();

		void onCommandExecutionFailed();
	}

	public interface ScreenShotJobProgressListener {
		// TODO: add "cancel" feature
		// TODO: add "progress update" feature
		void onScreenshotJobFinished();

		void onScreenshotJobFailed();
	}
}