package com.weezlabs.libs.screenshoter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vfarafonov on 15.02.2016.
 */
public class Mode {
	private final Device.ResolutionInterface resolution_;
	private final Device.DensityInterface density_;
	private boolean isActivated_ = true;

	public Mode(Device.ResolutionInterface resolution, Device.DensityInterface density) {
		resolution_ = resolution;
		density_ = density;
	}

	public static List<Mode> getDefaultModesQueue(Device device) {
		List<Mode> modes = new ArrayList<>();
		Device.ResolutionInterface resolution = device.getPhysicalResolution();
		Device.DensityInterface density = device.getPhysicalDpi();
		while (resolution != null) {
			modes.add(new Mode(resolution, density));
			density = density.getNext();
			if (density == null) {
				resolution = resolution.getNext();
				density = device.getPhysicalDpi();
				if (resolution != null) {
					density = device.getPhysicalDpi().getDpiValue() < resolution.getMaxDpi().getDpiValue() ? device.getPhysicalDpi() : resolution.getMaxDpi();
				}
			}
		}
		return modes;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == null) {
			return false;
		}
		if (!Mode.class.isAssignableFrom(obj.getClass())) {
			return false;
		}
		final Mode other = (Mode) obj;
		return resolution_.getHeight() == other.getResolution().getHeight() &&
				resolution_.getWidth() == other.getResolution().getWidth() &&
				density_.getDpiValue() == other.getDensity().getDpiValue();
	}

	public Device.ResolutionInterface getResolution() {
		return resolution_;
	}

	public Device.DensityInterface getDensity() {
		return density_;
	}

	public void setIsActivated(boolean isActivated) {
		isActivated_ = isActivated;
	}

	public boolean isActivated() {
		return isActivated_;
	}
}
