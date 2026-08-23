package org.telegram.margelet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Значок форка у имени создателя.
 *
 * Список один и короткий: значок принадлежит тому, кто это всё придумал.
 * Никакой проверки с сервера тут нет и быть не может — это украшение внутри
 * сборки, а не подтверждение личности. Кто соберёт свой форк, поставит свой
 * номер, и это нормально: значок ничего не удостоверяет.
 */
public class MargeletBadge {

    /** Владелец форка. */
    private static final long CREATOR_ID = 7826361017L;

    private static final String INFO_CHANNEL = "https://t.me/narezanyinf";

    public static boolean isCreator(long userId) {
        return userId == CREATOR_ID;
    }

    public static void show(Context context) {
        if (context == null) {
            return;
        }
        try {
            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);

            // Трёхмерный самолётик: сам крутится, можно крутить пальцем.
            // Если по какой-то причине не заведётся — покажем плоский значок,
            // окно не должно превращаться в чёрный квадрат.
            View spinner;
            try {
                spinner = new MargeletPlane3D(context);
            } catch (Throwable t) {
                final ImageView icon = new ImageView(context);
                icon.setImageResource(R.drawable.margelet_badge);
                final RotateAnimation spin = new RotateAnimation(0, 360,
                        Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                spin.setDuration(2600);
                spin.setRepeatCount(Animation.INFINITE);
                spin.setInterpolator(new LinearInterpolator());
                icon.startAnimation(spin);
                spinner = icon;
            }
            layout.addView(spinner, LayoutHelper.createLinear(120, 120, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 14));

            final TextView text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            text.setGravity(Gravity.CENTER);
            text.setText(LocaleController.getString(R.string.MargeletBadgeAbout));
            layout.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 0));

            new AlertDialog.Builder(context)
                    .setTitle(LocaleController.getString(R.string.MargeletBadgeTitle))
                    .setView(layout)
                    .setPositiveButton(LocaleController.getString(R.string.MargeletBadgeChannel),
                            (d, w) -> Browser.openUrl(context, INFO_CHANNEL))
                    .setNegativeButton(LocaleController.getString(R.string.Close), null)
                    .show();
        } catch (Exception ignored) {
            // Украшение не повод ронять профиль.
        }
    }
}
