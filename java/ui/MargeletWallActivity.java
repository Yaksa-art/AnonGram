package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.margelet.MargeletGroup;
import org.telegram.margelet.MargeletLinks;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Стена: что о человеке написали другие.
 *
 * Дуров стену убрал, здесь она возвращается — и не как страница, которую
 * хозяин правит под себя. Смысл ровно в обратном: написанное про тебя ты
 * снять не можешь. Поэтому стена и работает против разводил — обманутый
 * пишет, обманщик не стирает, а видят все.
 *
 * Живёт всё в общей группе: каждое сообщение стены — обычное сообщение с
 * меткой, содержащей номер того, О КОМ пишут. Подпись под сообщением ставит
 * телеграм, значит автор отзыва подделке не поддаётся.
 *
 * Рисуем настоящими ячейками переписки — {@link ChatMessageCell}, теми же, что
 * и обычный чат. Своих карточек здесь была целая своя вёрстка, и выглядела она
 * телеграмом позапрошлых лет: чужой вид посреди приложения читается как
 * поломка, даже когда всё работает. Заодно бесплатно приезжает всё, что эта
 * ячейка умеет, — реакции, ответы, пересылки, тёмная тема, обои.
 *
 * Своё сообщение автор может удалить сам, средствами телеграма. Чужое — нет,
 * и это не недоделка, а условие задачи.
 */
public class MargeletWallActivity extends BaseFragment {

    private final long peerId;
    private final String peerName;

    private SizeNotifierFrameLayout root;
    private RecyclerListView listView;
    private Adapter adapter;
    private TextView emptyView;
    private EditTextBoldCursor input;

    /** Новое первым: список перевёрнут, как в самой переписке. */
    private final List<MessageObject> messages = new ArrayList<>();
    private boolean loading;

