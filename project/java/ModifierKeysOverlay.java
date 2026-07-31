package org.openttdjgrpp.sdl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * On-screen sticky modifier keys (Ctrl / Shift) for games that need keyboard
 * modifiers on devices without a physical keyboard.
 *
 * Click once = key held down (button highlighted), click again = released.
 * Key events are injected through DemoGLSurfaceView.nativeKey(), the same
 * JNI path used by the hardware keyboard, so no C or game code changes are
 * required. MainActivity attaches this overlay to the video layout with a
 * right-edge, vertically-centered LayoutParams.
 */
public class ModifierKeysOverlay extends LinearLayout
{
	private static final int KEYCODE_CTRL_LEFT = 113;
	private static final int KEYCODE_SHIFT_LEFT = 59;

	private boolean ctrlDown = false;
	private boolean shiftDown = false;

	private final Button ctrlButton;
	private final Button shiftButton;

	public ModifierKeysOverlay(Context context)
	{
		super(context);
		setOrientation(VERTICAL);

		ctrlButton = makeButton("Ctrl");
		shiftButton = makeButton("Shift");

		ctrlButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v)
			{
				ctrlDown = !ctrlDown;
				DemoGLSurfaceView.nativeKey(KEYCODE_CTRL_LEFT, ctrlDown ? 1 : 0, 0, 0);
				updateStyle(ctrlButton, ctrlDown);
			}
		});
		shiftButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v)
			{
				shiftDown = !shiftDown;
				DemoGLSurfaceView.nativeKey(KEYCODE_SHIFT_LEFT, shiftDown ? 1 : 0, 0, 0);
				updateStyle(shiftButton, shiftDown);
			}
		});

		addView(ctrlButton);
		addView(shiftButton);
	}

	private Button makeButton(String label)
	{
		Button b = new Button(getContext());
		b.setText(label);
		b.setTextSize(11f);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		// Do not steal keyboard focus from the game view
		b.setFocusable(false);
		b.setFocusableInTouchMode(false);
		b.setMinimumWidth(0);
		b.setMinimumHeight(0);
		b.setPadding(0, 0, 0, 0);
		LayoutParams lp = new LayoutParams(dp(52), dp(38));
		lp.topMargin = dp(8);
		b.setLayoutParams(lp);
		updateStyle(b, false);
		return b;
	}

	private void updateStyle(Button b, boolean active)
	{
		if (active) {
			b.setBackgroundColor(Color.argb(0xDD, 0x1A, 0x8C, 0xFF));
			b.setTextColor(Color.WHITE);
		} else {
			b.setBackgroundColor(Color.argb(0x99, 0x00, 0x00, 0x00));
			b.setTextColor(Color.argb(0xE6, 0xFF, 0xFF, 0xFF));
		}
	}

	private int dp(int value)
	{
		return Math.round(getResources().getDisplayMetrics().density * value);
	}
}
