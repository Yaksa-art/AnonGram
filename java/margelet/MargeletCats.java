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
 */
public class MargeletCats {

    private static final int TAPS_NEEDED = 7;
    private static final long GAP_MS = 1000;

    /** Кошки. Добавить свою может кто угодно — через владельца, см. ссылку внизу. */
    private static final int[] PHOTOS = {R.drawable.margelet_cat_1, R.drawable.margelet_cat_2};
    private static final int[] NAMES = {R.string.MargeletCatOne, R.string.MargeletCatTwo};

    private static final String OWNER = "narezany";

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
            final int index = (int) (Math.random() * PHOTOS.length);

            final FrameLayout root = new FrameLayout(activity);
            root.setBackgroundColor(0xFF000000);

            final ImageView photo = new ImageView(activity);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageResource(PHOTOS[index]);
            root.addView(photo, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            final LinearLayout bottom = new LinearLayout(activity);
            bottom.setOrientation(LinearLayout.VERTICAL);
            bottom.setBackgroundColor(0xB0000000);
            bottom.setPadding(dp(20), dp(16), dp(20), dp(24));

            final TextView name = new TextView(activity);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            name.setTypeface(AndroidUtilities.bold());
            name.setTextColor(Color.WHITE);
            name.setText(LocaleController.getString(NAMES[index]));
            bottom.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            final TextView invite = new TextView(activity);
            invite.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            invite.setTextColor(0xFF8DD1B0);
            invite.setText(LocaleController.getString(R.string.MargeletCatAddYours));
            invite.setPadding(0, dp(10), 0, 0);
            invite.setOnClickListener(v -> writeToOwner(activity));
            bottom.addView(invite, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(bottom, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

            final Dialog dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.setContentView(root, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            }
            // По кошке — закрыть. По надписи снизу — написать владельцу; она
            // ловит нажатие сама и до этого обработчика не доходит.
            photo.setOnClickListener(v -> dialog.dismiss());
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
