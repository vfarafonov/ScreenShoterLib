package com.weezlabs.libs.screenshoter.model;

/**
 * Created by vfarafonov on 01.06.2016.
 */
public class Resolution implements Device.ResolutionInterface {
	private final int height_;
	private final int width_;
	private final Device.DensityInterface maxDpi_;
	private final Device.ResolutionInterface next_;

	public Resolution(int height, int width, Device.DensityInterface maxDpi, Device.ResolutionInterface next) {
		this.maxDpi_ = maxDpi;
		this.height_ = height;
		this.width_ = width;
		this.next_ = next;
	}

	@Override
	public Device.DensityInterface getMaxDpi() {
		return maxDpi_;
	}

	@Override
	public int getHeight() {
		return height_;
	}

	@Override
	public int getWidth() {
		return width_;
	}

	@Override
	public Device.ResolutionInterface getNext() {
		return next_;
	}

	@Override
	public String toString() {
		return width_ + "x" + height_;
	}

	public enum DefaultResolution implements Device.ResolutionInterface {
		XXLARGE_2(2560, 1600, Density.DefaultDensity.XHDPI),
		XXLARGE_1(2560, 1440, Density.DefaultDensity.DPI_560),
		XLARGE_2(1920, 1200, Density.DefaultDensity.DPI_560),
		XLARGE_1(1920, 1080, Density.DefaultDensity.DPI_560),
		LARGE_2(1280, 768, Density.DefaultDensity.DPI_420),
		LARGE_1(1280, 720, Density.DefaultDensity.DPI_420),
		NORMAL_PLUS_2(854, 480, Density.DefaultDensity.HDPI),
		NORMAL_PLUS_1(800, 480, Density.DefaultDensity.HDPI),
		NORMAL(480, 320, Density.DefaultDensity.MDPI),
		SMALL(320, 240, Density.DefaultDensity.MDPI);

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
		private final Device.DensityInterface maxDpi_;
		private Device.ResolutionInterface next_;

		DefaultResolution(int height, int width, Device.DensityInterface maxDpi) {
			height_ = height;
			width_ = width;
			maxDpi_ = maxDpi;
		}

		public static Device.ResolutionInterface fromSize(int width, int height) {
			Device.ResolutionInterface current = XXLARGE_2;
			int deltaHeight = current.getHeight() - height;
			int deltaWidth = current.getWidth() - width;
			Device.ResolutionInterface last = current;
			while (current != null && (deltaHeight > 0 || deltaWidth > 0)) {
				deltaHeight = current.getHeight() - height;
				deltaWidth = current.getWidth() - width;
				last = current;
				current = current.getNext();
			}
			return last;
		}

		public Device.ResolutionInterface getNext() {
			return next_;
		}

		@Override
		public String toString() {
			return width_ + "x" + height_;
		}

		public Device.DensityInterface getMaxDpi() {
			return maxDpi_;
		}

		public int getHeight() {
			return height_;
		}

		public int getWidth() {
			return width_;
		}
	}
}
