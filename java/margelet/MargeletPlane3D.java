package org.telegram.margelet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Иконка Margelet в объёме: тот же скруглённый квадрат с самолётиком, только
 * толстый. Сам крутится, можно крутить пальцем.
 *
 * Первая версия была другой — я слепил из самолётика отдельную фигуру в
 * пространстве. Владелец сказал коротко и по делу: незачем, возьми готовый знак
 * и сделай его толстым. Он прав, знак у форка один.
 *
 * Считается на процессоре обычным холстом, без OpenGL. Причина простая: GL-
 * поверхность живёт отдельным слоем и лезет поверх соседей, а этот значок нужен
 * и в диалоге, и в шапке списка, по которому ездят строки. Треугольников тут
 * меньше сотни — рисовать их кистью дешевле, чем разбираться с порядком слоёв.
 *
 * Тело выпуклое, поэтому весь «трёхмерный движок» помещается в две вещи:
 * отбросить грани, отвёрнутые от зрителя, и рисовать оставшиеся от дальних к
 * ближним. Наклейки самолёта приподняты над гранью и потому ложатся поверх неё.
 */
public class MargeletPlane3D extends View {

    /** Половина толщины плашки: знак должен читаться и с ребра. */
    private static final float HALF_DEPTH = 0.15f;
    private static final float HALF_SIZE = 1.0f;
    private static final float CORNER = 0.24f;
    private static final int CORNER_STEPS = 8;
    private static final float CAM_Z = 3.4f;
    private static final float TILT = -10f;

    private static final int GREEN = 0xFF8DD1B0;
    private static final int GREEN_SIDE = 0xFF7BC0A0;
    private static final int WING_LEFT = 0xFFFFFFFF;
    private static final int WING_RIGHT = 0xFFEEF3FA;
    private static final int KEEL = 0xFFCCD5E9;

    private static final float[] LIGHT = normalize(0.35f, 0.8f, 0.6f);

    /** Кусок поверхности: замкнутый многоугольник, своя нормаль, свой цвет. */
    private static final class Piece {
        final float[][] points;
        final float[] normal;
        final int color;
        float depth;

        Piece(float[][] points, float[] normal, int color) {
            this.points = points;
            this.normal = normal;
            this.color = color;
        }
    }

    private final ArrayList<Piece> pieces = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    private float angle;
    private long lastFrame;
    private boolean spinning = true;
    private float lastX;
    private boolean dragging;