    public MargeletWallActivity(long peerId, String peerName) {
        this.peerId = peerId;
        this.peerName = peerName == null ? "" : peerName;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setAddToContainer(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // Заголовок с аватаркой того, чья стена: тем же видом, каким телеграм
        // подписывает переписку. Человек должен понимать, у кого он в гостях,
        // без чтения текста.
        final ChatAvatarContainer avatarContainer = new ChatAvatarContainer(context, this, false);
        avatarContainer.setOccupyStatusBar(!AndroidUtilities.isTablet());
        actionBar.addView(avatarContainer, 0, LayoutHelper.createFrame(
                LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.TOP | Gravity.LEFT, !AndroidUtilities.isTablet() ? 56 : 80, 0, 40, 0));
        avatarContainer.setTitle(LocaleController.formatString(R.string.MargeletWallOf, peerName));
        avatarContainer.setSubtitle(LocaleController.getString(R.string.MargeletWallSubtitle));
        try {
            final TLRPC.User user = getMessagesController().getUser(peerId);
            if (user != null) {
                avatarContainer.setUserAvatar(user);
            }
        } catch (Throwable ignored) {
        }

        // Обои переписки, а не серая заливка: стена — это разговор о человеке,
        // и выглядеть она должна как разговор.
        root = new SizeNotifierFrameLayout(context);
        root.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        fragmentView = root;

        listView = new RecyclerListView(context);
        final LinearLayoutManager layout = new LinearLayoutManager(context);
        // Перевёрнутый список: свежее внизу, у поля ввода, как в переписке.
        layout.setReverseLayout(true);
        listView.setLayoutManager(layout);
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(4), 0, dp(4));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((view, position) -> openInGroup(position));
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 52));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
        emptyView.setPadding(dp(32), 0, dp(32), 0);
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 52));

        root.addView(compose(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));

        root.addView(actionBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        load();
        return root;
    }

    /**
     * Поле ввода. Скруглённое поле и круглая кнопка — то, как поле ввода
     * выглядит в телеграме сейчас, а не плоская полоса из позапрошлой версии.
     *
     * Настоящий {@code ChatActivityEnterView} сюда не встаёт: он требует
     * ChatActivity и живёт его жизнью. Поэтому поле своё, но по виду — их.
     */
    private View compose(Context context) {
        final FrameLayout box = new FrameLayout(context);
        box.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        box.setPadding(dp(8), dp(6), dp(8), dp(6));

        final FrameLayout field = new FrameLayout(context);
        field.setBackground(Theme.createRoundRectDrawable(dp(20),
                Theme.getColor(Theme.key_chat_messagePanelBackground)));
        box.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM, 0, 0, 52, 0));

        input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        input.setHintColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        input.setHintText(LocaleController.getString(R.string.MargeletWallHint));
        input.setBackground(null);
        input.setPadding(dp(14), dp(9), dp(14), dp(9));
        input.setMaxLines(5);
        field.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));

        final ImageView send = new ImageView(context);
        send.setScaleType(ImageView.ScaleType.CENTER);
        send.setImageResource(R.drawable.attach_send);
        send.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(44),
                Theme.getColor(Theme.key_chat_messagePanelSend),
                Theme.getColor(Theme.key_chat_messagePanelSend)));
        send.setColorFilter(new PorterDuffColorFilter(
                Theme.getColor(Theme.key_windowBackgroundWhite), PorterDuff.Mode.SRC_IN));
        send.setOnClickListener(v -> send());
        box.addView(send, LayoutHelper.createFrame(44, 44, Gravity.RIGHT | Gravity.BOTTOM));
        return box;
    }

    /**
     * Нажатие открывает само сообщение в группе: там работает всё, что умеет
     * телеграм, — ответить, поставить реакцию, пожаловаться. Своей половины
     * телеграма мы не пишем.
     */
    private void openInGroup(int position) {
        if (position < 0 || position >= messages.size()) {
            return;
        }
        Browser.openUrl(getContext(), "https://t.me/" + MargeletGroup.USERNAME
                + "/" + messages.get(position).getId());
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        MargeletGroup.find(MargeletGroup.tagWall(peerId), 100, (found, problem) -> {
            loading = false;
            messages.clear();
            for (MessageObject message : found) {
                // Проверка на показе, а не только при отправке: написать в
                // группу можно и обычным телеграмом, мимо нашего приложения.
                if (MargeletGroup.showable(message)) {
                    strip(message);
                    messages.add(message);
                }
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (emptyView != null) {
                // Пусто и «не смогли спросить» — разные вещи, и раньше они
                // выглядели одинаково: чистый экран. По чистому экрану нельзя
                // понять, что чинить.
                emptyView.setText(LocaleController.getString(problem != null
                        ? R.string.MargeletWallFailed : R.string.MargeletWallEmpty));
                emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
    }

    /**
     * Убирает служебную метку из текста сообщения.
     *
     * Метка нужна поиску, а не читателю: видеть «#margy_wall_123» первой
     * строкой каждого отзыва незачем. Правим сам объект до того, как ячейка
     * разберёт текст на строки, иначе она нарисует его вместе с меткой.
     */
    private void strip(MessageObject message) {
        try {
            if (message.messageOwner == null || message.messageOwner.message == null) {
                return;
            }
            final String tag = MargeletGroup.tagWall(peerId);
            String text = message.messageOwner.message;
            final int at = text.indexOf(tag);
            if (at < 0) {
                return;
            }
            text = (text.substring(0, at) + text.substring(at + tag.length())).trim();
            message.messageOwner.message = text;
            // Отсчёты разметки после вырезания метки уехали бы, а поправить их
            // здесь нечем: разметку в отзывах мы всё равно не показываем.
            message.messageOwner.entities = new ArrayList<>();
            message.applyNewText();
        } catch (Throwable ignored) {
        }
    }

    private void send() {
        final String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            return;
        }
        final String bad = MargeletLinks.firstBad(text, null);
        if (bad != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    LocaleController.formatString(R.string.MargeletWallNoLinks, bad)).show();
            return;
        }
        input.setText("");
        // Метка первой строкой: так её видно и в самой группе, и человеку
        // понятно, куда ушло его сообщение.
        // Перечитываем дважды: первый раз почти сразу — своё сообщение уже
        // лежит в истории группы и приезжает мгновенно; второй на случай, если
        // отправка задержалась в очереди. Одного отложенного взгляда мало,
        // а ждать секунду впустую незачем.
        MargeletGroup.post(MargeletGroup.tagWall(peerId) + "\n" + text, () -> {
            AndroidUtilities.runOnUIThread(this::load, 400);
            AndroidUtilities.runOnUIThread(this::load, 2500);
        });
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final ChatMessageCell cell = new ChatMessageCell(parent.getContext(), currentAccount);
            // Ячейке нужен собеседник, иначе она не знает, кому что показывать.
            // Пустой годится: всё, что она у него спрашивает, для стены не
            // нужно, а без него она падает.
            cell.setDelegate(new ChatMessageCell.ChatMessageCellDelegate() {
            });
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            final ChatMessageCell cell = (ChatMessageCell) holder.itemView;
            final MessageObject message = messages.get(position);
            // Слепляем подряд идущие сообщения одного автора в один пузырь —
            // ровно так же, как это делает переписка. Список перевёрнут,
            // поэтому «предыдущее сверху» лежит по большему номеру.
            final MessageObject above = position + 1 < messages.size()
                    ? messages.get(position + 1) : null;
            final MessageObject below = position > 0 ? messages.get(position - 1) : null;
            cell.setMessageObject(message, null, sameAuthor(message, below),
                    sameAuthor(message, above), false);
        }

        /** Один ли автор и близко ли по времени: условие склейки в переписке. */
        private boolean sameAuthor(MessageObject one, MessageObject other) {
            if (one == null || other == null) {
                return false;
            }
            try {
                return one.isOutOwner() == other.isOutOwner()
                        && one.getFromChatId() == other.getFromChatId()
                        && Math.abs(one.messageOwner.date - other.messageOwner.date) <= 5 * 60;
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}
