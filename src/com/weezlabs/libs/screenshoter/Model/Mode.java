package com.weezlabs.libs.screenshoter.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by vfarafonov on 15.02.2016.
 */
public class Mode {
	private final Device.Resolution resolution_;
	private final Device.Dpi density_;
	private boolean isActivated_ = true;

	public Mode(Device.Resolution resolution, Device.Dpi density) {
		resolution_ = resolution;
		density_ = density;
	}

	public static List<Mode> getModesQueue(Device device) {
		List<Mode> modes = new ArrayList<>();
		Device.Resolution resolution = device.getPhysicalResolution();
		Device.Dpi density = device.getPhysicalDpi();
		while (resolution != null) {
			modes.add(new Mode(resolution, density));
			density = density.getNext();
			if (density == null) {
				density = device.getPhysicalDpi();
				resolution = resolution.getNext();
			}
		}
		return modes;
	}

	public Device.Resolution getResolution() {
		return resolution_;
	}

	public Device.Dpi getDensity() {
		return density_;
	}

	public void setIsSkipping(boolean isSkipping) {
		isActivated_ = isSkipping;
	}

	public boolean isSkipping() {
		return isActivated_;
	}
}
