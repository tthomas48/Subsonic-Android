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

 Copyright 2009 (C) Sindre Mehus
 */
package com.thejoshwa.ultrasonic.androidapp.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

import com.thejoshwa.ultrasonic.androidapp.domain.MusicDirectory;
import com.thejoshwa.ultrasonic.androidapp.domain.PlayerState;
import com.thejoshwa.ultrasonic.androidapp.util.Util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sindre Mehus
 */
public class MediaPlayerLifecycleSupport
{

	private static final String TAG = MediaPlayerLifecycleSupport.class.getSimpleName();

	private MediaPlayer mediaPlayer;
	private BroadcastReceiver headsetEventReceiver;
	private PhoneStateListener phoneStateListener;

	/**
	 * This receiver manages the intent that could come from other applications.
	 */
	private BroadcastReceiver intentReceiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			Log.i(TAG, "intentReceiver.onReceive: " + action);
			if (MediaPlayer.CMD_PLAY.equals(action))
			{
				mediaPlayer.play();
			}
			else if (MediaPlayer.CMD_NEXT.equals(action))
			{
				mediaPlayer.next();
			}
			else if (MediaPlayer.CMD_PREVIOUS.equals(action))
			{
				mediaPlayer.previous();
			}
			else if (MediaPlayer.CMD_TOGGLEPAUSE.equals(action))
			{
				mediaPlayer.togglePlayPause();
			}
			else if (MediaPlayer.CMD_PAUSE.equals(action))
			{
				mediaPlayer.pause();
			}
			else if (MediaPlayer.CMD_STOP.equals(action))
			{
				mediaPlayer.pause();
				mediaPlayer.seekTo(0);
			}
		}
	};


	public MediaPlayerLifecycleSupport(MediaPlayer mediaPlayer)
	{
		this.mediaPlayer = mediaPlayer;
	}

	public void onCreate()
	{

		// Pause when headset is unplugged.
		headsetEventReceiver = new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context context, Intent intent)
			{
				Bundle extras = intent.getExtras();

				if (extras == null)
				{
					return;
				}

				Log.i(TAG, String.format("Headset event for: %s", extras.get("name")));
				if (extras.getInt("state") == 0)
				{
					if (!mediaPlayer.isJukeboxEnabled())
					{
						mediaPlayer.pause();
					}
				}
			}
		};

		// React to media buttons.
		Util.registerMediaButtonEventReceiver(mediaPlayer);

		// Pause temporarily on incoming phone calls.
		phoneStateListener = new MyPhoneStateListener();
		TelephonyManager telephonyManager = (TelephonyManager) mediaPlayer.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

		// Register the handler for outside intents.
		IntentFilter commandFilter = new IntentFilter();
		commandFilter.addAction(MediaPlayer.CMD_PLAY);
		commandFilter.addAction(MediaPlayer.CMD_TOGGLEPAUSE);
		commandFilter.addAction(MediaPlayer.CMD_PAUSE);
		commandFilter.addAction(MediaPlayer.CMD_STOP);
		commandFilter.addAction(MediaPlayer.CMD_PREVIOUS);
		commandFilter.addAction(MediaPlayer.CMD_NEXT);
		mediaPlayer.registerReceiver(intentReceiver, commandFilter);

		int instance = Util.getActiveServer(mediaPlayer);
		mediaPlayer.setJukeboxEnabled(Util.getJukeboxEnabled(mediaPlayer, instance));

	}

	public void onStart(Intent intent)
	{
		if (intent != null && intent.getExtras() != null)
		{
			KeyEvent event = (KeyEvent) intent.getExtras().get(Intent.EXTRA_KEY_EVENT);
			if (event != null)
			{
				handleKeyEvent(event);
			}
		}
	}

	public void onDestroy()
	{
		mediaPlayer.unregisterReceiver(headsetEventReceiver);
		mediaPlayer.unregisterReceiver(intentReceiver);

		TelephonyManager telephonyManager = (TelephonyManager) mediaPlayer.getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
	}

	private void handleKeyEvent(KeyEvent event)
	{
		if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0)
		{
			return;
		}

		switch (event.getKeyCode())
		{
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			case KeyEvent.KEYCODE_HEADSETHOOK:
				mediaPlayer.togglePlayPause();
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				mediaPlayer.previous();
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				if (mediaPlayer.getCurrentPlayingIndex() < mediaPlayer.size() - 1)
				{
					mediaPlayer.next();
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_STOP:
				mediaPlayer.stop();
				break;
			case KeyEvent.KEYCODE_MEDIA_PLAY:
				if (mediaPlayer.getPlayerState() != PlayerState.STARTED)
				{
					mediaPlayer.start();
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_PAUSE:
				mediaPlayer.pause();
				break;
			default:
				break;
		}
	}

	/**
	 * Logic taken from packages/apps/Music.  Will pause when an incoming
	 * call rings or if a call (incoming or outgoing) is connected.
	 */
	private class MyPhoneStateListener extends PhoneStateListener
	{
		private boolean resumeAfterCall;

		@Override
		public void onCallStateChanged(int state, String incomingNumber)
		{
			switch (state)
			{
				case TelephonyManager.CALL_STATE_RINGING:
				case TelephonyManager.CALL_STATE_OFFHOOK:
					if (mediaPlayer.getPlayerState() == PlayerState.STARTED && !mediaPlayer.isJukeboxEnabled())
					{
						resumeAfterCall = true;
						mediaPlayer.pause();
					}
					break;
				case TelephonyManager.CALL_STATE_IDLE:
					if (resumeAfterCall)
					{
						resumeAfterCall = false;
						mediaPlayer.start();
					}
					break;
				default:
					break;
			}
		}
	}

	private static class State implements Serializable
	{
		private static final long serialVersionUID = -6346438781062572270L;

		private List<MusicDirectory.Entry> songs = new ArrayList<MusicDirectory.Entry>();
		private int currentPlayingIndex;
		private int currentPlayingPosition;
	}
}