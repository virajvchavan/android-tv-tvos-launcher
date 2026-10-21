package com.viraj.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * An app's banner (or icon on a plain card) masked to a squircle with anti-aliased edges.
 * View outline clipping on this TV is neither smooth-edged nor able to take a custom path,
 * so the tile is painted through a BitmapShader instead. The bitmap is rendered at the
 * focused size and cached on the AppEntry, so zooming in stays sharp and costs nothing.
 */
final class CardDrawable extends Drawable {
    private static final int CARD_COLOR = 0xFF1C1C1E;

    private final AppEntry app;
    private final float radius;
    private final float renderScale;
    private final int iconPadding;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Path path = new Path();
    private final Matrix matrix = new Matrix();

    CardDrawable(AppEntry app, float radius, float renderScale, int iconPadding) {
        this.app = app;
        this.radius = radius;
        this.renderScale = renderScale;
        this.iconPadding = iconPadding;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        if (bounds.isEmpty()) {
            return;
        }
        Bitmap bitmap = render(Math.round(bounds.width() * renderScale), Math.round(bounds.height() * renderScale));
        BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        matrix.setScale(bounds.width() / (float) bitmap.getWidth(), bounds.height() / (float) bitmap.getHeight());
        matrix.postTranslate(bounds.left, bounds.top);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        Squircle.build(path, bounds.width(), bounds.height(), radius);
        path.offset(bounds.left, bounds.top);
    }

    private Bitmap render(int w, int h) {
        if (app.card != null && app.card.getWidth() == w && app.card.getHeight() == h) {
            return app.card;
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(CARD_COLOR);
        Drawable d = app.banner != null ? app.banner : app.icon;
        if (d != null) {
            Rect saved = d.copyBounds();
            d.setBounds(app.banner != null ? fill(d, w, h) : fit(d, w, h, Math.round(iconPadding * renderScale)));
            d.draw(canvas);
            d.setBounds(saved);
        }
        app.card = bitmap;
        return bitmap;
    }

    private static Rect fill(Drawable d, int w, int h) {
        int iw = d.getIntrinsicWidth();
        int ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            return new Rect(0, 0, w, h);
        }
        float scale = Math.max(w / (float) iw, h / (float) ih);
        int dw = Math.round(iw * scale);
        int dh = Math.round(ih * scale);
        return new Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
    }

    private static Rect fit(Drawable d, int w, int h, int pad) {
        int bw = w - 2 * pad;
        int bh = h - 2 * pad;
        int iw = d.getIntrinsicWidth();
        int ih = d.getIntrinsicHeight();
        if (iw <= 0 || ih <= 0) {
            return new Rect(pad, pad, w - pad, h - pad);
        }
        float scale = Math.min(bw / (float) iw, bh / (float) ih);
        int dw = Math.round(iw * scale);
        int dh = Math.round(ih * scale);
        return new Rect((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
