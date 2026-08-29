package org.telegram.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.margelet.MargeletGroup;
import org.telegram.messenger.HashtagSearchController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Стена: что о человеке написали другие.
 *
 * Дуров стену убрал, здесь она возвращается — и не как страница, которую
 * хозяин правит под себя. Смысл ровно в обратном: написанное про тебя ты
 * снять не можешь. Поэтому стена и работает против разводил — обманутый
 * пишет, обманщик не стирает, а видят все.
 *
 * Показывает её сам телеграм. У него есть готовый режим — поиск по метке
 * внутри одной группы, тот самый, которым он показывает найденное по хэштегу
 * в канале, — и он отдаёт ровно список нужных сообщений, а не всю переписку.
 *
 * До этого я трижды подходил не с той стороны: сначала своими карточками,
 * потом чужими ячейками на своём экране, потом открывал всю группу с включённой
 * строкой поиска — а поиск в переписке не отбирает сообщения, он подсвечивает и
 * прыгает по ним. Владелец каждый раз это видел раньше меня. Правильный ход
 * был первым же: искать, чем это делает сам телеграм, а не собирать своё из
 * его деталей.
 */
public class MargeletWallActivity extends BaseFragment {

    /** О ком стена. Метка считается от него, а не от того, кто пишет. */
    private final long peerId;
    private final String peerName;

    private ChatActivityContainer container;

    private static final int ID_WRITE = 1;

    public MargeletWallActivity(long peerId, String peerName) {
        this.peerId = peerId;
        this.peerName = peerName == null ? "" : peerName;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(LocaleController.formatString(R.string.MargeletWallOf, peerName));
        actionBar.setSubtitle(LocaleController.getString(R.string.MargeletWallSubtitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == ID_WRITE) {
                    write();
                }
            }
        });
        final ActionBarMenuItem write = actionBar.createMenu()
                .addItem(ID_WRITE, R.drawable.msg_edit);
        write.setContentDescription(LocaleController.getString(R.string.MargeletWallWrite));

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        // Дальше всё чужое. Свои здесь только доводы: какую метку искать и в
        // какой группе. Рисует, листает, показывает реакции и меню сообщения
        // сам телеграм — тем же кодом, которым показывает найденное по
        // хэштегу в любом канале.
        HashtagSearchController.getInstance(currentAccount)
                .clearSearchResults(ChatActivity.SEARCH_CHANNEL_POSTS);
        final Bundle args = new Bundle();
        args.putInt("chatMode", ChatActivity.MODE_SEARCH);
        args.putInt("searchType", ChatActivity.SEARCH_CHANNEL_POSTS);
        // Метка с «собакой» и именем группы — так этот поиск понимает, что
        // искать надо не везде, а в одном месте.
        args.putString("searchHashtag",
                MargeletGroup.tagWall(peerId) + "@" + MargeletGroup.USERNAME);
        container = new ChatActivityContainer(context, getParentLayout(), args);
        root.addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT, Gravity.FILL));

        return fragmentView = root;
    }

    /**
     * Написать на стену.
     *
     * Экран поиска только показывает — писать в нём нечем, и приделывать к
     * нему своё поле ввода значило бы снова подделывать чужой экран. Поэтому
     * открываем саму группу: там настоящее поле, а метку допишет отправка,
     * пока стена считается открытой. Человеку про метку знать не надо — он её
     * даже не увидит, она прячется при показе.
     */
    private void write() {
        MargeletGroup.resolve(dialogId -> {
            if (dialogId == 0) {
                org.telegram.ui.Components.BulletinFactory.of(this)
                        .createSimpleBulletin(R.raw.error, LocaleController.getString(
                                R.string.MargeletGroupUnreachable)).show();
                return;
            }
            MargeletGroup.writingTo(peerId);
            final Bundle args = new Bundle();
            args.putLong("chat_id", -dialogId);
            presentFragment(new ChatActivity(args));
        });
    }

    @Override
    public void onFragmentDestroy() {
        // Ушли со стены — метку больше не дописываем. Иначе она уехала бы в
        // соседнюю переписку вместе со следующим же сообщением.
        MargeletGroup.writingTo(0);
        super.onFragmentDestroy();
    }
}
