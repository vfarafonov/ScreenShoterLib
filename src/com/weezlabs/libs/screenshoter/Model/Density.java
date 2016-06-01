package com.weezlabs.libs.screenshoter.model;

/**
 * Created by vfarafonov on 01.06.2016.
 */
public class Density implements Device.DensityInterface {
	private final int density_;
	private Device.DensityInterface next_;

	public Density(int density, Device.DensityInterface next) {
		this.density_ = density;
		this.next_ = next;
	}

	public Density(int density) {
		this.density_ = density;
	}

	@Override
	public int getDpiValue() {
		return density_;
	}

	@Override
	public Device.DensityInterface getNext() {
		return next_;
	}

	public void setNext(Device.DensityInterface next) {
		this.next_ = next;
	}

	@Override
	public String toString() {
		return density_ + "dpi";
	}

	public enum DefaultDensity implements Device.DensityInterface {
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
		private Device.DensityInterface next_;

		DefaultDensity(int density) {
			density_ = density;
		}

		public static DefaultDensity fromDensity(int density) {
			for (DefaultDensity dpi : DefaultDensity.values()) {
				if (density == dpi.getDpiValue()) {
					return dpi;
				}
			}
			return null;
		}

		@Override
		public Device.DensityInterface getNext() {
			return next_;
		}

		@Override
		public int getDpiValue() {
			return density_;
		}
	}
}
