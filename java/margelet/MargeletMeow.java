package org.telegram.margelet;

import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

/**
 * Мяуканье на долгое нажатие по названию на главном экране.
 *
 * Обычный OnLongClickListener срабатывает через полсекунды — слишком быстро,
 * чтобы это читалось как «зажал». Поэтому свой отсчёт: полторы секунды
 * удержания, и отмена, если палец ушёл в сторону или отпустил. Сам звук
 * синтезирован, чужих записей в приложении нет.
 */
public class MargeletMeow {

    private static final long HOLD_MS = 1500;
    /** Разрешённый сдвиг пальца: дрожь рукой — не отмена, прокрутка — отмена. */
    private static final float SLOP_DP = 12;

    public static void attach(View view) {
        if (view == null) {
            return;
        }
        final Runnable play = () -> play(view);
        view.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        AndroidUtilities.runOnUIThread(play, HOLD_MS);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        float slop = AndroidUtilities.dp(SLOP_DP);
                        if (Math.abs(event.getX() - startX) > slop || Math.abs(event.getY() - startY) > slop) {
                            AndroidUtilities.cancelRunOnUIThread(play);
                        }
                        break;
                    default:
                        AndroidUtilities.cancelRunOnUIThread(play);
                        break;
                }
                // Событие не съедаем: нажатие по названию прокручивает список
                // к началу, и ломать это ради шутки не стоит.
                return false;
            }
        });
    }

    private static void play(View view) {
        try {
            MediaPlayer player = MediaPlayer.create(view.getContext(), R.raw.margelet_meow);
            if (player == null) {
                return;
            }
            player.setOnCompletionListener(MediaPlayer::release);
            player.start();
        } catch (Exception ignored) {
            // Звук — украшение. Если система его не дала, экран должен жить дальше.
        }
    }
}
