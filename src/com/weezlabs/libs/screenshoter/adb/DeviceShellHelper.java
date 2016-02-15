package com.weezlabs.libs.screenshoter.adb;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.sun.javafx.beans.annotations.NonNull;
import com.weezlabs.libs.screenshoter.model.Device;
import com.weezlabs.libs.screenshoter.ScreenShooterManager;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Helper to interact with {@link IDevice}'s shell
 * <p/>
 * Created by vfarafonov on 12.02.2016.
 */
public class DeviceShellHelper {
	public static final String COMMAND_WM_DENSITY = "wm density ";
	public static final String COMMAND_WM_SIZE = "wm size ";
	private static final String COMMAND_WM_DENSITY_RESET = "wm density reset";
	private static final String COMMAND_WM_SIZE_RESET = "wm size reset";

	private static volatile DeviceShellHelper instance_;
	private IDevice iDevice_;

	public static DeviceShellHelper getInstance() {
		if (instance_ == null) {
			synchronized (DeviceShellHelper.class) {
				if (instance_ == null) {
					instance_ = new DeviceShellHelper();
				}
			}
		}
		return instance_;
	}

	public void setIDevice(IDevice iDevice) {
		// TODO: add check if job is in progress
		iDevice_ = iDevice;
	}

	/**
	 * Sets up new display parameters
	 */
	public void setResolutionAndDensity(final Device.Resolution targetResolution, Device.Dpi targetDpi, @NonNull ScreenShooterManager.CommandStatusListener commandSentListener) {
		System.out.println(String.format("Setting params: size %s dpi %s", targetResolution, targetDpi));
		if (targetDpi != null) {
			setUpNewDensity(targetResolution, targetDpi, commandSentListener);
		} else if (targetResolution != null) {
			setUpNewResolution(targetResolution, commandSentListener);
		} else {
			commandSentListener.onCommandSentToDevice();
		}
	}

	/**
	 * Sets up new density and triggers resolution change if needed
	 */
	private void setUpNewDensity(final Device.Resolution targetResolution, Device.Dpi targetDpi, @NonNull final ScreenShooterManager.CommandStatusListener commandSentListener) {
		checkIDevice();
		try {
			iDevice_.executeShellCommand(COMMAND_WM_DENSITY + targetDpi.getDensity(), new ShellFlushReceiver() {
				@Override
				public void flush() {
					if (targetResolution != null) {
						setUpNewResolution(targetResolution, commandSentListener);
					} else {
						commandSentListener.onCommandSentToDevice();
					}
				}
			});
		} catch (TimeoutException | IOException | AdbCommandRejectedException | ShellCommandUnresponsiveException e) {
			e.printStackTrace();
			commandSentListener.onCommandExecutionFailed();
		}
	}

	/**
	 * Sets up new resolution
	 */
	private void setUpNewResolution(Device.Resolution targetResolution, @NonNull final ScreenShooterManager.CommandStatusListener commandSentListener) {
		checkIDevice();
		try {
			iDevice_.executeShellCommand(COMMAND_WM_SIZE + targetResolution, new ShellFlushReceiver() {
				@Override
				public void flush() {
					commandSentListener.onCommandSentToDevice();
				}
			});
		} catch (TimeoutException | AdbCommandRejectedException | IOException | ShellCommandUnresponsiveException e) {
			e.printStackTrace();
			commandSentListener.onCommandExecutionFailed();
		}
	}

	/**
	 * Makes screenshot and saves it to a file
	 */
	public boolean makeScreenshot(@NonNull File output) {
		checkIDevice();
		RawImage rawImage;
		try {
			rawImage = iDevice_.getScreenshot();
		} catch (TimeoutException e) {
			e.printStackTrace();
			return false;
		} catch (AdbCommandRejectedException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		if (rawImage == null) {
			return false;
		}

		BufferedImage bufferedImage = new BufferedImage(rawImage.width, rawImage.height, BufferedImage.TYPE_INT_ARGB);
		int index = 0;
		int indexIncBytes = rawImage.bpp >> 3;
		for (int y = 0; y < rawImage.height; y++) {
			for (int x = 0; x < rawImage.width; x++) {
				int value = rawImage.getARGB(index);
				index += indexIncBytes;
				bufferedImage.setRGB(x, y, value);
			}
		}
		try {
			if (ImageIO.write(bufferedImage, "png", output)) {
				return true;
			} else {
				return false;
			}
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}

	public void resetDeviceDisplay(final ScreenShooterManager.CommandStatusListener commandListener) {
		checkIDevice();
		try {
			// Reset display resolution
			iDevice_.executeShellCommand(COMMAND_WM_SIZE_RESET, new ShellFlushReceiver() {
				@Override
				public void flush() {
					try {
						// Reset display density
						iDevice_.executeShellCommand(COMMAND_WM_DENSITY_RESET, new ShellFlushReceiver() {
							@Override
							public void flush() {
								try {
									// Need to reset display density twice to make system UI looks perfect
									iDevice_.executeShellCommand(COMMAND_WM_DENSITY_RESET, new ShellFlushReceiver() {
										@Override
										public void flush() {
											if (commandListener != null) {
												commandListener.onCommandSentToDevice();
											}
										}
									});
								} catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
									e.printStackTrace();
									if (commandListener != null) {
										commandListener.onCommandExecutionFailed();
									}
								}
							}
						});
					} catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
						e.printStackTrace();
						if (commandListener != null) {
							commandListener.onCommandExecutionFailed();
						}
					}
				}
			});
		} catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e) {
			e.printStackTrace();
			if (commandListener != null) {
				commandListener.onCommandExecutionFailed();
			}
		}
	}

	private void checkIDevice() {
		if (iDevice_ == null) {
			throw new RuntimeException("You must set IDevice");
		}
	}

	/**
	 * Receives only flush event. use if you do not need to do anything on other events
	 */
	private abstract class ShellFlushReceiver implements IShellOutputReceiver {
		@Override
		public void addOutput(byte[] bytes, int i, int i1) {

		}

		@Override
		public boolean isCancelled() {
			return false;
		}
	}
}