    public MargeletPlane3D(Context context) {
        super(context);
        // Заливка со швом в полпикселя: соседние грани одного цвета иначе
        // расходятся волоском от сглаживания.
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(1f);
        build();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                dragging = true;
                spinning = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    // Полградуса на точку экрана: вращение поспевает за пальцем
                    // и не срывается в юлу от дрожи руки.
                    angle += (event.getX() - lastX) * 0.5f;
                    lastX = event.getX();
                }
                return true;
            default:
                dragging = false;
                spinning = true;
                return true;
        }
    }

    // ------------------------------------------------------------------ сборка

    /** Контур скруглённого квадрата, против часовой стрелки. */
    private static ArrayList<float[]> outline() {
        final ArrayList<float[]> points = new ArrayList<>();
        final float s = HALF_SIZE - CORNER;
        final float[][] centers = {{s, s}, {-s, s}, {-s, -s}, {s, -s}};
        final float[] starts = {0f, 90f, 180f, 270f};
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i <= CORNER_STEPS; i++) {
                final double a = Math.toRadians(starts[c] + 90.0 * i / CORNER_STEPS);
                points.add(new float[]{
                        centers[c][0] + (float) Math.cos(a) * CORNER,
                        centers[c][1] + (float) Math.sin(a) * CORNER
                });
            }
        }
        return points;
    }

    private void build() {
        pieces.clear();
        final ArrayList<float[]> ring = outline();
        final int n = ring.size();

        // Лицевая грань одним многоугольником, изнанка — тем же контуром в
        // обратную сторону.
        final float[][] front = new float[n][];
        final float[][] back = new float[n][];
        for (int i = 0; i < n; i++) {
            final float[] p = ring.get(i);
            front[i] = new float[]{p[0], p[1], HALF_DEPTH};
            back[n - 1 - i] = new float[]{p[0], p[1], -HALF_DEPTH};
        }
        pieces.add(new Piece(front, new float[]{0, 0, 1}, GREEN));
        pieces.add(new Piece(back, new float[]{0, 0, -1}, GREEN));

        // Рёбра: по четырёхугольнику на отрезок контура. Своя нормаль наружу —
        // она и делает толщину видимой.
        for (int i = 0; i < n; i++) {
            final float[] p1 = ring.get(i);
            final float[] p2 = ring.get((i + 1) % n);
            final float dx = p2[0] - p1[0], dy = p2[1] - p1[1];
            final float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len == 0) {
                continue;
            }
            final float[] normal = {dy / len, -dx / len, 0};
            pieces.add(new Piece(new float[][]{
                    {p1[0], p1[1], HALF_DEPTH},
                    {p2[0], p2[1], HALF_DEPTH},
                    {p2[0], p2[1], -HALF_DEPTH},
                    {p1[0], p1[1], -HALF_DEPTH}
            }, normal, GREEN_SIDE));
        }

        // Самолётик на обеих гранях, приподнятый над поверхностью.
        addPlane(HALF_DEPTH + 0.004f, new float[]{0, 0, 1}, false);
        addPlane(-HALF_DEPTH - 0.004f, new float[]{0, 0, -1}, true);
    }

    /** Те же пропорции, что у плоской иконки: подъём центра на три процента. */
    private void addPlane(float z, float[] normal, boolean mirror) {
        final float k = mirror ? -1f : 1f;
        final float up = 0.06f;
        final float[] nose = {0f, 0.56f + up, z};
        final float[] left = {-0.52f * k, -0.30f + up, z};
        final float[] right = {0.52f * k, -0.30f + up, z};
        final float[] keelL = {-0.04f * k, -0.14f + up, z};
        final float[] keelR = {0.04f * k, -0.14f + up, z};
        final float[] tail = {0f, -0.24f + up, z};

        // Крылья и ребро сгиба разной светлоты — иначе плоскости сливаются.
        pieces.add(new Piece(new float[][]{nose, left, keelL}, normal,
                mirror ? WING_RIGHT : WING_LEFT));
        pieces.add(new Piece(new float[][]{nose, keelR, right}, normal,
                mirror ? WING_LEFT : WING_RIGHT));
        pieces.add(new Piece(new float[][]{nose, keelL, tail, keelR}, normal, KEEL));
    }

    // ------------------------------------------------------------------ рисунок

    private static float[] normalize(float x, float y, float z) {
        final float len = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / len, y / len, z / len};
    }

    private final float[] tmp = new float[3];

    /** Поворот вокруг вертикали, затем небольшой наклон к зрителю. */
    private void rotate(float x, float y, float z, float sinA, float cosA,
                        float sinT, float cosT, float[] out) {
        final float rx = x * cosA + z * sinA;
        final float rz = -x * sinA + z * cosA;
        out[0] = rx;
        out[1] = y * cosT - rz * sinT;
        out[2] = y * sinT + rz * cosT;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final long now = System.currentTimeMillis();
        if (lastFrame != 0 && spinning) {
            angle += (now - lastFrame) * 0.04f;
        }
        lastFrame = now;
        if (angle > 360f) {
            angle -= 360f;
        }

        final int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) {
            return;
        }
        final float cx = w / 2f, cy = h / 2f;
        // Полкадра на половину знака с запасом на толщину при развороте боком.
        final float focal = CAM_Z * Math.min(w, h) * 0.40f;

        final double a = Math.toRadians(angle), t = Math.toRadians(TILT);
        final float sinA = (float) Math.sin(a), cosA = (float) Math.cos(a);
        final float sinT = (float) Math.sin(t), cosT = (float) Math.cos(t);

        final ArrayList<Piece> visible = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            final Piece piece = pieces.get(i);
            rotate(piece.normal[0], piece.normal[1], piece.normal[2],
                    sinA, cosA, sinT, cosT, tmp);
            final float nx = tmp[0], ny = tmp[1], nz = tmp[2];

            float sumZ = 0f;
            float toCamera = 0f;
            for (float[] p : piece.points) {
                rotate(p[0], p[1], p[2], sinA, cosA, sinT, cosT, tmp);
                sumZ += tmp[2];
                toCamera += nx * -tmp[0] + ny * -tmp[1] + nz * (CAM_Z - tmp[2]);
            }
            if (toCamera <= 0) {
                continue;   // грань отвёрнута от зрителя
            }
            piece.depth = sumZ / piece.points.length;
            visible.add(piece);
        }

        // От дальних к ближним. Тело выпуклое, наклейки лежат на гранях —
        // этого порядка достаточно, глубина попиксельно не нужна.
        Collections.sort(visible, (p1, p2) -> Float.compare(p1.depth, p2.depth));

        for (int i = 0; i < visible.size(); i++) {
            final Piece piece = visible.get(i);
            rotate(piece.normal[0], piece.normal[1], piece.normal[2],
                    sinA, cosA, sinT, cosT, tmp);
            paint.setColor(shade(piece.color, tmp[0], tmp[1], tmp[2]));

            path.reset();
            for (int j = 0; j < piece.points.length; j++) {
                final float[] p = piece.points[j];
                rotate(p[0], p[1], p[2], sinA, cosA, sinT, cosT, tmp);
                final float denom = Math.max(CAM_Z - tmp[2], 0.1f);
                final float sx = cx + tmp[0] * focal / denom;
                final float sy = cy - tmp[1] * focal / denom;
                if (j == 0) {
                    path.moveTo(sx, sy);
                } else {
                    path.lineTo(sx, sy);
                }
            }
            path.close();
            canvas.drawPath(path, paint);
        }

        postInvalidateOnAnimation();
    }

    /** Свет сверху-спереди плюс мягкая подсветка снизу: изнанка не чернеет. */
    private static int shade(int color, float nx, float ny, float nz) {
        final float top = Math.max(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2], 0f);
        final float fill = Math.max(-(nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]), 0f);
        final float light = 0.62f + 0.38f * top + 0.12f * fill;
        return Color.argb(Color.alpha(color),
                clamp(Color.red(color) * light),
                clamp(Color.green(color) * light),
                clamp(Color.blue(color) * light));
    }

    private static int clamp(float value) {
        return value < 0 ? 0 : (value > 255 ? 255 : (int) value);
    }
}
