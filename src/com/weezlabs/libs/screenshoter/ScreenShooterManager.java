package com.weezlabs.libs.screenshoter;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.sun.istack.internal.Nullable;
import com.sun.javafx.beans.annotations.NonNull;
import com.weezlabs.libs.screenshoter.adb.AdbHelper;
import com.weezlabs.libs.screenshoter.adb.DeviceShellHelper;
import com.weezlabs.libs.screenshoter.model.Device;
import com.weezlabs.libs.screenshoter.model.Mode;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import javax.swing.SwingWorker;

/**
 * Manager to interact with Adb. <br>
 * Typical flow:<br>
 * 1. Get IDevices list<br>
 * 2. Request parameters for selected IDevice and set created Device object to manager's instance<br>
 * 3. Start screenshots job with {@link ScreenShooterManager#createScreenshotsForAllResolutions(File, String, Integer, List, ScreenShotJobProgressListener)}<br>
 * 4. Reset display parameters with {@link ScreenShooterManager#resetDeviceDisplay(CommandStatusListener)}<br>
 * <p/>
 * Created by vfarafonov on 12.02.2016.
 */
public class ScreenShooterManager {
	public static final String DEFAULT_SCREENSHOTS_DIR = "screenshots";
	public static final String DEFAULT_SCREENSHOTS_PREFIX = "output_";
	public static final int DEFAULT_SLEEP_TIME_MS = 1000;
	private static final int SUCCESS = 1;
	private static final int FAIL = 2;
	private static final int CANCEL = 3;
	private static final String ADB_PATH_RELATIVE_TO_SDK_ROOT = "/platform-tools/adb";
	private static volatile ScreenShooterManager instance_;

	private AdbHelper adbHelper_;
	private DeviceShellHelper shellHelper_;
	private Device device_;
	private boolean isJobStarted;

	private ScreenShooterManager() {
	}

	private ScreenShooterManager(String adbPath) {
		adbHelper_ = AdbHelper.getInstance(adbPath);
		if (!adbHelper_.isConnected()) {
			adbHelper_.restartAdb();
		}
		shellHelper_ = DeviceShellHelper.getInstance();
	}

	public static ScreenShooterManager getInstance(String adbPath) {
		if (instance_ == null) {
			System.out.println("Creating sync");
			createInstance(adbPath);
		}
		return instance_;
	}

	public static void getInstanceAsync(final String adbPath, final ManagerInitListener listener) {
		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				if (instance_ == null) {
					createInstance(adbPath);
				}
				return null;
			}

