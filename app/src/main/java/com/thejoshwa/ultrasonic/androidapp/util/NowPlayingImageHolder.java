package com.thejoshwa.ultrasonic.androidapp.util;

import android.view.View;
import android.widget.IconTextView;
import android.widget.ImageView;
import android.widget.TextView;

import com.thejoshwa.ultrasonic.androidapp.R;

/**
 * Created by tthomas on 7/3/15.
 */
public class NowPlayingImageHolder extends ImageHolder
{

	public NowPlayingImageHolder(View nowPlayingView) {
		super();
		addImageView((ImageView) nowPlayingView.findViewById(R.id.now_playing_image));
		addBackground(nowPlayingView.findViewById(R.id.now_playing));
		addTitleText((TextView) nowPlayingView.findViewById(R.id.now_playing_trackname));
		addBodyText((TextView) nowPlayingView.findViewById(R.id.now_playing_artist));
		addBodyText((IconTextView) nowPlayingView.findViewById(R.id.now_playing_control_play));
	}

}
