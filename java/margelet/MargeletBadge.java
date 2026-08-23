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

import java.util.ArrayList;
import java.util.List;

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
 *
 * Значков у одного человека может быть несколько. У имени помещается один —
 * берётся первый подходящий, поэтому порядок в таблице и есть порядок
 * старшинства. В профиле показываются все.
 *
 * Ключ таблицы — не только человек. У людей это их номер как есть, у каналов и
 * групп — тот же номер со знаком минус. Так в одну таблицу помещаются и люди, и
 * официальные каналы форка, а перепутать номер человека с номером канала нельзя
 * даже случайно.
 */
public class MargeletBadge {

    /** Один значок: кому, как называется, каким цветом и куда ведёт кнопка. */
    public static final class Badge {
        /** Человек — положительный номер, канал или группа — отрицательный. */
        public final long peerId;
        public final int title;
        public final int about;
        public final int icon;
        /** Цвет поля — им же красится объёмный значок в окне. */
        public final int color;
        /** Куда ведёт кнопка в окне. null — кнопки нет. */
        public final String url;

        Badge(long peerId, int title, int about, int icon, int color, String url) {
            this.peerId = peerId;
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
            // Свои площадки форка. Значок тут не украшение, а ответ на вопрос
            // «а это точно тот самый канал»: подделать чужой значок внутри
            // чужой сборки можно, а вот в этой — нет.
            new Badge(-4426743212L, R.string.MargeletBadgeChannelTitle, R.string.MargeletBadgeChannelAbout,
                    R.drawable.margelet_badge, 0xFF8DD1B0, MargeletConfig.CHANNEL_URL),
            new Badge(-4436273526L, R.string.MargeletBadgeForumTitle, R.string.MargeletBadgeForumAbout,
                    R.drawable.margelet_badge, 0xFF8DD1B0, MargeletConfig.FORUM_URL),
            // Чьи коты живут в приложении. Кнопки у этого значка нет: вести
            // некуда, он не про площадку, а про кота.
            new Badge(7826361017L, R.string.MargeletBadgeCatTitle, R.string.MargeletBadgeCatAbout,
                    R.drawable.margelet_badge_yellow, 0xFFEBC85C, null),
            new Badge(6092720414L, R.string.MargeletBadgeCatTitle, R.string.MargeletBadgeCatAbout,
                    R.drawable.margelet_badge_yellow, 0xFFEBC85C, null),
    };

    /** Номер канала или группы в том виде, в каком он лежит в таблице. */
    public static long chatPeer(long chatId) {
        return -chatId;
    }

    /**
     * Виды значков — по одному на вид, а не по одному на человека.
     *
     * В таблице «кот в Margelet» стоит дважды: котов двое, у каждого свой
     * хозяин. Витрине это не нужно — она показывает, какие значки бывают, и
     * два одинаковых «Кот в Margelet» там выглядят ошибкой. Ею и были.
     */
    public static Badge[] list() {
        final List<Badge> kinds = new ArrayList<>();
        for (Badge badge : BADGES) {
            boolean seen = false;
            for (Badge already : kinds) {
                if (already.title == badge.title) {
                    seen = true;
                    break;
                }
            }
            if (!seen) {
                kinds.add(badge);
            }
        }
        return kinds.toArray(new Badge[0]);
    }

    /** Старший значок — тот, что стоит у имени. Первый в таблице и есть старший. */
    public static Badge of(long peerId) {
        if (!MargeletConfig.badgesEnabled()) {
            return null;
        }
        for (Badge badge : BADGES) {
            if (badge.peerId == peerId) {
                return badge;
            }
        }
        return null;
    }

    /** Все значки этого человека или чата, по старшинству. */
    public static List<Badge> all(long peerId) {
        final List<Badge> found = new ArrayList<>();
        if (!MargeletConfig.badgesEnabled()) {
            return found;
        }
        for (Badge badge : BADGES) {
            if (badge.peerId == peerId) {
                found.add(badge);
            }
        }
        return found;
    }

    public static boolean has(long peerId) {
        return of(peerId) != null;
    }

    /** Ресурс значка у имени или ноль, если такого в таблице нет. */
    public static int icon(long peerId) {
        final Badge badge = of(peerId);
        return badge == null ? 0 : badge.icon;
    }

    /**
     * Название значка. Отдаётся строкой, а не CharSequence: в профиле оно
     * ложится в поле описания для озвучки, а там объявлен String.
     */
    public static String title(long peerId) {
        final Badge badge = of(peerId);
        return badge == null ? null : LocaleController.getString(badge.title);
    }

    public static void show(Context context, long peerId) {
        show(context, of(peerId));
    }

    public static void show(Context context, Badge badge) {
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
            layout.addView(spinner, LayoutHelper.createLinear(150, 150, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

            final TextView text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            text.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            text.setGravity(Gravity.CENTER);
            text.setText(LocaleController.getString(badge.about));
            layout.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 8, 0, 8, 0));

            final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(LocaleController.getString(badge.title))
                    .setView(layout);
            if (badge.url != null) {
                builder.setPositiveButton(LocaleController.getString(R.string.MargeletBadgeChannel),
                        (d, w) -> Browser.openUrl(context, badge.url));
            }
            builder.setNegativeButton(LocaleController.getString(R.string.Close), null).show();
        } catch (Exception ignored) {
            // Украшение не повод ронять профиль.
        }
    }
}
