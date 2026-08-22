package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/** Ветка «Поле ввода»: сколько строк, какой размер текста, где оно стоит. */
public class MargeletInputActivity extends UniversalFragment {

    /** Значения ползунка строк. Ноль — «сколько влезет на экран». */
    private static final int[] LINES = {2, 3, 4, 5, 6, 8, 10, 15, 0};
    private static final int ID_TOP = 1;

    private static final int[] SIZES = {14, 15, 16, 17, 18, 19, 20, 22, 24};

    private static int indexOf(int[] arr, int value, int fallback) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == value) {
                return i;
            }
        }
        return fallback;
    }

    @Override
    protected CharSequence getTitle() {
        return "Поле ввода";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("Сколько строк"));
        String[] lines = new String[LINES.length];
        for (int i = 0; i < LINES.length; i++) {
            lines[i] = LINES[i] == 0 ? "макс." : String.valueOf(LINES[i]);
        }
        items.add(UItem.asSlideView(lines, indexOf(LINES, MargeletConfig.inputMaxLinesRaw(), 4),
                i -> MargeletConfig.setInputMaxLines(LINES[i])));
        items.add(UItem.asShadow("До скольких строк поле растёт, прежде чем начать прокручиваться. "
                + "В оригинале их шесть. «Макс.» — расти, пока есть место на экране.\n\n"
                + "Предел в 4096 знаков на сообщение остаётся: его держит сервер, а не приложение."));

        items.add(UItem.asHeader("Размер текста"));
        String[] sizes = new String[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            sizes[i] = String.valueOf(SIZES[i]);
        }
        items.add(UItem.asSlideView(sizes, indexOf(SIZES, Math.round(MargeletConfig.inputTextSize()), 4),
                i -> MargeletConfig.setInputTextSize(SIZES[i])));
        items.add(UItem.asShadow("Применяется к следующему открытому чату."));

        items.add(UItem.asCheck(ID_TOP, "Поле ввода сверху").setChecked(MargeletConfig.inputOnTop()));
        items.add(UItem.asShadow("Поле переезжает под шапку чата, место под него в списке "
                + "сообщений тоже переезжает наверх. Клавиатура и панели остаются внизу — они "
                + "принадлежат экрану, а не полю.\n\nПрименяется к следующему открытому чату: "
                + "у уже открытого половина размеров посчитана от прежней стороны."));
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        // Скруглённые карточки — так выглядят нынешние экраны настроек.
        // Без этой строки список рисуется сплошной лентой, как в прошлой
        // версии приложения: владелец это заметил сразу.
        listView.setSections();
        return view;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_TOP) {
            MargeletConfig.setInputOnTop(!MargeletConfig.inputOnTop());
            listView.adapter.update(true);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
