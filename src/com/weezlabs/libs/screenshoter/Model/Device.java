package com.weezlabs.libs.screenshoter.model;

import com.android.ddmlib.IDevice;

/**
 * Created by vfarafonov on 09.02.2016.
 */
public class Device {
	private IDevice iDevice_;
	private Dpi physicalDpi_;
	private Dpi currentDpi_;
	private Resolution physicalResolution_;
	private Resolution currentResolution_;

	public Device(IDevice iDevice) {
		iDevice_ = iDevice;
	}

	public IDevice getIDevice() {
		return iDevice_;
	}

	public Dpi getPhysicalDpi() {
		return physicalDpi_;
	}

	public void setPhysicalDpi(Dpi dpi) {
		this.physicalDpi_ = dpi;
	}

	public Resolution getPhysicalResolution() {
		return physicalResolution_;
	}

	public void setPhysicalResolution(Resolution resolution) {
		this.physicalResolution_ = resolution;
	}

	public Resolution getCurrentResolution() {
		return currentResolution_;
	}

	public void setCurrentResolution(Resolution currentResolution) {
		this.currentResolution_ = currentResolution;
	}

	public Dpi getCurrentDpi() {
		return currentDpi_;
	}

	public void setCurrentDpi(Dpi currentDpi) {
		this.currentDpi_ = currentDpi;
	}

	public enum Dpi {
		XXXHDPI(640),
		DPI_560(560),
		XXHDPI(480),
		DPI_420(420),
		XHDPI(320),
		HDPI(240),
		MDPI(160);

		static {
			XXXHDPI.next_ = DPI_560;
			DPI_560.next_ = XXHDPI;
			XXHDPI.next_ = DPI_420;
			DPI_420.next_ = XHDPI;
			XHDPI.next_ = HDPI;
			HDPI.next_ = MDPI;
		}

		private final int density_;
		private Dpi next_;

		Dpi(int density) {
			density_ = density;
		}

		public static Dpi fromDensity(int density) {
			for (Dpi dpi : Dpi.values()) {
				if (density == dpi.getDensity()) {
					return dpi;
				}
			}
			return null;
		}

		public Dpi getNext() {
			return next_;
		}

		public int getDensity() {
			return density_;
		}
	}

	public enum Resolution {
		XXLARGE_2(2560, 1600, Dpi.XHDPI),
		XXLARGE_1(2560, 1440, Dpi.DPI_560),
		XLARGE_2(1920, 1200, Dpi.DPI_560),
		XLARGE_1(1920, 1080, Dpi.DPI_560),
		LARGE_2(1280, 768, Dpi.DPI_420),
		LARGE_1(1280, 720, Dpi.DPI_420),
		NORMAL_PLUS_2(854, 480, Dpi.HDPI),
		NORMAL_PLUS_1(800, 480, Dpi.HDPI),
		NORMAL(480, 320, Dpi.MDPI),
		SMALL(320, 240, Dpi.MDPI);

		static {
			XXLARGE_2.next_ = XXLARGE_1;
			XXLARGE_1.next_ = XLARGE_2;
			XLARGE_2.next_ = XLARGE_1;
			XLARGE_1.next_ = LARGE_2;
			LARGE_2.next_ = LARGE_1;
			LARGE_1.next_ = NORMAL_PLUS_2;
			NORMAL_PLUS_2.next_ = NORMAL_PLUS_1;
			NORMAL_PLUS_1.next_ = NORMAL;
			NORMAL.next_ = SMALL;
			SMALL.next_ = null;
		}

		private final int height_;
		private final int width_;
		private final Dpi maxDpi_;
		private Resolution next_;

		Resolution(int height, int width, Dpi maxDpi) {
			height_ = height;
			width_ = width;
			maxDpi_ = maxDpi;
		}

		public static Resolution fromSize(int width, int height) {
			Resolution current = XXLARGE_2;
			int deltaHeight = current.height_ - height;
			int deltaWidth = current.width_ - width;
			Resolution last = current;
			while (current != null && (deltaHeight > 0 || deltaWidth > 0)) {
				deltaHeight = current.height_ - height;
				deltaWidth = current.width_ - width;
				last = current;
				current = current.next_;
			}
			return last;
		}

		public Resolution getNext() {
			return next_;
		}

		@Override
		public String toString() {
			return width_ + "x" + height_;
		}

		public Dpi getMaxDpi() {
			return maxDpi_;
		}
	}
}
