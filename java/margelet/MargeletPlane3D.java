package org.telegram.margelet;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Самолётик Margelet, настоящий трёхмерный: сам крутится и крутится пальцем.
 *
 * Своя вьюшка, а не телеграмовская GLIcon: у той модель лежит в assets в своём
 * двоичном формате и жёстко привязана к типам «звезда», «монета», «алмаз».
 * Дописывать туда свой тип значило бы лезть в чужой renderer ради одной фигуры;
 * здесь же вся сцена — пять точек и восемь треугольников, и она целиком тут.
 *
 * Цвет задаётся вершинами, а не текстурой: у бумажного самолётика всего три
 * оттенка — две плоскости крыльев и ребро сгиба, — и текстура для этого лишняя.
 *
 * Владелец был прав, что трёхмерное тут возможно: в самом телеграме крутится
 * звезда премиума. Я до этого сказал, что «нечем нарисовать», не посмотрев.
 */
public class MargeletPlane3D extends GLSurfaceView {

    public MargeletPlane3D(Context context) {
        super(context);
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        renderer = new Renderer();
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    private final Renderer renderer;
    private float lastX;
    private boolean dragging;

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                dragging = true;
                renderer.spinning = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    // Полградуса на точку экрана: так вращение поспевает за
                    // пальцем и при этом не срывается в юлу от дрожи руки.
                    renderer.angle += (event.getX() - lastX) * 0.5f;
                    lastX = event.getX();
                }
                return true;
            default:
                dragging = false;
                renderer.spinning = true;
                return true;
        }
    }

    /** Пять точек: нос, два конца крыльев, ребро сгиба и хвост. */
    private static final float[] V = {
            0f, 1f, 0f,          // 0 нос
            -0.85f, -0.65f, -0.18f,  // 1 левый конец
            0.85f, -0.65f, -0.18f,   // 2 правый конец
            0f, -0.30f, 0.30f,       // 3 ребро сгиба
            0f, -0.78f, 0.10f        // 4 хвост
    };

    private static final int[][] FACES = {
            {0, 3, 1}, {0, 2, 3},   // верх крыльев
            {1, 3, 4}, {3, 2, 4}    // задние скосы
    };

    private static final float[][] COLORS = {
            {1f, 1f, 1f}, {0.93f, 0.95f, 0.98f},
            {0.86f, 0.89f, 0.95f}, {0.80f, 0.84f, 0.92f}
    };

    private static class Renderer implements GLSurfaceView.Renderer {

        volatile float angle;
        volatile boolean spinning = true;

        private FloatBuffer vertices;
        private FloatBuffer normals;
        private FloatBuffer colors;
        private int program;
        private int count;

        private final float[] mvp = new float[16];
        private final float[] model = new float[16];
        private final float[] view = new float[16];
        private final float[] projection = new float[16];
        private long lastFrame;

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVP;\n" +
                "uniform mat4 uModel;\n" +
                "attribute vec4 aPos;\n" +
                "attribute vec3 aNormal;\n" +
                "attribute vec3 aColor;\n" +
                "varying vec3 vColor;\n" +
                "varying vec3 vNormal;\n" +
                "void main() {\n" +
                "  vColor = aColor;\n" +
                "  vNormal = mat3(uModel) * aNormal;\n" +
                "  gl_Position = uMVP * aPos;\n" +
                "}";

        private static final String FRAGMENT_SHADER =
                "precision mediump float;\n" +
                "varying vec3 vColor;\n" +
                "varying vec3 vNormal;\n" +
                "void main() {\n" +
                // Свет сверху и чуть спереди, плюс подсветка снизу, чтобы
                // отвёрнутая сторона не проваливалась в чёрное.
                "  vec3 n = normalize(vNormal);\n" +
                "  float top = max(dot(n, normalize(vec3(0.3, 0.9, 0.5))), 0.0);\n" +
                "  float fill = max(dot(n, normalize(vec3(-0.3, -0.9, -0.5))), 0.0);\n" +
                "  float light = 0.45 + 0.55 * top + 0.18 * fill;\n" +
                "  gl_FragColor = vec4(vColor * light, 1.0);\n" +
                "}";

        private static int compile(int type, String source) {
            final int shader = GLES20.glCreateShader(type);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            return shader;
        }

        private static float[] normal(float[] a, float[] b, float[] c) {
            final float[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]};
            final float[] v = {c[0] - a[0], c[1] - a[1], c[2] - a[2]};
            final float[] n = {
                    u[1] * v[2] - u[2] * v[1],
                    u[2] * v[0] - u[0] * v[2],
                    u[0] * v[1] - u[1] * v[0]
            };
            final float len = (float) Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            if (len > 0) {
                n[0] /= len;
                n[1] /= len;
                n[2] /= len;
            }
            return n;
        }

        private void build() {
            // Каждый треугольник кладём дважды, вторым — с обратным обходом:
            // самолётик плоский, и без задних граней он исчезал бы наполовину
            // оборота.
            final int triangles = FACES.length * 2;
            count = triangles * 3;
            final float[] pos = new float[count * 3];
            final float[] nor = new float[count * 3];
            final float[] col = new float[count * 3];
            int p = 0, q = 0, r = 0;
            for (int side = 0; side < 2; side++) {
                for (int f = 0; f < FACES.length; f++) {
                    final int[] face = FACES[f];
                    final int i0 = face[0], i1 = side == 0 ? face[1] : face[2], i2 = side == 0 ? face[2] : face[1];
                    final float[] a = {V[i0 * 3], V[i0 * 3 + 1], V[i0 * 3 + 2]};
                    final float[] b = {V[i1 * 3], V[i1 * 3 + 1], V[i1 * 3 + 2]};
                    final float[] c = {V[i2 * 3], V[i2 * 3 + 1], V[i2 * 3 + 2]};
                    final float[] n = normal(a, b, c);
                    for (float[] vertex : new float[][]{a, b, c}) {
                        pos[p++] = vertex[0];
                        pos[p++] = vertex[1];
                        pos[p++] = vertex[2];
                        nor[q++] = n[0];
                        nor[q++] = n[1];
                        nor[q++] = n[2];
                        col[r++] = COLORS[f][0];
                        col[r++] = COLORS[f][1];
                        col[r++] = COLORS[f][2];
                    }
                }
            }
            vertices = buffer(pos);
            normals = buffer(nor);
            colors = buffer(col);
        }

        private static FloatBuffer buffer(float[] data) {
            final FloatBuffer b = ByteBuffer.allocateDirect(data.length * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            b.put(data).position(0);
            return b;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glClearColor(0f, 0f, 0f, 0f);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            build();
            program = GLES20.glCreateProgram();
            GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER));
            GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER));
            GLES20.glLinkProgram(program);
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            GLES20.glViewport(0, 0, width, height);
            final float ratio = height == 0 ? 1f : (float) width / height;
            Matrix.frustumM(projection, 0, -ratio * 0.6f, ratio * 0.6f, -0.6f, 0.6f, 1.6f, 8f);
            Matrix.setLookAtM(view, 0, 0f, 0.15f, 3.2f, 0f, 0f, 0f, 0f, 1f, 0f);
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            final long now = System.currentTimeMillis();
            if (lastFrame != 0 && spinning) {
                angle += (now - lastFrame) * 0.045f;
            }
            lastFrame = now;

            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glUseProgram(program);

            Matrix.setIdentityM(model, 0);
            Matrix.rotateM(model, 0, angle, 0f, 1f, 0f);
            Matrix.rotateM(model, 0, -14f, 1f, 0f, 0f);
            Matrix.multiplyMM(mvp, 0, view, 0, model, 0);
            Matrix.multiplyMM(mvp, 0, projection, 0, mvp, 0);

            final int pos = GLES20.glGetAttribLocation(program, "aPos");
            final int nor = GLES20.glGetAttribLocation(program, "aNormal");
            final int col = GLES20.glGetAttribLocation(program, "aColor");
            GLES20.glVertexAttribPointer(pos, 3, GLES20.GL_FLOAT, false, 0, vertices);
            GLES20.glVertexAttribPointer(nor, 3, GLES20.GL_FLOAT, false, 0, normals);
            GLES20.glVertexAttribPointer(col, 3, GLES20.GL_FLOAT, false, 0, colors);
            GLES20.glEnableVertexAttribArray(pos);
            GLES20.glEnableVertexAttribArray(nor);
            GLES20.glEnableVertexAttribArray(col);

            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVP"), 1, false, mvp, 0);
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uModel"), 1, false, model, 0);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, count);

            GLES20.glDisableVertexAttribArray(pos);
            GLES20.glDisableVertexAttribArray(nor);
            GLES20.glDisableVertexAttribArray(col);
        }
    }
}
