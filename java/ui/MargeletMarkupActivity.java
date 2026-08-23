package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletMarkup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Оформление»: что из своего оформления показывать у себя.
 *
 * Выключатели тут не про отправку, а про показ. Отправить оформленное можно
 * всегда — а вот принимать чужую радугу человек может и не хотеть.
 */
public class MargeletMarkupActivity extends UniversalFragment {

    private static final int ID_SIZE = 1;
    private static final int ID_DIM = 2;
    private static final int ID_RAINBOW = 3;
    private static final int ID_WATERMARKS = 4;
    private static final int ID_COPY = 5;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletMarkup);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_SIZE, LocaleController.getString(R.string.MargeletMarkupSize))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_SIZE)));
        items.add(UItem.asCheck(ID_DIM, LocaleController.getString(R.string.MargeletMarkupDim))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_DIM)));
        items.add(UItem.asCheck(ID_RAINBOW, LocaleController.getString(R.string.MargeletMarkupRainbow))
                .setChecked(MargeletConfig.markupEnabled(MargeletMarkup.KIND_RAINBOW)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletMarkupAbout)));
        items.add(UItem.asCheck(ID_WATERMARKS, LocaleController.getString(R.string.MargeletWatermarks))
                .setChecked(MargeletConfig.showWatermarks()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletWatermarksAbout)));
        items.add(UItem.asCheck(ID_COPY, LocaleController.getString(R.string.MargeletCopyFormatted))
                .setChecked(MargeletConfig.copyFormatting()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletCopyFormattedAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_WATERMARKS) {
            MargeletConfig.setShowWatermarks(!MargeletConfig.showWatermarks());
        } else if (item.id == ID_COPY) {
            MargeletConfig.setCopyFormatting(!MargeletConfig.copyFormatting());
        } else {
            final int kind = item.id == ID_SIZE ? MargeletMarkup.KIND_SIZE
                    : item.id == ID_DIM ? MargeletMarkup.KIND_DIM
                    : MargeletMarkup.KIND_RAINBOW;
            MargeletConfig.setMarkupEnabled(kind, !MargeletConfig.markupEnabled(kind));
        }
        listView.adapter.update(true);
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
