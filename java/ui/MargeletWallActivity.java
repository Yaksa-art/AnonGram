package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.margelet.MargeletGroup;
import org.telegram.margelet.MargeletLinks;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatAvatarContainer;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

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
 * Реакции показываем как есть: это и есть суд толпы. Наговор минусуют, и
 * читающий видит не только обвинение, но и то, поверили ли ему.
 *
 * Своё сообщение автор может удалить сам, средствами телеграма. Чужое — нет,
 * и это не недоделка, а условие задачи.
 */
public class MargeletWallActivity extends BaseFragment {

    private final long peerId;
    private final String peerName;

    private RecyclerListView listView;
    private Adapter adapter;
    private TextView emptyView;
    private EditTextBoldCursor input;
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

        final FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = root;

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setClipToPadding(false);
        listView.setPadding(0, dp(8), 0, dp(8));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 56));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        emptyView.setPadding(dp(32), 0, dp(32), 0);
        emptyView.setText(LocaleController.getString(R.string.MargeletWallEmpty));
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT, Gravity.TOP, 0, 0, 0, 56));

        root.addView(compose(context), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                56, Gravity.BOTTOM));

        load();
        return root;
    }

    /** Поле ввода и кнопка отправки. */
    private View compose(Context context) {
        final FrameLayout box = new FrameLayout(context);
        box.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

        input = new EditTextBoldCursor(context);
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        input.setHintText(LocaleController.getString(R.string.MargeletWallHint));
        input.setBackground(null);
        input.setMaxLines(3);
        box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 56, 0));

        final ImageView send = new ImageView(context);
        send.setScaleType(ImageView.ScaleType.CENTER);
        send.setImageResource(R.drawable.attach_send);
        send.setColorFilter(new android.graphics.PorterDuffColorFilter(
                Theme.getColor(Theme.key_chats_actionBackground),
                android.graphics.PorterDuff.Mode.SRC_IN));
        send.setOnClickListener(v -> send());
        box.addView(send, LayoutHelper.createFrame(48, 48, Gravity.RIGHT | Gravity.CENTER_VERTICAL));
        return box;
    }

    private void load() {
        if (loading) {
            return;
        }
        loading = true;
        MargeletGroup.find(MargeletGroup.tagWall(peerId), 100, found -> {
            loading = false;
            messages.clear();
            for (MessageObject message : found) {
                // Проверка на показе, а не только при отправке: написать в
                // группу можно и обычным телеграмом, мимо нашего приложения.
                if (MargeletGroup.showable(message)) {
                    messages.add(message);
                }
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            if (emptyView != null) {
                emptyView.setVisibility(messages.isEmpty() ? View.VISIBLE : View.GONE);
            }
        });
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
        MargeletGroup.post(MargeletGroup.tagWall(peerId) + "\n" + text, () ->
                AndroidUtilities.runOnUIThread(this::load, 1200));
    }

    /** Одна запись на стене. */
    private class Cell extends FrameLayout {
        private final BackupImageView avatar;
        private final TextView name;
        private final TextView body;
        private final TextView date;
        private final TextView reactions;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();

        Cell(Context context) {
            super(context);
            setPadding(dp(12), dp(8), dp(12), dp(8));

            final FrameLayout card = new FrameLayout(context);
            card.setBackground(Theme.createRoundRectDrawable(dp(12),
                    Theme.getColor(Theme.key_windowBackgroundWhite)));
            addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT));

            avatar = new BackupImageView(context);
            avatar.setRoundRadius(dp(16));
            card.addView(avatar, LayoutHelper.createFrame(32, 32, Gravity.TOP | Gravity.LEFT, 12, 12, 0, 0));

            name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            name.setTypeface(AndroidUtilities.bold());
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
            card.addView(name, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 54, 12, 60, 0));

            date = new TextView(context);
            date.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            date.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            card.addView(date, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT, 0, 13, 12, 0));

            body = new TextView(context);
            body.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            body.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            card.addView(body, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 54, 32, 12, 0));

            reactions = new TextView(context);
            reactions.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            reactions.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            card.addView(reactions, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 54, 0, 12, 12));
        }

        void set(MessageObject message) {
            final long author = MargeletGroup.authorOf(message);
            final TLRPC.User user = getMessagesController().getUser(author);
            final String title = user != null ? UserObject.getUserName(user)
                    : LocaleController.getString(R.string.MargeletWallSomeone);
            name.setText(title);
            if (user != null) {
                avatarDrawable.setInfo(currentAccount, user);
                avatar.setForUserOrChat(user, avatarDrawable);
            } else {
                avatar.setImageDrawable(avatarDrawable);
            }
            // Метку из текста убираем: она служебная, читать её человеку незачем.
            String text = message.messageOwner != null && message.messageOwner.message != null
                    ? message.messageOwner.message : "";
            final String tag = MargeletGroup.tagWall(peerId);
            if (text.startsWith(tag)) {
                text = text.substring(tag.length()).trim();
            }
            body.setText(text);
            date.setText(LocaleController.formatDateAudio(message.messageOwner.date, true));
            reactions.setText(reactionsOf(message));
            reactions.setVisibility(reactions.getText().length() > 0 ? VISIBLE : GONE);
            // Нажатие открывает само сообщение в группе: там работает всё, что
            // умеет телеграм, — ответить, поставить реакцию, пожаловаться. Своей
            // половины телеграма мы не пишем.
            setOnClickListener(v -> Browser.openUrl(getContext(),
                    "https://t.me/" + MargeletGroup.USERNAME + "/" + message.getId()));
        }

        /** Реакции строкой. Это и есть ответ читателей на написанное. */
        private String reactionsOf(MessageObject message) {
            try {
                if (message.messageOwner == null || message.messageOwner.reactions == null
                        || message.messageOwner.reactions.results == null) {
                    return "";
                }
                final StringBuilder out = new StringBuilder();
                for (TLRPC.ReactionCount count : message.messageOwner.reactions.results) {
                    if (count.reaction instanceof TLRPC.TL_reactionEmoji) {
                        if (out.length() > 0) {
                            out.append("   ");
                        }
                        out.append(((TLRPC.TL_reactionEmoji) count.reaction).emoticon)
                                .append(' ').append(count.count);
                    }
                }
                return out.toString();
            } catch (Throwable t) {
                return "";
            }
        }
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            final Cell cell = new Cell(parent.getContext());
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            ((Cell) holder.itemView).set(messages.get(position));
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }
}
