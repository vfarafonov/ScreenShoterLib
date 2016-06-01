package com.weezlabs.libs.screenshoter.model;

import com.android.ddmlib.IDevice;

/**
 * Created by vfarafonov on 09.02.2016.
 */
public class Device {
	private IDevice iDevice_;
	private DensityInterface physicalDpi_;
	private DensityInterface currentDpi_;
	private ResolutionInterface physicalResolution_;
	private ResolutionInterface currentResolution_;

	public Device(IDevice iDevice) {
		iDevice_ = iDevice;
	}

	public IDevice getIDevice() {
		return iDevice_;
	}

	public DensityInterface getPhysicalDpi() {
		return physicalDpi_;
	}

	public void setPhysicalDpi(DensityInterface dpi) {
		this.physicalDpi_ = dpi;
	}

	public ResolutionInterface getPhysicalResolution() {
		return physicalResolution_;
	}

	public void setPhysicalResolution(ResolutionInterface resolution) {
		this.physicalResolution_ = resolution;
	}

	public ResolutionInterface getCurrentResolution() {
		return currentResolution_;
	}

	public void setCurrentResolution(ResolutionInterface currentResolution) {
		this.currentResolution_ = currentResolution;
	}

	public DensityInterface getCurrentDpi() {
		return currentDpi_;
	}

	public void setCurrentDpi(DensityInterface currentDpi) {
		this.currentDpi_ = currentDpi;
	}

	public interface DensityInterface {
		int getDpiValue();

		DensityInterface getNext();
	}

	public interface ResolutionInterface {
		DensityInterface getMaxDpi();

		int getHeight();

		int getWidth();

		ResolutionInterface getNext();
	}
}
