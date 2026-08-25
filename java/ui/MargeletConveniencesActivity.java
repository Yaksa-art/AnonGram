package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletSeizure;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * «Удобности» — мелкие переключатели, которым не нужен свой экран каждому:
 * канал форка сверху, теги музыки и «приступ». Раньше они были разбросаны по
 * корню настроек (а теги музыки занимали целую вкладку ради одного тумблера);
 * собраны сюда, чтобы корень не был свалкой.
 */
public class MargeletConveniencesActivity extends UniversalFragment {

    private static final int ID_CHANNEL_TOP = 1;
    private static final int ID_TRACKS = 2;
    private static final int ID_SEIZURE = 3;
    private static final int ID_FONTS = 4;
    private static final int ID_RELATIVE_ONLINE_TIME = 5;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletConveniences);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        // Скруглённые карточки — как на прочих экранах настроек.
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(ID_CHANNEL_TOP, LocaleController.getString(R.string.MargeletChannelOnTop))
                .setChecked(MargeletConfig.channelOnTop()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletChannelOnTopAbout)));
        items.add(UItem.asCheck(ID_RELATIVE_ONLINE_TIME, LocaleController.getString(R.string.MargeletRelativeOnlineTime))
                .setChecked(MargeletConfig.relativeOnlineTime()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletRelativeOnlineTimeAbout)));
        items.add(UItem.asCheck(ID_TRACKS, LocaleController.getString(R.string.MargeletTracksEnabled))
                .setChecked(MargeletConfig.tagsEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletTracksEnabledAbout)));
        items.add(UItem.asCheck(ID_SEIZURE, LocaleController.getString(R.string.MargeletSeizure))
                .setChecked(MargeletSeizure.enabled()));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_FONTS, LocaleController.getString(R.string.MargeletFonts),
                LocaleController.getString(R.string.MargeletFontsInfo)));
        items.add(UItem.asShadow(null));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_CHANNEL_TOP) {
            MargeletConfig.setChannelOnTop(!MargeletConfig.channelOnTop());
            listView.adapter.update(true);
        } else if (item.id == ID_RELATIVE_ONLINE_TIME) {
            MargeletConfig.setRelativeOnlineTime(!MargeletConfig.relativeOnlineTime());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_STATUS);
        } else if (item.id == ID_TRACKS) {
            MargeletConfig.setTagsEnabled(!MargeletConfig.tagsEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_SEIZURE) {
            toggleSeizure();
        } else if (item.id == ID_FONTS) {
            presentFragment(new MargeletFontsActivity());
        }
    }

    /**
     * Выключается молча, включается только через предупреждение: подвижная
     * картинка бывает опасна не в переносном смысле, и решать это за человека
     * нельзя.
     */
    private void toggleSeizure() {
        if (MargeletSeizure.enabled()) {
            MargeletSeizure.set(false);
            listView.adapter.update(true);
            return;
        }
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.MargeletSeizureWarning))
                .setMessage(LocaleController.getString(R.string.MargeletSeizureWarningText))
                .setPositiveButton(LocaleController.getString(R.string.MargeletSeizureEnable), (d, w) -> {
                    MargeletSeizure.set(true);
                    listView.adapter.update(true);
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