			@Override
			protected void done() {
				if (listener != null) {
					listener.onManagerReady(instance_);
				}
			}
		}.execute();
	}

	private static synchronized void createInstance(String adbPath) {
		if (instance_ == null) {
			instance_ = new ScreenShooterManager(adbPath);
		}
	}

	/**
	 * Requests device's display parameters asynchronous
	 */
	public static void getDeviceDisplayInfoAsync(@NonNull final IDevice iDevice, @NonNull final DeviceShellHelper.DeviceInfoListener deviceInfoListener) {
		new SwingWorker<Void, Void>() {
			private Device device_;
			private Exception exception_;

			@Override
			protected Void doInBackground() throws Exception {
				DeviceShellHelper.getDeviceDisplayInfo(iDevice, new DeviceShellHelper.DeviceInfoListener() {
					@Override
					public void onDeviceInfoUpdated(Device device) {
						device_ = device;
					}

					@Override
					public void onDeviceUpdateFailed(IDevice iDevice, Exception e) {
						exception_ = e;
					}
				});
				return null;
			}

			@Override
			protected void done() {
				if (device_ != null) {
					deviceInfoListener.onDeviceInfoUpdated(device_);
				} else {
					deviceInfoListener.onDeviceUpdateFailed(iDevice, exception_);
				}
			}
		}.execute();
	}

	public static String getSystemAdbLocation() {
		String android_home = System.getenv("ANDROID_HOME");
		if (android_home != null) {
			String sdkPath = android_home + ADB_PATH_RELATIVE_TO_SDK_ROOT;
			if (checkForAdbInPath(sdkPath)) {
				return sdkPath;
			}
		}
		return null;
	}

	public static boolean checkForAdbInPath(String adbPath) {
		if (adbPath == null) {
			return false;
		}
		File adbDir = new File(adbPath).getParentFile();
		return adbDir != null &&
				adbDir.exists() &&
				adbDir.isDirectory() &&
				adbDir.list(new FilenameFilter() {
					@Override
					public boolean accept(File dir, String name) {
						return name.indexOf("adb") == 0;
					}
				}).length > 0;
	}

	public static void getDeviceDisplayInfo(@NonNull IDevice iDevice, @NonNull DeviceShellHelper.DeviceInfoListener deviceInfoListener) {
		DeviceShellHelper.getDeviceDisplayInfo(iDevice, deviceInfoListener);
	}

	public IDevice[] getDevices() {
		return adbHelper_.getDevices();
	}

	public void setDevice(@NonNull Device device) {
		device_ = device;
		shellHelper_.setIDevice(device_.getIDevice());
	}

	public void resetDeviceDisplayAsync(final CommandStatusListener statusListener) {
		new SwingWorker<Void, Void>() {
			private int result;

			@Override
			protected Void doInBackground() throws Exception {
				resetDeviceDisplay(new CommandStatusListener() {
					@Override
					public void onCommandSentToDevice() {
						result = SUCCESS;
					}

					@Override
					public void onCommandExecutionFailed() {
						result = FAIL;
					}
				});
				return null;
			}

			@Override
			protected void done() {
				if (statusListener != null) {
					if (result == SUCCESS) {
						statusListener.onCommandSentToDevice();
					} else {
						statusListener.onCommandExecutionFailed();
					}
				}
			}
		}.execute();
	}

	public void resetDeviceDisplay(CommandStatusListener statusListener) {
		shellHelper_.resetDeviceDisplay(statusListener);
	}

	/**
	 * Goes through all possible display params and makes a screenshots. Skips modes in excludeModes list. Works asynchronous.
	 *
	 * @param directory        Directory to save screenshots. Will try to create if not exists. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_DIR} will be used if null
	 * @param filePrefix       File prefix. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_PREFIX} will be used if null
	 * @param sleepTimeMs      Time to sleep before making a screenshot. {@link ScreenShooterManager#DEFAULT_SLEEP_TIME_MS} will be used if null
	 * @param excludeModes     List of modes excluded from making a screenshot
	 * @param progressListener Progress listener
	 */
	public void createScreenshotsForAllResolutionsAsync(@Nullable final File directory,
														@Nullable final String filePrefix,
														@Nullable final Integer sleepTimeMs,
														@Nullable final List<Mode> excludeModes,
														final ScreenShooterManager.ScreenShotJobProgressListener progressListener) {
		new SwingWorker<Void, Integer>() {
			public int totalCount_;
			private int result = 0;

			@Override
			protected Void doInBackground() throws Exception {
				createScreenshotsForAllResolutions(directory, filePrefix, sleepTimeMs, excludeModes, new ScreenShotJobProgressListener() {
					@Override
					public void onScreenshotJobFinished() {
						result = SUCCESS;
					}

					@Override
					public void onScreenshotJobFailed() {
						result = FAIL;
					}

					@Override
					public void onScreenshotJobCancelled() {
						result = CANCEL;
					}

					@Override
					public void onScreenshotJobProgressUpdate(int currentProgress, int totalCount) {
						if (totalCount_ == 0) {
							totalCount_ = totalCount;
						}
						publish(currentProgress);
					}
				});
				return null;
			}

			@Override
			protected void process(List<Integer> chunks) {
				if (progressListener != null) {
					progressListener.onScreenshotJobProgressUpdate(chunks.get(chunks.size() - 1), totalCount_);
				}
			}

			@Override
			protected void done() {
				if (progressListener != null) {
					if (result == SUCCESS) {
						progressListener.onScreenshotJobFinished();
					} else if (result == CANCEL) {
						progressListener.onScreenshotJobCancelled();
					} else if (result == FAIL) {
						progressListener.onScreenshotJobFailed();
					}
				}
			}
		}.execute();
	}

	/**
	 * Goes through all specified modes and makes a screenshots. Works asynchronous.
	 *
	 * @param directory        Directory to save screenshots. Will try to create if not exists. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_DIR} will be used if null
	 * @param filePrefix       File prefix. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_PREFIX} will be used if null
	 * @param sleepTimeMs      Time to sleep before making a screenshot. {@link ScreenShooterManager#DEFAULT_SLEEP_TIME_MS} will be used if null
	 * @param modes            List of modes
	 * @param progressListener Progress listener
	 */
	public void createScreenshotsForModesAsync(@Nullable final File directory,
											   @Nullable final String filePrefix,
											   @Nullable final Integer sleepTimeMs,
											   @NonNull final List<Mode> modes,
											   final ScreenShooterManager.ScreenShotJobProgressListener progressListener) {
		new SwingWorker<Void, Integer>() {
			public int totalCount_;
			private int result = 0;

			@Override
			protected Void doInBackground() throws Exception {
				createScreenshotsForModes(directory, filePrefix, sleepTimeMs, modes, new ScreenShotJobProgressListener() {
					@Override
					public void onScreenshotJobFinished() {
						result = SUCCESS;
					}

					@Override
					public void onScreenshotJobFailed() {
						result = FAIL;
					}

					@Override
					public void onScreenshotJobCancelled() {
						result = CANCEL;
					}

					@Override
					public void onScreenshotJobProgressUpdate(int currentProgress, int totalCount) {
						if (totalCount_ == 0) {
							totalCount_ = totalCount;
						}
						publish(currentProgress);
					}
				});
				return null;
			}

			@Override
			protected void process(List<Integer> chunks) {
				if (progressListener != null) {
					progressListener.onScreenshotJobProgressUpdate(chunks.get(chunks.size() - 1), totalCount_);
				}
			}

			@Override
			protected void done() {
				if (progressListener != null) {
					if (result == SUCCESS) {
						progressListener.onScreenshotJobFinished();
					} else if (result == CANCEL) {
						progressListener.onScreenshotJobCancelled();
					} else if (result == FAIL) {
						progressListener.onScreenshotJobFailed();
					}
				}
			}
		}.execute();
	}

	/**
	 * Goes through all possible display params and makes a screenshots. Skips modes in excludeModes list
	 *
	 * @param directory        Directory to save screenshots. Will try to create if not exists. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_DIR} will be used if null
	 * @param filePrefix       File prefix. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_PREFIX} will be used if null
	 * @param sleepTimeMs      Time to sleep before making a screenshot. {@link ScreenShooterManager#DEFAULT_SLEEP_TIME_MS} will be used if null
	 * @param excludeModes     List of modes excluded from making a screenshot
	 * @param progressListener Progress listener
	 */
	public void createScreenshotsForAllResolutions(@Nullable File directory,
												   @Nullable final String filePrefix,
												   @Nullable final Integer sleepTimeMs,
												   final List<Mode> excludeModes,
												   final ScreenShotJobProgressListener progressListener) {
		checkIfDeviceReady();

		List<Mode> modesList = Mode.getModesQueue(device_);
		// Remove excluded modes from list
		if (excludeModes != null && excludeModes.size() > 0) {
			for (int i = modesList.size() - 1; i >= 0; i--) {
				if (excludeModes.contains(modesList.get(i))) {
					System.out.println("removing: " + modesList.get(i).getDensity() + " | " + modesList.get(i).getResolution());
					modesList.remove(i);
				}
			}
		}

		createScreenshotsForModes(directory, filePrefix, sleepTimeMs, modesList, progressListener);
	}

	/**
	 * Goes through list of modes and makes a screenshots.
	 *
	 * @param directory        Directory to save screenshots. Will try to create if not exists. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_DIR} will be used if null
	 * @param filePrefix       File prefix. {@link ScreenShooterManager#DEFAULT_SCREENSHOTS_PREFIX} will be used if null
	 * @param sleepTimeMs      Time to sleep before making a screenshot. {@link ScreenShooterManager#DEFAULT_SLEEP_TIME_MS} will be used if null
	 * @param modes            List of modes
	 * @param progressListener Progress listener
	 */
	public void createScreenshotsForModes(@Nullable File directory,
										  @Nullable final String filePrefix,
										  @Nullable final Integer sleepTimeMs,
										  @NonNull final List<Mode> modes,
										  final ScreenShotJobProgressListener progressListener) {
		checkIfDeviceReady();

		final File dir = directory != null ? directory : new File(DEFAULT_SCREENSHOTS_DIR);
		if (!dir.exists() && !dir.mkdirs()) {
			throw new RuntimeException("Cannot create screenshots dir");
		}

		isJobStarted = true;

		ScreenShooterManager.CommandStatusListener commandSentListener = new ScreenShooterManager.CommandStatusListener() {

			@Override
			public void onCommandSentToDevice() {
				if (checkIsCancelled(progressListener)) {
					return;
				}
				// Display params changed. Need to wait activity re-initialising and then to make a screenshot
				sleepAndMakeScreenshot();
			}

			@Override
			public void onCommandExecutionFailed() {
				isJobStarted = false;
				if (progressListener != null) {
					progressListener.onScreenshotJobFailed();
				}
			}

			private void sleepAndMakeScreenshot() {
				System.out.println("Resolution changed, sleeping");
				try {
					Thread.sleep(sleepTimeMs != null ? sleepTimeMs : DEFAULT_SLEEP_TIME_MS);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				String fileName = filePrefix != null ? filePrefix : DEFAULT_SCREENSHOTS_PREFIX;
				fileName = fileName + device_.getCurrentResolution() + "_" + device_.getCurrentDpi() + ".png";
				File output = new File(dir, fileName);
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
		};

		int size = modes.size();
		for (int i = 0; i < size; i++) {
			if (checkIsCancelled(progressListener)) {
				break;
			}
			Mode mode = modes.get(i);
			device_.setCurrentDpi(mode.getDensity());
			device_.setCurrentResolution(mode.getResolution());
			shellHelper_.setResolutionAndDensity(mode.getResolution(), mode.getDensity(), commandSentListener);
			if (progressListener != null) {
				progressListener.onScreenshotJobProgressUpdate(i + 1, size);
			}
		}
		isJobStarted = false;
		if (progressListener != null) {
			progressListener.onScreenshotJobFinished();
		}
	}

	private void checkIfDeviceReady() {
		if (device_ == null) {
			throw new RuntimeException("Device must be set up");
		}
		if (device_.getPhysicalDpi() == null || device_.getPhysicalResolution() == null) {
			throw new IllegalArgumentException("Device's physical dpi and resolution cannot be null");
		}
	}

	private boolean checkIsCancelled(ScreenShotJobProgressListener progressListener) {
		if (!isJobStarted) {
			if (progressListener != null) {
				progressListener.onScreenshotJobCancelled();
			}
			return true;
		}
		return false;
	}

	public void stopScreenshotsJob() {
		if (isJobStarted) {
			isJobStarted = false;
		}
	}

	public void addDeviceChangeListener(AndroidDebugBridge.IDeviceChangeListener listener) {
		adbHelper_.addDeviceListener(listener);
	}

	public interface ManagerInitListener {
		void onManagerReady(ScreenShooterManager manager);
	}

	public interface CommandStatusListener {
		void onCommandSentToDevice();

		void onCommandExecutionFailed();
	}

	public interface ScreenShotJobProgressListener {
		void onScreenshotJobFinished();

		void onScreenshotJobFailed();

		void onScreenshotJobCancelled();

		void onScreenshotJobProgressUpdate(int currentProgress, int totalCount);
	}
}
