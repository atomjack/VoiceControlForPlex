package com.atomjack.vcfp;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;

public class SquareImageButton extends ImageView {

  public SquareImageButton(Context context) {
    super(context);
  }

  public SquareImageButton(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public SquareImageButton(Context context, AttributeSet attrs, int defStyle) {
    super(context, attrs, defStyle);
  }

  int squareDim = 1000000000;

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    super.onLayout(changed, left, top, right, bottom);
    RelativeLayout layout = (RelativeLayout)getParent();
    if(layout != null && squareDim > 0) {
      int layoutHeight = layout.getHeight();

      if(layoutHeight > 0) {
        int topMargin = (layoutHeight - squareDim) / 2;

        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)getLayoutParams();
        layoutParams.setMargins(0, topMargin, 0, 0);
        setLayoutParams(layoutParams);
      }
    }
  }

  @Override
  public void onMeasure(int widthMeasureSpec, int heightMeasureSpec){
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);


    int h = this.getMeasuredHeight();
    int w = this.getMeasuredWidth();
    int curSquareDim = Math.min(w,h);

    if(curSquareDim < squareDim)
    {
      squareDim = curSquareDim;
    }

//    Logger.d("h "+h+"w "+w+"squareDim "+squareDim);


    setMeasuredDimension(squareDim, squareDim);



  }

}