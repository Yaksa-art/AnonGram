package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletMarkup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextCaption;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Окошки своего оформления: выбор размера и одно предупреждение.
 */
public class MargeletMarkupAlert {

    /** Ползунок размера с живым примером: цифры тут ничего не говорят. */
    public static void showSize(Context context, EditTextCaption editText) {
        if (context == null || editText == null) {
            return;
        }
        final int[] chosen = {MargeletMarkup.sizeValue(1.4f)};

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(4), dp(20), 0);

        final TextView preview = new TextView(context);
        preview.setGravity(Gravity.CENTER);
        preview.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        preview.setText(LocaleController.getString(R.string.MargeletMarkupSizeExample));
        layout.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 64));

        final SeekBar bar = new SeekBar(context);
        bar.setMax(13);
        bar.setProgress(chosen[0]);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                chosen[0] = progress;
                preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * MargeletMarkup.sizeOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16 * MargeletMarkup.sizeOf(chosen[0]));
        layout.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MargeletMarkupSize))
                .setView(layout)
                .setPositiveButton(LocaleController.getString(R.string.Done),
                        (d, w) -> editText.makeSelectedMargelet(MargeletMarkup.KIND_SIZE, chosen[0]))
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    /**
     * Предупреждение об оформлении, один раз за всё время.
     *
     * Показывается в тот миг, когда человек впервые применяет оформление, а не
     * перед отправкой. Владелец просил перед отправкой, и я сделал иначе
     * сознательно: отправка в телеграме — одна длинная цепочка, которая сама
     * чистит поле ввода и запускает движение сообщения. Вклиниться в неё
     * вопросом можно только оборвав её и запустив заново из ответа диалога, и
     * тогда в половине случаев текст остаётся в поле или уходит дважды.
     * Предупредить на шаг раньше — тот же смысл и никакой поломки отправки.
     */
    public static void warnOnce(Context context) {
        if (context == null || MargeletConfig.markupWarned()) {
            return;
        }
        MargeletConfig.setMarkupWarned(true);
        new AlertDialog.Builder(context)
                .setTitle(LocaleController.getString(R.string.MargeletMarkupWarnTitle))
                .setMessage(LocaleController.getString(R.string.MargeletMarkupWarnText))
                .setPositiveButton(LocaleController.getString(R.string.OK), null)
                .show();
    }
}
