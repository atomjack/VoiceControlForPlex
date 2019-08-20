package com.atomjack.vcfp;

import android.content.Context;
import androidx.appcompat.widget.AppCompatSpinner;
import android.util.AttributeSet;

public class CustomSpinner extends AppCompatSpinner {
  private OnSpinnerEventsListener mListener;
  private boolean mOpenInitiated = false;

  public CustomSpinner(Context context, AttributeSet attrs, int defStyleAttr, int mode) {
    super(context, attrs, defStyleAttr, mode);
  }

  public CustomSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public CustomSpinner(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public CustomSpinner(Context context, int mode) {
    super(context, mode);
  }

  public CustomSpinner(Context context) {
    super(context);
  }

  public interface OnSpinnerEventsListener {
    void onSpinnerOpened();
    void onSpinnerClosed();
  }

  @Override
  public boolean performClick() {
    // register that the Spinner was opened so we have a status
    // indicator for the activity(which may lose focus for some other
    // reasons)
    mOpenInitiated = true;
    if (mListener != null) {
      mListener.onSpinnerOpened();
    }
    return super.performClick();
  }

  public void setSpinnerEventsListener(OnSpinnerEventsListener onSpinnerEventsListener) {
    mListener = onSpinnerEventsListener;
  }

  /**
   * Propagate the closed Spinner event to the listener from outside.
   */
  public void performClosedEvent() {
    mOpenInitiated = false;
    if (mListener != null) {
      mListener.onSpinnerClosed();
    }
  }

  /**
   * A boolean flag indicating that the Spinner triggered an open event.
   *
   * @return true for opened Spinner
   */
  public boolean hasBeenOpened() {
    return mOpenInitiated;
  }

  @Override
  public void onWindowFocusChanged(boolean hasWindowFocus) {
    super.onWindowFocusChanged(hasWindowFocus);
    if (hasBeenOpened() && hasWindowFocus) {
      performClosedEvent();
    }
  }
}
