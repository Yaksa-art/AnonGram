package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletBadge;
import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Витрина значков: какие вообще бывают и как выглядит любой из них на себе.
 *
 * Примерка живёт только в этом приложении и только у того, кто примеряет: на
 * сервер ничего не уходит, чужим ничего не видно. Значок и так ничего не
 * удостоверяет, но примерка не должна выглядеть как способ им притвориться.
 */
public class MargeletBadgeGalleryActivity extends UniversalFragment {

    private static final int ID_NONE = 1000;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletBadgeGallery);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final MargeletBadge.Badge[] badges = MargeletBadge.list();
        final int chosen = MargeletConfig.badgePreview();
        items.add(UItem.asRadio(ID_NONE, LocaleController.getString(R.string.MargeletBadgePreviewOff))
                .setChecked(chosen < 0));
        for (int i = 0; i < badges.length; i++) {
            items.add(UItem.asRadio(i, LocaleController.getString(badges[i].title))
                    .setChecked(chosen == i));
        }
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletBadgePreviewAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        MargeletConfig.setBadgePreview(item.id == ID_NONE ? -1 : item.id);
        listView.adapter.update(true);
        if (item.id != ID_NONE) {
            MargeletBadge.show(getContext(), MargeletBadge.list()[item.id]);
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
