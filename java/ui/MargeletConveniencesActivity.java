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
    private static final int ID_FILTER_ZALGO = 6;
    private static final int ID_HIDE_SEND_AS = 7;
    private static final int ID_HIDE_BOT_BUTTON = 8;
    private static final int ID_AVATAR_CORNERS = 9;
    private static final int ID_HIDE_BOTTOM_TABS = 10;
    private static final int ID_GLASS_OUTLINE = 11;
    private static final int ID_CLASSIC_DRAWER = 12;

    private String getAvatarCornerName(int mode) {
        switch (mode) {
            case 1: return LocaleController.getString(R.string.MargeletAvatarCornersSquare);
            case 2: return LocaleController.getString(R.string.MargeletAvatarCornersSquircle);
            case 3: return LocaleController.getString(R.string.MargeletAvatarCornersMedium);
            default: return LocaleController.getString(R.string.MargeletAvatarCornersDefault);
        }
    }

    private String getGlassOutlineName(int mode) {
        switch (mode) {
            case 1: return LocaleController.getString(R.string.MargeletGlassOutlineSolid);
            case 2: return LocaleController.getString(R.string.MargeletGlassOutlineHidden);
            default: return LocaleController.getString(R.string.MargeletGlassOutlineGlare);
        }
    }

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
        items.add(UItem.asCheck(ID_FILTER_ZALGO, LocaleController.getString(R.string.MargeletFilterZalgo))
                .setChecked(MargeletConfig.filterZalgo()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletFilterZalgoAbout)));
        items.add(UItem.asCheck(ID_HIDE_SEND_AS, LocaleController.getString(R.string.MargeletHideSendAsPeer))
                .setChecked(MargeletConfig.hideSendAsPeer()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletHideSendAsPeerAbout)));
        items.add(UItem.asCheck(ID_HIDE_BOT_BUTTON, LocaleController.getString(R.string.MargeletHideBotButton))
                .setChecked(MargeletConfig.hideBotButton()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletHideBotButtonAbout)));
        items.add(UItem.asButton(ID_AVATAR_CORNERS, LocaleController.getString(R.string.MargeletAvatarCorners),
                getAvatarCornerName(MargeletConfig.avatarRadius())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asButton(ID_GLASS_OUTLINE, LocaleController.getString(R.string.MargeletGlassOutlineStyle),
                getGlassOutlineName(MargeletConfig.glassOutlineStyle())));
        items.add(UItem.asShadow(null));
        items.add(UItem.asCheck(ID_HIDE_BOTTOM_TABS, LocaleController.getString(R.string.MargeletHideBottomTabs))
                .setChecked(MargeletConfig.hideBottomTabs()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletHideBottomTabsAbout)));
        items.add(UItem.asCheck(ID_CLASSIC_DRAWER, LocaleController.getString(R.string.MargeletClassicDrawer))
                .setChecked(MargeletConfig.classicDrawer()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletClassicDrawerAbout)));
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
        } else if (item.id == ID_FILTER_ZALGO) {
            MargeletConfig.setFilterZalgo(!MargeletConfig.filterZalgo());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
        } else if (item.id == ID_HIDE_SEND_AS) {
            MargeletConfig.setHideSendAsPeer(!MargeletConfig.hideSendAsPeer());
            listView.adapter.update(true);
        } else if (item.id == ID_HIDE_BOT_BUTTON) {
            MargeletConfig.setHideBotButton(!MargeletConfig.hideBotButton());
            listView.adapter.update(true);
        } else if (item.id == ID_AVATAR_CORNERS) {
            final CharSequence[] options = new CharSequence[]{
                    LocaleController.getString(R.string.MargeletAvatarCornersDefault),
                    LocaleController.getString(R.string.MargeletAvatarCornersSquare),
                    LocaleController.getString(R.string.MargeletAvatarCornersSquircle),
                    LocaleController.getString(R.string.MargeletAvatarCornersMedium)
            };
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.MargeletAvatarCorners))
                    .setItems(options, (d, which) -> {
                        MargeletConfig.setAvatarRadius(which);
                        listView.adapter.update(true);
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                        org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        } else if (item.id == ID_GLASS_OUTLINE) {
            final CharSequence[] options = new CharSequence[]{
                    LocaleController.getString(R.string.MargeletGlassOutlineGlare),
                    LocaleController.getString(R.string.MargeletGlassOutlineSolid),
                    LocaleController.getString(R.string.MargeletGlassOutlineHidden)
            };
            new AlertDialog.Builder(getContext())
                    .setTitle(LocaleController.getString(R.string.MargeletGlassOutlineStyle))
                    .setItems(options, (d, which) -> {
                        MargeletConfig.setGlassOutlineStyle(which);
                        listView.adapter.update(true);
                        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                    })
                    .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                    .show();
        } else if (item.id == ID_HIDE_BOTTOM_TABS) {
            MargeletConfig.setHideBottomTabs(!MargeletConfig.hideBottomTabs());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(org.telegram.messenger.NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
        } else if (item.id == ID_CLASSIC_DRAWER) {
            MargeletConfig.setClassicDrawer(!MargeletConfig.classicDrawer());
            listView.adapter.update(true);
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
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
