package com.viraj.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The focus highlight, drawn over the focused card: a top-lit sheen in the tile's squircle and a ring hugging the
 * card's outside edge, tinted with the app's colour. While an app is being moved, a white ring sits on the card's
 * edge instead and the sheen brightens. The bounds extend {@code margin} past the card to leave room for the ring.
 */
final class GlassDrawable extends Drawable {
    private final float radius;
    private final float margin;
    private final float ringWidth;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focusRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint liftRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path card = new Path();
    private final Path focusPath = new Path();
    private final Path liftPath = new Path();
    private boolean lifted;

    GlassDrawable(float radius, float margin, float ringWidth) {
        this.radius = radius;
        this.margin = margin;
        this.ringWidth = ringWidth;
        for (Paint ring : new Paint[] {focusRing, liftRing}) {
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(ringWidth);
            ring.setColor(0xF2FFFFFF);
        }
    }

    void setRingColor(int color) {
        focusRing.setColor(color);
        invalidateSelf();
    }

    void setLifted(boolean lifted) {
        this.lifted = lifted;
        updateFill();
        invalidateSelf();
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        RectF c = new RectF(bounds);
        c.inset(margin, margin);
        squircle(card, c, 0, radius);
        squircle(focusPath, c, ringWidth / 2, radius + ringWidth / 2);
        squircle(liftPath, c, -ringWidth / 2, radius - ringWidth / 2);
        updateFill();
    }

    private static void squircle(Path path, RectF r, float grow, float radius) {
        Squircle.build(path, r.width() + 2 * grow, r.height() + 2 * grow, radius);
        path.offset(r.left - grow, r.top - grow);
    }

    private void updateFill() {
        RectF c = new RectF(getBounds());
        c.inset(margin, margin);
        int[] colors = lifted
                ? new int[] {0x5CFFFFFF, 0x14FFFFFF}
                : new int[] {0x38FFFFFF, 0x0AFFFFFF, 0x00FFFFFF};
        fill.setShader(new LinearGradient(0, c.top, 0, c.bottom, colors, null, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(card, fill);
        if (lifted) {
            canvas.drawPath(liftPath, liftRing);
        } else {
            canvas.drawPath(focusPath, focusRing);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        fill.setAlpha(alpha);
        focusRing.setAlpha(alpha);
        liftRing.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        focusRing.setColorFilter(colorFilter);
        liftRing.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
