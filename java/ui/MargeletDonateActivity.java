package org.telegram.ui;

import android.view.View;

import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

/**
 * Ветка «Донат»: куда можно отправить деньги автору форка.
 *
 * Кнопок оплаты внутри приложения нет намеренно. Форк мессенджера — последнее
 * место, где стоит вводить платёжные данные, и просить об этом человека я не
 * буду. Здесь только реквизиты, которые копируются нажатием; платит человек
 * там, где обычно платит.
 *
 * Номер разбит по четыре цифры для глаза, а копируется сплошным: пробелы в
 * поле перевода мешают.
 */
public class MargeletDonateActivity extends UniversalFragment {

    private static final int ID_YOOMONEY = 1;
    private static final int ID_ROBLOX = 2;

    @Override
    protected CharSequence getTitle() {
        return LocaleController.getString(R.string.MargeletDonate);
    }

    @Override
    public View createView(android.content.Context context) {
        final View view = super.createView(context);
        listView.setSections();
        return view;
    }

    private static String spaced(String digits) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && i % 4 == 0) {
                sb.append(' ');
            }
            sb.append(digits.charAt(i));
        }
        return sb.toString();
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(ID_YOOMONEY, LocaleController.getString(R.string.MargeletDonateYoomoney),
                spaced(MargeletConfig.DONATE_YOOMONEY)));
        items.add(UItem.asButton(ID_ROBLOX, LocaleController.getString(R.string.MargeletDonateRoblox),
                MargeletConfig.DONATE_ROBLOX));
        items.add(UItem.asShadow(LocaleController.getString(R.string.MargeletDonateAbout)));
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        final String value = item.id == ID_YOOMONEY ? MargeletConfig.DONATE_YOOMONEY
                : item.id == ID_ROBLOX ? MargeletConfig.DONATE_ROBLOX : null;
        if (value == null) {
            return;
        }
        AndroidUtilities.addToClipboard(value);
        BulletinFactory.of(this).createCopyBulletin(
                LocaleController.getString(R.string.MargeletDonateCopied)).show();
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
