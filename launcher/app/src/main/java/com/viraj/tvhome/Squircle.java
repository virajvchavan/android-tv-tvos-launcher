package com.viraj.tvhome;

import android.graphics.Path;

/**
 * Continuous-curvature rounded rectangle, the corner shape tvOS uses for app icons.
 * Built with Figma's corner-smoothing construction: each corner is a short circular arc
 * eased into the straight edges by a cubic on each side, so there is no visible kink.
 */
final class Squircle {
    static final float SMOOTHING = 0.6f;

    private Squircle() {
    }

    static void build(Path path, float w, float h, float radius) {
        path.reset();
        float r = Math.min(radius, Math.min(w, h) / 2f / (1 + SMOOTHING));
        float p = (1 + SMOOTHING) * r;
        double arcMeasure = Math.toRadians(90 * (1 - SMOOTHING));
        float arcLen = (float) (Math.sin(arcMeasure / 2) * r * Math.sqrt(2));
        double alpha = (Math.PI / 2 - arcMeasure) / 2;
        float p3ToP4 = (float) (r * Math.tan(alpha / 2));
        double beta = Math.toRadians(45 * SMOOTHING);
        float c = (float) (p3ToP4 * Math.cos(beta));
        float d = (float) (c * Math.tan(beta));
        float b = (p - arcLen - c - d) / 3;
        float a = 2 * b;
        // One cubic stands in for the short arc; at under 45 degrees the error is far below a pixel.
        float k = (float) (4.0 / 3.0 * Math.tan(arcMeasure / 4) * r);
        float ax = a + b + c;
        float ay = d;
        float bx = ax + arcLen;
        float by = ay + arcLen;
        float cx = p - r;
        float cy = r;
        float ta = (float) Math.hypot(ay - cy, ax - cx);
        float tb = (float) Math.hypot(by - cy, bx - cx);

        // Corners clockwise from top-right: corner point, then the incoming (u) and outgoing (v) edge directions.
        float[][] corners = {
                {w, 0, 1, 0, 0, 1},
                {w, h, 0, 1, -1, 0},
                {0, h, -1, 0, 0, -1},
                {0, 0, 0, -1, 1, 0},
        };
        path.moveTo(p, 0);
        for (float[] cn : corners) {
            float ox = cn[0] - p * cn[2];
            float oy = cn[1] - p * cn[3];
            float ux = cn[2], uy = cn[3], vx = cn[4], vy = cn[5];
            Mapper m = (x, y, i) -> i == 0 ? ox + x * ux + y * vx : oy + x * uy + y * vy;
            path.lineTo(m.at(0, 0, 0), m.at(0, 0, 1));
            cubic(path, m, a, 0, a + b, 0, ax, ay);
            // Arc tangents are the radius vectors turned 90 degrees in the direction of travel.
            cubic(path, m,
                    ax + k * (cy - ay) / ta, ay + k * (ax - cx) / ta,
                    bx - k * (cy - by) / tb, by - k * (bx - cx) / tb,
                    bx, by);
            cubic(path, m, bx + d, by + c, bx + d, by + b + c, p, p);
        }
        path.close();
    }

    private interface Mapper {
        float at(float x, float y, int axis);
    }

    private static void cubic(Path path, Mapper m, float x1, float y1, float x2, float y2, float x3, float y3) {
        path.cubicTo(m.at(x1, y1, 0), m.at(x1, y1, 1), m.at(x2, y2, 0), m.at(x2, y2, 1), m.at(x3, y3, 0), m.at(x3, y3, 1));
    }
}
