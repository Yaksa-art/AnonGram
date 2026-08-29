package org.telegram.margelet;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.LayoutHelper;

import java.net.URLEncoder;

/**
 * Кошка на весь экран, если долбить по вкладке «Чаты».
 *
 * Считаем нажатия подряд: семь штук, между соседними не больше секунды. Порог
 * такой, чтобы обычный человек, переключающий вкладки, на него не наткнулся, а
 * тот, кто именно долбит, — наткнулся сразу.
 *
 * Отсюда же две меры против собственного открытия. Подпись висит сверху, а не
 * снизу: снизу ровно то место, по которому человек только что долбил пальцем.
 * И первые полсекунды окно нажатий не принимает вовсе — палец после седьмого
 * тапа успевает опуститься восьмой раз, и без этой задержки кошка закрывалась
 * или уводила в переписку с владельцем раньше, чем её успевали увидеть.
 */
public class MargeletCats {

    private static final int TAPS_NEEDED = 7;
    private static final long GAP_MS = 1000;

    /**
     * Вшитые кошки — запас на случай, когда список с гитхаба ещё не приехал.
     *
     * Раньше список жил только здесь, и чтобы добавить кота, приходилось
     * выпускать новую сборку. Теперь он лежит рядом со значками, в cats.json,
     * и пополняется без обновления клиента.
     */
    private static final int[] PHOTOS = {R.drawable.margelet_cat_1, R.drawable.margelet_cat_2};
    private static final int[] NAMES = {R.string.MargeletCatOne, R.string.MargeletCatTwo};
    /** Кто принёс кота. Порядок тот же, что у фотографий. */
    private static final String[] FROM = {"@narezany", "@egorkagds"};

    private static final String FILE = "cats.json";
    private static final String CACHE_KEY = "cats";
    /** Как часто перечитывать список. Кошки не новости, десяти минут хватит. */
    private static final long REFRESH_MS = 10 * 60 * 1000L;

    /** Один кот из списка: где картинка, как зовут и кто принёс. */
    public static final class Cat {
        public final String photo;
        public final String name;
        public final String from;

        Cat(String photo, String name, String from) {
            this.photo = photo;
            this.name = name;
            this.from = from;
        }
    }

    /**
     * Перечитать список, если он старее десяти минут.
     *
     * Ответа никто не ждёт: пока он едет, показывается прошлый список, а на
     * свежей установке — вшитый. Пасхалка не то, ради чего человек должен
     * смотреть на крутилку.
     */
    public static void refresh() {
        MargeletRemote.refreshIfOlder(FILE, CACHE_KEY, REFRESH_MS, text -> warm());
        warm();
    }

    /**
     * Заранее принести картинки котов из списка.
     *
     * Кот показывается, только когда его снимок уже на диске, — иначе подпись
     * пришлось бы вешать на чужую фотографию. Значит, ждать первого показа,
     * чтобы начать качать, нельзя: первый показ тогда всегда достаётся вшитым,
     * и новый кот не появится никогда, сколько бы его ни открывали.
     *
     * Уже скачанное {@link MargeletRemote#image} отдаёт сразу и в сеть не идёт,
     * поэтому звать это можно спокойно.
     */
    private static void warm() {
        for (Cat cat : remote()) {
            MargeletRemote.image(cat.photo, file -> { });
        }
    }

    /**
     * Кот, которого можно показать целиком — со своим снимком и своей кличкой.
     *
     * Пусто, если список ещё не приехал или ни одного снимка на диске нет.
     * Тогда показываются вшитые коты, у которых снимок и подпись лежат рядом в
     * сборке и разъехаться не могут.
     */
    private static Cat pick() {
        final java.util.List<Cat> ready = new java.util.ArrayList<>();
        for (Cat cat : remote()) {
            if (MargeletRemote.cachedImage(cat.photo) != null) {
                ready.add(cat);
            }
        }
        if (ready.isEmpty()) {
            return null;
        }
        return ready.get((int) (Math.random() * ready.size()));
    }

