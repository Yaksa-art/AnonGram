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
 * папка «Все чаты», теги музыки и «приступ». Раньше они были разбросаны по
 * корню настроек (а теги музыки занимали целую вкладку ради одного тумблера);
 * собраны сюда, чтобы корень не был свалкой.
 */
public class MargeletConveniencesActivity extends UniversalFragment {

    private static final int ID_ALL_CHATS = 1;
    private static final int ID_TRACKS = 2;
    private static final int ID_SEIZURE = 3;
    private static final int ID_FONTS = 4;

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
        items.add(UItem.asCheck(ID_ALL_CHATS, LocaleController.getString(R.string.MargeletHideAllChats))
                .setChecked(MargeletConfig.hideAllChatsTab()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletHideAllChatsAbout)));
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
        if (item.id == ID_ALL_CHATS) {
            MargeletConfig.setHideAllChatsTab(!MargeletConfig.hideAllChatsTab());
            listView.adapter.update(true);
            // Полоса вкладок пересобирается по этому же событию, каким телеграм
            // отвечает на правку папок. Без него список чатов узнал бы о
            // настройке только при следующем открытии, и человек решил бы, что
            // она не работает.
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount)
                    .postNotificationName(
                            org.telegram.messenger.NotificationCenter.dialogFiltersUpdated);
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
