package com.thejoshwa.ultrasonic.androidapp.util;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * Created by tthomas on 6/19/15.
 */
public class SquareLinearLayout extends LinearLayout
{

	public SquareLinearLayout(Context context)
	{
		super(context);
	}

	public SquareLinearLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	public SquareLinearLayout(Context context, AttributeSet attrs, int defStyleAttr)
	{
		super(context, attrs, defStyleAttr);
	}

	public SquareLinearLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes)
	{
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		// Set a square layout.
		super.onMeasure(widthMeasureSpec, widthMeasureSpec);
	}
}
