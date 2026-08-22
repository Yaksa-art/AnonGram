package org.telegram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.margelet.MargeletTags;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/**
 * Правка тегов у аудио прямо из чата: название, исполнитель, обложка.
 *
 * В телеграме это обычно делают через ботов — то есть отдают свой файл чужому
 * серверу ради трёх строчек текста. Здесь всё происходит на телефоне: теги
 * пишутся в копию файла, копия отправляется в тот же чат. Исходное сообщение
 * не трогаем: чужое сообщение править нельзя, а своё телеграм разрешает менять
 * только текстом, не файлом.
 */
public class MargeletTagsAlert {

    public static final int PICK_COVER = 4802;

    private static MargeletTagsAlert current;

    private final ChatActivity fragment;
    private final MessageObject message;
    private final EditTextBoldCursor titleField;
    private final EditTextBoldCursor artistField;
    private final TextView coverButton;
    private byte[] cover;

    private MargeletTagsAlert(ChatActivity fragment, MessageObject message) {
        this.fragment = fragment;
        this.message = message;

        final Activity context = fragment.getParentActivity();
        titleField = field(context, "Название");
        artistField = field(context, "Исполнитель");

        final TLRPC.Document document = message.getDocument();
        if (document != null) {
            for (TLRPC.DocumentAttribute a : document.attributes) {
                if (a instanceof TLRPC.TL_documentAttributeAudio) {
                    titleField.setText(a.title == null ? "" : a.title);
                    artistField.setText(a.performer == null ? "" : a.performer);
                }
            }
        }

        coverButton = new TextView(context);
        coverButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        coverButton.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        coverButton.setText("Выбрать обложку");
        coverButton.setPadding(0, dp(12), 0, dp(4));
        coverButton.setOnClickListener(v -> pickCover());
    }

    private static EditTextBoldCursor field(Activity context, String hint) {
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setHintText(hint);
        edit.setBackgroundDrawable(null);
        edit.setLineColors(Theme.getColor(Theme.key_dialogInputField),
                Theme.getColor(Theme.key_dialogInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular));
        edit.setSingleLine(true);
        edit.setPadding(0, dp(4), 0, dp(6));
        return edit;
    }

    public static void show(ChatActivity fragment, MessageObject message) {
        if (fragment == null || fragment.getParentActivity() == null || message == null) {
            return;
        }
        final MargeletTagsAlert alert = new MargeletTagsAlert(fragment, message);
        current = alert;

        final LinearLayout layout = new LinearLayout(fragment.getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(alert.titleField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40));
        layout.addView(alert.artistField, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40, 0, 8, 0, 0));
        layout.addView(alert.coverButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT));

        new AlertDialog.Builder(fragment.getParentActivity())
                .setTitle("Теги трека")
                .setView(layout)
                .setPositiveButton("Отправить", (d, w) -> alert.apply())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void pickCover() {
        try {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            fragment.startActivityForResult(intent, PICK_COVER);
        } catch (Exception ignored) {
            // Не на каждом телефоне есть чем открыть выбор файла.
        }
    }

    /** Вызывается из ChatActivity, когда система вернула выбранную картинку. */
    public static void onCoverPicked(Intent data) {
        if (current == null || data == null || data.getData() == null) {
            return;
        }
        current.readCover(data.getData());
    }

    private void readCover(Uri uri) {
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) {
                return;
            }
            // Обложку ужимаем: в тег иногда кладут снимок с камеры на пять
            // мегабайт, и он едет вместе с песней каждому получателю.
            final int max = 800;
            if (bitmap.getWidth() > max || bitmap.getHeight() > max) {
                final float scale = Math.min(max / (float) bitmap.getWidth(), max / (float) bitmap.getHeight());
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        Math.round(bitmap.getWidth() * scale), Math.round(bitmap.getHeight() * scale), true);
            }
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 87, out);
            cover = out.toByteArray();
            coverButton.setText("Обложка выбрана");
        } catch (Exception ignored) {
        }
    }

    private void apply() {
        final File src = FileLoader.getInstance(fragment.getCurrentAccount())
                .getPathToMessage(message.messageOwner);
        if (src == null || !src.exists()) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    "Сначала скачай файл — теги пишутся в него, а его ещё нет на телефоне.").show();
            return;
        }
        final String name = message.getDocumentName();
        final File dst = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE),
                (name == null || name.isEmpty() ? "track.mp3" : name));
        final boolean ok = MargeletTags.write(src, dst,
                titleField.getText().toString().trim(),
                artistField.getText().toString().trim(),
                cover);
        if (!ok) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error,
                    "Не получилось записать теги в файл.").show();
            return;
        }
        SendMessagesHelper.prepareSendingDocument(fragment.getAccountInstance(),
                dst.getAbsolutePath(), dst.getAbsolutePath(), null, null, "audio/mpeg",
                fragment.getDialogId(), null, fragment.getThreadMessage(), null, null, null,
                true, 0, null, fragment.getMessageChatSendParams(), false);
        current = null;
    }
}
