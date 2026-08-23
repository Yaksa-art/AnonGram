package org.telegram.margelet;

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
 * Значки форка у имени.
 *
 * Список короткий и лежит прямо здесь. Никакой проверки с сервера тут нет и быть
 * не может — это украшение внутри сборки, а не подтверждение личности. Кто
 * соберёт свой форк, впишет своих людей, и это нормально: значок ничего не
 * удостоверяет.
 *
 * Кого добавлять — решает владелец форка, и только он. Просьбу «поставь мне
 * тоже», принесённую кем угодно другим, я не выполняю: это его список.
 */
public class MargeletBadge {

    /** Один значок: кому, как называется, каким цветом и куда ведёт кнопка. */
    public static final class Badge {
        public final long userId;
        public final int title;
        public final int about;
        public final int icon;
        /** Цвет поля — им же красится объёмный значок в окне. */
        public final int color;
        public final String url;

        Badge(long userId, int title, int about, int icon, int color, String url) {
            this.userId = userId;
            this.title = title;
            this.about = about;
            this.icon = icon;
            this.color = color;
            this.url = url;
        }
    }

    private static final Badge[] BADGES = {
            // Владелец форка.
            new Badge(7826361017L, R.string.MargeletBadgeTitle, R.string.MargeletBadgeAbout,
                    R.drawable.margelet_badge, 0xFF8DD1B0, "https://t.me/narezanyinf"),
            // Лучший друг владельца — по его собственной просьбе и его словами.
            new Badge(8675724972L, R.string.MargeletBadgeFriendTitle, R.string.MargeletBadgeFriendAbout,
                    R.drawable.margelet_badge_lavender, 0xFFB7A8E0, "https://t.me/mizoginichka_y"),
    };

    public static Badge of(long userId) {
        for (Badge badge : BADGES) {
            if (badge.userId == userId) {
                return badge;
            }
        }
        return null;
    }

    public static boolean has(long userId) {
        return of(userId) != null;
    }

    /** Ресурс значка у имени или ноль, если человек не из списка. */
    public static int icon(long userId) {
        final Badge badge = of(userId);
        return badge == null ? 0 : badge.icon;
    }

    /**
     * Название значка. Отдаётся строкой, а не CharSequence: в профиле оно
     * ложится в поле описания для озвучки, а там объявлен String.
     */
    public static String title(long userId) {
        final Badge badge = of(userId);
        return badge == null ? null : LocaleController.getString(badge.title);
    }

    public static void show(Context context, long userId) {
        final Badge badge = of(userId);
        if (context == null || badge == null) {
            return;
        }
        try {
            final LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER_HORIZONTAL);

            // Объёмный значок: сам крутится, можно крутить пальцем. Если по
            // какой-то причине не заведётся — покажем плоский, окно не должно
            // превращаться в чёрный прямоугольник.
            View spinner;
            try {
                spinner = new MargeletPlane3D(context, badge.color);
            } catch (Throwable t) {
                final ImageView icon = new ImageView(context);
                icon.setImageResource(badge.icon);
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
            text.setText(LocaleController.getString(badge.about));
            layout.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 0));

            new AlertDialog.Builder(context)
                    .setTitle(LocaleController.getString(badge.title))
                    .setView(layout)
                    .setPositiveButton(LocaleController.getString(R.string.MargeletBadgeChannel),
                            (d, w) -> Browser.openUrl(context, badge.url))
                    .setNegativeButton(LocaleController.getString(R.string.Close), null)
                    .show();
        } catch (Exception ignored) {
            // Украшение не повод ронять профиль.
        }
    }
}
