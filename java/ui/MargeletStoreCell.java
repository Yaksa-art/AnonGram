package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.margelet.MargeletStore;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Строка магазина: плашка с буквой, имя, подпись автора, стрелка вниз.
 *
 * Похожа на строку установленного плагина нарочно: это один и тот же предмет
 * в двух состояниях — «лежит в канале» и «стоит у меня». Разными их делает
 * только правый край: там либо переключатель, либо стрелка «принести».
 *
 * Значка у файла в канале нет — он внутри архива, а качать архив ради
 * картинки до того, как человек решил ставить, незачем. Поэтому плашка с
 * первой буквой, и цвет её вычислен из имени: при каждом открытии тот же.
 */
public class MargeletStoreCell extends FrameLayout implements Theme.Colorable {

    /** Те же цвета, что и у строк установленных плагинов. */
    private static final int[] COLORS = {
            0xFF4F85F6, 0xFF55CA47, 0xFFF09F1B, 0xFFF45255, 0xFF32C0CE,
            0xFFC46EF4, 0xFF8699AA, 0xFFE26314
    };

    private final TextView plateView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ImageView arrowView;

    public MargeletStoreCell(Context context) {
        super(context);

        plateView = new TextView(context);
        plateView.setGravity(Gravity.CENTER);
        plateView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        plateView.setTypeface(AndroidUtilities.bold());
        plateView.setTextColor(0xFFFFFFFF);
        addView(plateView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 68, 10, 56, 0));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 68, 32, 56, 0));

        arrowView = new ImageView(context);
        arrowView.setScaleType(ImageView.ScaleType.CENTER);
        arrowView.setImageResource(R.drawable.msg_download);
        addView(arrowView, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));

        updateColors();
    }

    @Override
    public void updateColors() {
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        arrowView.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueText),
                android.graphics.PorterDuff.Mode.SRC_IN));
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, android.view.View.MeasureSpec.makeMeasureSpec(dp(60),
                android.view.View.MeasureSpec.EXACTLY));
    }

    public void set(MargeletStore.Item item) {
        if (item == null) {
            return;
        }
        titleView.setText(item.name);
        subtitleView.setText(subtitleOf(item));
        final String letter = item.name.isEmpty() ? "?" : item.name.substring(0, 1).toUpperCase();
        plateView.setText(letter);
        plateView.setBackground(Theme.createRoundRectDrawable(dp(10), colorOf(item.name)));
    }

    /**
     * Что написать под именем.
     *
     * Подпись автора, если она есть, — она и есть описание. Нет подписи —
     * пишем размер и дату: сказать «нет описания» значит занять строку ничем.
     */
    private String subtitleOf(MargeletStore.Item item) {
        final String about = item.about == null ? "" : item.about.replace('\n', ' ').trim();
        if (!about.isEmpty()) {
            return about;
        }
        return AndroidUtilities.formatFileSize(item.size) + " · "
                + LocaleController.formatDateAudio(item.date, true);
    }

    /** Цвет плашки из имени: не случайный, значит всегда один и тот же. */
    private int colorOf(String name) {
        int hash = 0;
        for (int i = 0; i < name.length(); i++) {
            hash = hash * 31 + name.charAt(i);
        }
        return COLORS[Math.abs(hash) % COLORS.length];
    }

    public static class Factory extends UItem.UItemFactory<MargeletStoreCell> {
        static { setup(new Factory()); }

        @Override
        public MargeletStoreCell createView(Context context, RecyclerListView listView, int currentAccount,
                                            int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new MargeletStoreCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter,
                             UniversalRecyclerView listView) {
            ((MargeletStoreCell) view).set((MargeletStore.Item) item.object);
        }

        public static UItem of(int id, MargeletStore.Item item) {
            final UItem cell = UItem.ofFactory(Factory.class);
            cell.id = id;
            cell.object = item;
            return cell;
        }
    }
}
