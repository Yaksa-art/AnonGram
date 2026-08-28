package org.telegram.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;

import org.telegram.margelet.MargeletBanner;
import org.telegram.margelet.MargeletConfig;
import org.telegram.margelet.MargeletGroup;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Профиль»: баннер за аватаркой и стены.
 *
 * Обе вещи живут в общей группе, а не у нас на сервере, и это стоит сказать
 * человеку прямо на экране, а не спрятать: то, что он сюда положит, увидят
 * все, включая тех, у кого форка нет.
 */
public class MargeletProfileActivity extends UniversalFragment {

    private static final int ID_BANNER = 1;
    private static final int ID_BANNER_OFF = 2;
    private static final int ID_BANNERS_SHOW = 3;
    private static final int ID_WALL_SHOW = 4;
    private static final int ID_MY_WALL = 5;
    private static final int ID_GROUP = 6;

    private static final int PICK_BANNER = 4833;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletProfileTitle);
    }

    @Override
    public View createView(Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletBannerHeader)));
        items.add(UItem.asButton(ID_BANNER, LocaleController.getString(R.string.MargeletBannerPick)));
        items.add(UItem.asButton(ID_BANNER_OFF, LocaleController.getString(R.string.MargeletBannerRemove)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletBannerAbout)));

        items.add(UItem.asCheck(ID_BANNERS_SHOW, LocaleController.getString(R.string.MargeletBannerShow))
                .setChecked(MargeletConfig.bannersEnabled()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(LocaleController.getString(R.string.MargeletWall)));
        items.add(UItem.asButton(ID_MY_WALL, LocaleController.getString(R.string.MargeletWallOpenMine)));
        items.add(UItem.asCheck(ID_WALL_SHOW, LocaleController.getString(R.string.MargeletWallShow))
                .setChecked(MargeletConfig.wallEnabled()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletWallAbout)));

        items.add(UItem.asButton(ID_GROUP, LocaleController.getString(R.string.MargeletProfileGroup),
                "@" + MargeletGroup.USERNAME));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletProfileGroupAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_BANNER) {
            pick();
        } else if (item.id == ID_BANNER_OFF) {
            MargeletBanner.clear(() -> BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.MargeletBannerRemoved)).show());
        } else if (item.id == ID_BANNERS_SHOW) {
            MargeletConfig.setBannersEnabled(!MargeletConfig.bannersEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_WALL_SHOW) {
            MargeletConfig.setWallEnabled(!MargeletConfig.wallEnabled());
            listView.adapter.update(true);
        } else if (item.id == ID_MY_WALL) {
            final long me = UserConfig.getInstance(currentAccount).getClientUserId();
            presentFragment(new MargeletWallActivity(me,
                    LocaleController.getString(R.string.MargeletWallMine)));
        } else if (item.id == ID_GROUP) {
            Browser.openUrl(getContext(), "https://t.me/" + MargeletGroup.USERNAME);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private void pick() {
        // Предупреждаем до выбора, а не после отправки: баннер уходит в общую
        // группу, и оттуда его видно всем, даже тем, у кого форка нет. Человек
        // должен знать это раньше, чем выберет фотографию.
        new AlertDialog.Builder(getContext())
                .setTitle(LocaleController.getString(R.string.MargeletBannerHeader))
                .setMessage(LocaleController.getString(R.string.MargeletBannerWarn))
                .setPositiveButton(LocaleController.getString(R.string.MargeletBannerPick), (d, w) -> {
                    final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.setType("image/*");
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    try {
                        startActivityForResult(intent, PICK_BANNER);
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_BANNER || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        MargeletBanner.set(uri, () -> {
            if (getContext() == null) {
                return;
            }
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    LocaleController.getString(R.string.MargeletBannerSaved)).show();
        });
    }
}
