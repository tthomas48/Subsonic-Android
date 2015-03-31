/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2011 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.audiofx;

import android.media.MediaPlayer;
import android.media.audiofx.Visualizer;
import android.util.Log;

/**
 * Backward-compatible wrapper for {@link Visualizer}, which is API Level 9.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
public class VisualizerController
{

	private static final String TAG = VisualizerController.class.getSimpleName();
	private static final int PREFERRED_CAPTURE_SIZE = 128; // Must be a power of two.

	private Visualizer visualizer;
	private boolean released;
	private int audioSessionId;

	// Class initialization fails when this throws an exception.
	static
	{
		try
		{
			Class.forName("android.media.audiofx.Visualizer");
		}
		catch (Exception ex)
		{
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Throws an exception if the {@link Visualizer} class is not available.
	 */
	public static void checkAvailable() throws Throwable
	{
		// Calling here forces class initialization.
	}

	public VisualizerController(MediaPlayer mediaPlayer)
	{
		try
		{
			if (mediaPlayer == null)
			{
				return;
			}

			audioSessionId = mediaPlayer.getAudioSessionId();
			visualizer = new Visualizer(audioSessionId);
		}
		catch (Throwable x)
		{
			Log.w(TAG, "Failed to create visualizer.", x);
		}

		if (visualizer != null)
		{
			int[] captureSizeRange = Visualizer.getCaptureSizeRange();
			int captureSize = Math.max(PREFERRED_CAPTURE_SIZE, captureSizeRange[0]);
			captureSize = Math.min(captureSize, captureSizeRange[1]);
			visualizer.setCaptureSize(captureSize);
		}
	}

	public boolean isAvailable()
	{
		return visualizer != null;
	}

	public void release()
	{
		if (isAvailable())
		{
			visualizer.release();
			released = true;
		}
	}

	public Visualizer getVisualizer()
	{
		if (released)
		{
			released = false;

			try
			{
				visualizer = new Visualizer(audioSessionId);
			}
			catch (Throwable x)
			{
				visualizer = null;
				Log.w(TAG, "Failed to create visualizer.", x);
			}
		}

		return visualizer;
	}
}