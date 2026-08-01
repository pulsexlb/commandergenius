package org.openttdjgrpp.sdl;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * On-screen sticky modifier keys (Ctrl / Shift / Alt) plus a keyboard button
 * for games that need keyboard input on devices without a physical keyboard.
 *
 * Modifier buttons: click once = key held down (button highlighted), click
 * again = released. Key events are injected through DemoGLSurfaceView.nativeKey(),
 * the same JNI path used by the hardware keyboard, so no C or game code changes
 * are required. The keyboard button opens the Android soft keyboard, whose
 * input reaches the game through the existing onKeyDown -> nativeKey forwarding.
 * MainActivity attaches this overlay to the video layout with a right-edge,
 * vertically-centered LayoutParams.
 */
public class ModifierKeysOverlay extends LinearLayout
{
	private static final int KEYCODE_CTRL_LEFT = 113;
	private static final int KEYCODE_SHIFT_LEFT = 59;
	private static final int KEYCODE_ALT_LEFT = 57;

	public ModifierKeysOverlay(Context context)
	{
		super(context);
		setOrientation(VERTICAL);

		addModifierButton("Ctrl", KEYCODE_CTRL_LEFT);
		addModifierButton("Shift", KEYCODE_SHIFT_LEFT);
		addModifierButton("Alt", KEYCODE_ALT_LEFT);

		Button keyboardButton = makeButton("KEY");
		keyboardButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v)
			{
				((MainActivity) getContext()).showScreenKeyboardWithoutTextInputField(Globals.TextInputKeyboard);
			}
		});
		addView(keyboardButton);
	}

	private void addModifierButton(final String label, final int keyCode)
	{
		final Button b = makeButton(label);
		b.setTag(Boolean.FALSE);
		b.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v)
			{
				boolean down = !((Boolean) b.getTag());
				b.setTag(down);
				DemoGLSurfaceView.nativeKey(keyCode, down ? 1 : 0, 0, 0);
				updateStyle(b, down);
			}
		});
		addView(b);
	}

	private Button makeButton(String label)
	{
		Button b = new Button(getContext());
		b.setText(label);
		b.setTextSize(10f);
		b.setTypeface(Typeface.DEFAULT_BOLD);
		// Do not steal keyboard focus from the game view
		b.setFocusable(false);
		b.setFocusableInTouchMode(false);
		b.setMinimumWidth(0);
		b.setMinimumHeight(0);
		b.setPadding(0, 0, 0, 0);
		LayoutParams lp = new LayoutParams(dp(44), dp(30));
		lp.topMargin = dp(6);
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
