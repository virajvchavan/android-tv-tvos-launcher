package com.viraj.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** The focus highlight: a top-lit sheen in the same squircle as the tiles, optionally ringed while moving an app. */
final class GlassDrawable extends Drawable {
    private final float radius;
    private final float ringWidth;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final Path ringPath = new Path();
    private boolean lifted;

    GlassDrawable(float radius, float ringWidth) {
        this.radius = radius;
        this.ringWidth = ringWidth;
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringWidth);
        ring.setColor(0xFFFFFFFF);
    }

    void setLifted(boolean lifted) {
        this.lifted = lifted;
        updateFill(getBounds());
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        Squircle.build(path, bounds.width(), bounds.height(), radius);
        path.offset(bounds.left, bounds.top);
        float inset = ringWidth / 2;
        Squircle.build(ringPath, bounds.width() - ringWidth, bounds.height() - ringWidth, radius - inset);
        ringPath.offset(bounds.left + inset, bounds.top + inset);
        updateFill(bounds);
    }

    private void updateFill(Rect bounds) {
        int[] colors = lifted
                ? new int[] {0x5CFFFFFF, 0x14FFFFFF}
                : new int[] {0x38FFFFFF, 0x0AFFFFFF, 0x00FFFFFF};
        fill.setShader(new LinearGradient(0, bounds.top, 0, bounds.bottom, colors, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, fill);
        if (lifted) {
            canvas.drawPath(ringPath, ring);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        ring.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        ring.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