    /** Список с гитхаба или пусто, если его ещё нет. */
    private static java.util.List<Cat> remote() {
        final java.util.List<Cat> out = new java.util.ArrayList<>();
        final String text = MargeletRemote.cached(CACHE_KEY);
        if (text == null) {
            return out;
        }
        try {
            final org.json.JSONArray array = new org.json.JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                final org.json.JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                final String photo = item.optString("photo", "");
                if (photo.isEmpty()) {
                    continue;   // кот без картинки — не кот
                }
                out.add(new Cat(photo,
                        MargeletRemote.localized(item, "name", ""),
                        item.optString("from", "")));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return out;
    }

    private static final String OWNER = "narezany";

    /** Сколько окно не принимает нажатий после открытия. */
    private static final long DEAF_MS = 500;

    private static int taps;
    private static long lastTap;

    public static void tap(Activity activity) {
        final long now = System.currentTimeMillis();
        taps = (now - lastTap > GAP_MS) ? 1 : taps + 1;
        lastTap = now;
        if (taps >= TAPS_NEEDED) {
            taps = 0;
            show(activity);
        }
    }

    private static void show(Activity activity) {
        if (activity == null) {
            return;
        }
        try {
            // Бросок кубика ровно один. Раньше их было два — отдельно кот из
            // списка, отдельно вшитая картинка, — и подпись доставалась одному
            // коту, а картинка другому. Владелец увидел под фотографией кота
            // своего друга чужую кличку, и был прав: имя, которое не
            // принадлежит тому, кто на снимке, — не украшение, а враньё.
            final Cat cat = pick();
            // Кот из списка берётся, только когда его картинка уже на диске,
            // поэтому она здесь есть наверняка.
            final java.io.File ready = cat == null ? null : MargeletRemote.cachedImage(cat.photo);
            final int index = (int) (Math.random() * PHOTOS.length);
            final long openedAt = System.currentTimeMillis();

            final FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(0xFF000000);

            final ImageView photo = new ImageView(activity);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            boolean shown = false;
            if (ready != null) {
                try {
                    final android.graphics.Bitmap bitmap =
                            android.graphics.BitmapFactory.decodeFile(ready.getAbsolutePath());
                    if (bitmap != null) {
                        photo.setImageBitmap(bitmap);
                        shown = true;
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }
            // Картинка на диске оказалась битой — показываем вшитого кота с
            // его же подписью, а не чужую кличку поверх запасной фотографии.
            final boolean fromList = shown;
            if (!shown) {
                photo.setImageResource(PHOTOS[index]);
            }
            root.addView(photo, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            final LinearLayout caption = new LinearLayout(activity);
            caption.setOrientation(LinearLayout.VERTICAL);
            caption.setBackgroundColor(0xB0000000);
            caption.setPadding(dp(20), dp(16) + AndroidUtilities.statusBarHeight, dp(20), dp(18));

            final TextView name = new TextView(activity);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Color.WHITE);
            name.setText(fromList && !cat.name.isEmpty()
                    ? cat.name : LocaleController.getString(NAMES[index]));
            caption.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final TextView from = new TextView(activity);
            from.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            from.setTextColor(0xB3FFFFFF);
            from.setText(LocaleController.formatString(R.string.MargeletCatFrom,
                    fromList && !cat.from.isEmpty() ? cat.from : FROM[index]));
            from.setPadding(0, dp(4), 0, 0);
            caption.addView(from, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final TextView invite = new TextView(activity);
            invite.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            invite.setTextColor(0xFF8DD1B0);
            invite.setText(LocaleController.getString(R.string.MargeletCatAddYours));
            invite.setPadding(0, dp(10), 0, 0);
            invite.setOnClickListener(v -> {
                if (System.currentTimeMillis() - openedAt < DEAF_MS) {
                    return;
                }
                writeToOwner(activity);
            });
            caption.addView(invite, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(caption, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            final Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
            // По кошке — закрыть. По надписи сверху — написать владельцу; она
            // ловит нажатие сама и до этого обработчика не доходит.
            photo.setOnClickListener(v -> {
                if (System.currentTimeMillis() - openedAt < DEAF_MS) {
                    return;
                }
                dialog.dismiss();
            });
            dialog.show();
        } catch (Exception ignored) {
            // Пасхалка не то, ради чего можно ронять приложение.
        }
    }

    /** Открывает переписку с владельцем и подставляет заготовку сообщения. */
    private static void writeToOwner(Activity activity) {
        try {
            final String text = LocaleController.getString(R.string.MargeletCatAskText)
                    + "…" + LocaleController.getString(R.string.MargeletCatAskTail);
            Browser.openUrl(activity, "https://t.me/" + OWNER + "?text="
                    + URLEncoder.encode(text, "UTF-8"));
        } catch (Exception ignored) {
        }
    }
}
