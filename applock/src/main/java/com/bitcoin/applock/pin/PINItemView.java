package com.bitcoin.applock.pin;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.bitcoin.applock.R;

public class PINItemView {

    private float[] position;
    private int intendedRadius;
    private int currentRadius;
    private Paint backgroundPaint;

    private float[] textPosition;
    private Paint textPaint;
    private Resources resources;

    private PINItemAnimator.ItemAnimationDirection animationDirection = PINItemAnimator.ItemAnimationDirection.OUT;

    public PINItemView(float[] position, int[] minMaxRadius, Paint baseTextPaint, Paint baseBackgroundPaint, Resources context) {
        this.position = position;
        this.intendedRadius = minMaxRadius[1];
        this.currentRadius = minMaxRadius[0];
        this.resources = context;

        setupPaints(baseTextPaint, baseBackgroundPaint);
    }

    private void setupPaints(Paint baseTextPaint, Paint baseBackgroundPaint) {
        this.textPaint = new Paint();
        this.textPaint.setTextAlign(Paint.Align.CENTER);
        this.textPaint.setAntiAlias(true);
        this.textPaint.setColor(resources.getColor(R.color.applock__pin_default));
        this.textPaint.setTextSize(baseTextPaint.getTextSize());

        this.textPosition = new float[]{
                position[0],
                position[1] - ((textPaint.descent() + textPaint.ascent()) / 2)
        };

        this.backgroundPaint = new Paint();
        this.backgroundPaint.setTextAlign(Paint.Align.CENTER);
        this.backgroundPaint.setAntiAlias(true);
        this.backgroundPaint.setColor(baseBackgroundPaint.getColor());
    }

    public void draw(Canvas canvas, String textValue) {
        canvas.drawCircle(position[0], position[1], currentRadius, backgroundPaint);
        canvas.drawText(textValue, textPosition[0], textPosition[1], textPaint);
    }

    public void setAnimationDirection(PINItemAnimator.ItemAnimationDirection animationDirection) {
        this.animationDirection = animationDirection;
    }

    public void onAnimationUpdate(int colorResource) {
        //this.currentRadius = (int) (intendedRadius * percentCompleted);
        //this.textPaint.setAlpha((int) (255 * percentCompleted));
        this.backgroundPaint.setColor(resources.getColor(colorResource));
    }

    public boolean isAnimatedOut() {
        return animationDirection == PINItemAnimator.ItemAnimationDirection.OUT;
    }
}
