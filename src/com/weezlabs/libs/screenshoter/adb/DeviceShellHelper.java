package com.weezlabs.libs.screenshoter.adb;

import com.android.ddmlib.AdbCommandRejectedException;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.ddmlib.RawImage;
import com.android.ddmlib.ShellCommandUnresponsiveException;
import com.android.ddmlib.TimeoutException;
import com.sun.javafx.beans.annotations.NonNull;
import com.weezlabs.libs.screenshoter.ScreenShooterManager;
import com.weezlabs.libs.screenshoter.model.Device;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import javax.imageio.ImageIO;

/**
 * Helper to interact with {@link IDevice}'s shell
 * <p/>
 * Created by vfarafonov on 12.02.2016.
 */
public class DeviceShellHelper {
	public static final String COMMAND_WM_DENSITY = "wm density ";
	public static final String COMMAND_WM_SIZE = "wm size ";
	public static final String COMMAND_AM_DENSITY = "am display-density ";
	public static final String COMMAND_AM_SIZE = "am display-size ";
	public static final String TEXT_INIT = "init=";
	public static final int JELLY_BEAN_4_3_LEVEL = 18;
	private static final String COMMAND_WM_DENSITY_RESET = "wm density reset";
	private static final String COMMAND_WM_SIZE_RESET = "wm size reset";
	private static final String COMMAND_AM_DENSITY_RESET = "am display-density reset";
	private static final String COMMAND_AM_SIZE_RESET = "am display-size reset";
	private static final String COMMAND_SCREEN_INFO_PRE_18 = "dumpsys window";
	private static final String TEXT_PHYSICAL_DENSITY = "Physical density: ";
	private static final String TEXT_PHYSICAL_SIZE = "Physical size: ";
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

	/**
	 * Requests device's display parameters with different commands depending on API level
	 */
	public static void getDeviceDisplayInfo(@NonNull final IDevice iDevice, @NonNull final DeviceInfoListener deviceInfoListener) {
		if (iDevice.getApiLevel() >= JELLY_BEAN_4_3_LEVEL) {
			getDeviceInfoPostApi18(iDevice, deviceInfoListener);
		} else {
			getDeviceInfoPreApi18(iDevice, deviceInfoListener);
		}
	}

	private static void getDeviceInfoPreApi18(final IDevice iDevice, final DeviceInfoListener deviceInfoListener) {
		final Device device = new Device(iDevice);
		try {
			iDevice.executeShellCommand(COMMAND_SCREEN_INFO_PRE_18, new IShellOutputReceiver() {
				boolean result = false;

				@Override
				public void addOutput(byte[] bytes, int i, int i1) {
					try {
						result = getDeviceInfoFromOutputPreApi18(device, new String(bytes, i, i1, "UTF-8"));
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					}
				}

				@Override
				public void flush() {
					if (result) {
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

	private static boolean getDeviceInfoFromOutputPreApi18(Device device, String output) {
		// Expecting output like "init=480x800 240dpi b"
		int resolutionStartIndex = output.indexOf(TEXT_INIT);
		if (resolutionStartIndex != -1) {
			String resolutionString = output.substring(resolutionStartIndex + TEXT_INIT.length());
			String width = resolutionString.substring(0, resolutionString.indexOf('x'));
			String height = resolutionString.substring(resolutionString.indexOf('x') + 1, resolutionString.indexOf(" "));
			int dpiValue = Integer.valueOf(resolutionString.substring(resolutionString.indexOf(" ") + 1, resolutionString.indexOf("dpi")));

			device.setPhysicalResolution(Device.Resolution.fromSize(Integer.valueOf(width), Integer.valueOf(height)));
			device.setPhysicalDpi(Device.Dpi.fromDensity(dpiValue));
			return true;
		}
		return false;
	}

	private static void getDeviceInfoPostApi18(@NonNull final IDevice iDevice, @NonNull final DeviceInfoListener deviceInfoListener) {
		final Device device = new Device(iDevice);
		try {
			// Request device density
			iDevice.executeShellCommand(COMMAND_WM_DENSITY, new IShellOutputReceiver() {
				@Override
				public void addOutput(byte[] bytes, int i, int i1) {
					try {
						Device.Dpi dpi = getDpiFromOutputPost18(new String(bytes, i, i1, "UTF-8"));
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
						iDevice.executeShellCommand(COMMAND_WM_SIZE, new IShellOutputReceiver() {
							@Override
							public void addOutput(byte[] bytes, int i, int i1) {
								try {
									Device.Resolution resolution = getResolutionFromOutputPost18(new String(bytes, i, i1, "UTF-8"));
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
	private static Device.Resolution getResolutionFromOutputPost18(String output) {
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
	private static Device.Dpi getDpiFromOutputPost18(String output) {
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
			iDevice_.executeShellCommand(getDensityCommand(iDevice_) + targetDpi.getDpiValue(), new ShellFlushReceiver() {
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
	 * Returns command for density switching based on IDevice Api level
	 */
	private String getDensityCommand(IDevice iDevice_) {
		return iDevice_.getApiLevel() >= JELLY_BEAN_4_3_LEVEL ? COMMAND_WM_DENSITY : COMMAND_AM_DENSITY;
	}

	/**
	 * Returns command for size switching based on IDevice Api level
	 */
	private String getSizeCommand(IDevice iDevice_) {
		return iDevice_.getApiLevel() >= JELLY_BEAN_4_3_LEVEL ? COMMAND_WM_SIZE : COMMAND_AM_SIZE;
	}

	/**
	 * Sets up new resolution
	 */
	private void setUpNewResolution(Device.Resolution targetResolution, @NonNull final ScreenShooterManager.CommandStatusListener commandSentListener) {
		checkIDevice();
		try {
			iDevice_.executeShellCommand(getSizeCommand(iDevice_) + targetResolution, new ShellFlushReceiver() {
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

	/**
	 * Throws RuntimeException if iDevice_ is null
	 */
	private void checkIDevice() {
		if (iDevice_ == null) {
			throw new RuntimeException("You must set IDevice");
		}
	}

	public interface DeviceInfoListener {
		void onDeviceInfoUpdated(Device device);

		void onDeviceUpdateFailed(IDevice iDevice, Exception e);
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
