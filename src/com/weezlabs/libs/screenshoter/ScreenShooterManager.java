package com.weezlabs.libs.screenshoter;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.sun.istack.internal.Nullable;
import com.sun.javafx.beans.annotations.NonNull;
import com.weezlabs.libs.screenshoter.adb.AdbHelper;
import com.weezlabs.libs.screenshoter.adb.DeviceShellHelper;
import com.weezlabs.libs.screenshoter.model.Device;
import com.weezlabs.libs.screenshoter.model.Mode;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
	private static final String TEXT_PHYSICAL_DENSITY = "Physical density: ";
	private static final String TEXT_PHYSICAL_SIZE = "Physical size: ";
	private static final int SUCCESS = 1;
	private static final int FAIL = 2;
	private static final int CANCEL = 3;
	private static volatile ScreenShooterManager instance_;

	private AdbHelper adbHelper_;
	private DeviceShellHelper shellHelper_;
	private Device device_;
	private boolean isJobStarted;

	private ScreenShooterManager() {
		adbHelper_ = AdbHelper.getInstance();
		if (!adbHelper_.isConnected()) {
			adbHelper_.restartAdb();
		}
		shellHelper_ = DeviceShellHelper.getInstance();
	}

	public static ScreenShooterManager getInstance() {
		if (instance_ == null) {
			System.out.println("Creating sync");
			createInstance();
		}
		return instance_;
	}

	public static void getInstanceAsync(final ManagerInitListener listener) {
		new SwingWorker<Void, Void>() {

			@Override
			protected Void doInBackground() throws Exception {
				if (instance_ == null) {
					createInstance();
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

	private static synchronized void createInstance() {
		if (instance_ == null) {
			instance_ = new ScreenShooterManager();
		}
	}

	/**
	 * Requests device's display parameters asynchronous
	 */
	public static void getDeviceDisplayInfoAsync(@NonNull final IDevice iDevice, @NonNull final ScreenShooterManager.DeviceInfoListener deviceInfoListener) {
		new SwingWorker<Void, Void>() {
			private Device device_;
			private Exception exception_;

			@Override
			protected Void doInBackground() throws Exception {
				getDeviceDisplayInfo(iDevice, new DeviceInfoListener() {
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
			int lineEndingIndex = output.indexOf("\n");
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
			int lineEndingIndex = output.indexOf("\n");
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
		if (device_ == null) {
			throw new RuntimeException("Device must be set up");
		}
		if (device_.getPhysicalDpi() == null || device_.getPhysicalResolution() == null) {
			throw new IllegalArgumentException("Device's physical dpi and resolution cannot be null");
		}

		final File dir = directory != null ? directory : new File(DEFAULT_SCREENSHOTS_DIR);
		if (!dir.exists() && !dir.mkdirs()) {
			throw new RuntimeException("Cannot create screenshots dir");
		}

		isJobStarted = true;

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

		int size = modesList.size();
		for (int i = 0; i < size; i++) {
			if (checkIsCancelled(progressListener)) {
				break;
			}
			Mode mode = modesList.get(i);
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

	public interface DeviceInfoListener {
		void onDeviceInfoUpdated(Device device);

		void onDeviceUpdateFailed(IDevice iDevice, Exception e);
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
