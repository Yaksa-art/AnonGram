package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.IconBackgroundColors;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Корень своего раздела: ветки, а не свалка переключателей. Пока веток одна,
 * «Поле ввода», плюс две ссылки — канал и форум.
 *
 * Строки рисуются тем же классом, что и на главном экране настроек
 * (SettingsActivity.SettingCell): цветная плашка со значком, название,
 * подпись под ним. Первая версия была собрана на старых ячейках, и владелец
 * сразу заметил, что раздел выглядит из прошлой версии приложения.
 */
public class MargeletSettingsActivity extends UniversalFragment {

    private static final int ID_INPUT = 1;
    private static final int ID_SOUND = 2;
    private static final int ID_CHANNEL = 3;
    private static final int ID_FORUM = 4;
    private static final int ID_TRACKS = 5;

    @Override
    protected CharSequence getTitle() {
        return "Margelet";
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(SettingsActivity.SettingCell.Factory.of(ID_INPUT,
                IconBackgroundColors.GREEN.top, IconBackgroundColors.GREEN.bottom,
                R.drawable.settings_chat, LocaleController.getString(R.string.MargeletInput), LocaleController.getString(R.string.MargeletInputInfo)));
        // Раздел «Звук» появляется, только когда мяуканье уже услышали.
        if (MargeletConfig.meowHeard()) {
            items.add(SettingsActivity.SettingCell.Factory.of(ID_SOUND,
                    IconBackgroundColors.ORANGE_DEEP.top, IconBackgroundColors.ORANGE_DEEP.bottom,
                    R.drawable.settings_sounds, LocaleController.getString(R.string.MargeletSound), LocaleController.getString(R.string.MargeletSoundInfo)));
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_TRACKS,
                IconBackgroundColors.PURPLE.top, IconBackgroundColors.PURPLE.bottom,
                R.drawable.settings_folders, LocaleController.getString(R.string.MargeletTracks),
                LocaleController.getString(R.string.MargeletTracksInfo)));
        items.add(UItem.asShadow(null));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_CHANNEL,
                IconBackgroundColors.BLUE.top, IconBackgroundColors.BLUE.bottom,
                R.drawable.settings_channel, LocaleController.getString(R.string.MargeletChannel), "t.me/margeletter"));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_FORUM,
                IconBackgroundColors.ORANGE.top, IconBackgroundColors.ORANGE.bottom,
                R.drawable.settings_group, LocaleController.getString(R.string.MargeletForum), "t.me/margeletforum"));
        items.add(UItem.asShadow(null));
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
        if (item.id == ID_INPUT) {
            presentFragment(new MargeletInputActivity());
        } else if (item.id == ID_SOUND) {
            presentFragment(new MargeletSoundActivity());
        } else if (item.id == ID_CHANNEL) {
            Browser.openUrl(getContext(), MargeletConfig.CHANNEL_URL);
        } else if (item.id == ID_TRACKS) {
            presentFragment(new MargeletTracksActivity());
        } else if (item.id == ID_FORUM) {
            Browser.openUrl(getContext(), MargeletConfig.FORUM_URL);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
