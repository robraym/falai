package br.com.falai;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.tasks.Tasks;

public final class ProfileActivity extends FalaiActivity {
    private LinearLayout root;
    private String owner, name = "", email = "", about = "";
    private boolean busy;
    private final ActivityResultLauncher<String> picker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri == null || busy || !owner.equals(Backend.uid())) return;
        busy = true; render();
        ProfilePhotos.prepare(getApplicationContext(), uri).onSuccessTask(photo -> {
            if (!owner.equals(Backend.uid())) return Tasks.forException(new IllegalStateException("account-changed"));
            return Backend.call("updateProfilePhoto", Backend.data("photo", photo));
        }).addOnCompleteListener(this, task -> {
            busy = false;
            if (task.isSuccessful()) AvatarView.invalidatePhoto(owner);
            else Toast.makeText(this, R.string.photo_error, Toast.LENGTH_LONG).show();
            render();
        });
    });
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (!Backend.ready() || Backend.uid().isEmpty()) { finish(); return; }
        owner = Backend.uid(); email = Backend.auth().getCurrentUser().getEmail(); name = Backend.auth().getCurrentUser().getDisplayName();
        if (email == null) email = ""; if (name == null) name = "";
        render();
        Backend.db().collection("users").document(owner).get().addOnSuccessListener(this, doc -> {
            if (doc.getString("name") != null) name = doc.getString("name"); render();
        });
        Backend.db().collection("profileAbout").document(owner).get().addOnSuccessListener(this, doc -> {
            about = doc.getString("text") == null ? "" : doc.getString("text"); render();
        });
    }
    @Override protected void onResume() { super.onResume(); if (owner != null && !owner.equals(Backend.uid())) finish(); }
    private void render() {
        if (isFinishing() || !owner.equals(Backend.uid())) return;
        root = Ui.root(this);
        LinearLayout header = Ui.row(this); header.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        ImageButton back = Ui.icon(this, R.drawable.ic_back, R.string.back, v -> finish()); header.addView(back);
        TextView title = Ui.text(this, getString(R.string.profile), 25, true); title.setPadding(Ui.dp(this, 16), 0, 0, 0); header.addView(title); root.addView(header);
        ScrollView scroll = new ScrollView(this); LinearLayout content = Ui.column(this); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(Ui.dp(this, 16), Ui.dp(this, 28), Ui.dp(this, 16), Ui.dp(this, 24));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        AvatarView avatar = new AvatarView(this); avatar.load(owner, name); avatar.setContentDescription(getString(R.string.profile_photo));
        avatar.setOnClickListener(v -> photoActions()); avatar.setFocusable(true); avatar.setEnabled(!busy); avatar.setAlpha(busy ? .6f : 1f);
        FrameLayout photo = new FrameLayout(this);
        photo.addView(avatar, new FrameLayout.LayoutParams(Ui.dp(this, 112), Ui.dp(this, 112), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        ImageButton editPhoto = new ImageButton(this); editPhoto.setImageResource(R.drawable.ic_edit);
        editPhoto.setContentDescription(getString(R.string.choose_photo)); editPhoto.setEnabled(!busy); editPhoto.setAlpha(busy ? .6f : 1f);
        editPhoto.setImageTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.background)));
        editPhoto.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        android.graphics.drawable.GradientDrawable badge = Ui.shape(this, R.color.action_green, 20);
        badge.setStroke(Ui.dp(this, 2), getColor(R.color.background));
        editPhoto.setBackground(new android.graphics.drawable.InsetDrawable(badge, Ui.dp(this, 4)));
        editPhoto.setOnClickListener(v -> photoActions());
        photo.addView(editPhoto, new FrameLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48), Gravity.BOTTOM | Gravity.END));
        content.addView(photo, new LinearLayout.LayoutParams(Ui.dp(this, 120), Ui.dp(this, 120)));
        if (busy) {
            TextView saving = Ui.text(this, getString(R.string.photo_saving), 13, false); saving.setPadding(0, Ui.dp(this, 12), 0, 0); content.addView(saving);
        }
        TextView displayName = Ui.text(this, name, 28, false); displayName.setMaxLines(2); displayName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        displayName.setGravity(Gravity.CENTER); displayName.setPadding(Ui.dp(this, 8), Ui.dp(this, 24), Ui.dp(this, 8), Ui.dp(this, 8));
        android.graphics.drawable.Drawable editIcon = getDrawable(R.drawable.ic_edit).mutate(); editIcon.setTint(Appearance.primary(this)); editIcon.setBounds(0, 0, Ui.dp(this, 18), Ui.dp(this, 18));
        displayName.setCompoundDrawables(null, null, editIcon, null); displayName.setCompoundDrawablePadding(Ui.dp(this, 8));
        displayName.setContentDescription(getString(R.string.edit_name) + ": " + name); displayName.setOnClickListener(v -> editName()); displayName.setFocusable(true); content.addView(displayName, new LinearLayout.LayoutParams(-2, -2));
        TextView mail = Ui.text(this, email, 15, false); mail.setGravity(Gravity.CENTER); mail.setSingleLine(true); mail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        content.addView(mail, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, Ui.dp(this, 1)); dividerParams.topMargin = Ui.dp(this, 24); dividerParams.bottomMargin = Ui.dp(this, 8);
        content.addView(Ui.divider(this), dividerParams);
        LinearLayout aboutRow = Ui.row(this); aboutRow.setGravity(Gravity.TOP); aboutRow.setBaselineAligned(false);
        aboutRow.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), 0, Ui.dp(this, 12));
        LinearLayout text = Ui.column(this);
        TextView aboutTitle = Ui.text(this, getString(R.string.profile_about), 13, false); aboutTitle.setPadding(0, 0, 0, Ui.dp(this, 6)); text.addView(aboutTitle);
        TextView phrase = Ui.text(this, about.isEmpty() ? getString(R.string.about_empty) : about, 16, false);
        phrase.setTextColor(getColor(about.isEmpty() ? R.color.text_secondary : R.color.text_primary)); text.addView(phrase);
        aboutRow.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView pencil = new ImageView(this); pencil.setImageResource(R.drawable.ic_edit); pencil.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(this)));
        pencil.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 14)); pencil.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        aboutRow.addView(pencil, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        aboutRow.setOnClickListener(v -> editAbout()); aboutRow.setFocusable(true); aboutRow.setContentDescription(getString(R.string.edit_about) + ": " + (about.isEmpty() ? getString(R.string.about_empty) : about));
        aboutRow.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(this)), null, new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        content.addView(aboutRow, new LinearLayout.LayoutParams(-1, -2));
    }
    private void editAbout() {
        EditText field = Ui.input(this, R.string.about_hint); field.setSingleLine(false); field.setMaxLines(3); field.setText(about);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(139)});
        LinearLayout form = Ui.column(this); form.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0); form.addView(field);
        TextView count = Ui.text(this, getString(R.string.about_count, about.length()), 12, false); count.setGravity(Gravity.END); form.addView(count);
        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int length, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int length) { count.setText(getString(R.string.about_count, s.length())); }
            public void afterTextChanged(android.text.Editable value) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.edit_about).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = field.getText().toString().trim().replaceAll("\\s+", " "); view.setEnabled(false);
            Backend.call("updateProfileAbout", Backend.data("text", value)).addOnCompleteListener(this, task -> {
                view.setEnabled(true); if (task.isSuccessful()) { about = (String) task.getResult().get("text"); dialog.dismiss(); render(); }
                else Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
            });
        })); dialog.show();
    }
    private void photoActions() {
        if (busy) return;
        new AlertDialog.Builder(this).setTitle(R.string.profile_photo).setItems(new String[]{getString(R.string.choose_photo), getString(R.string.remove_photo)}, (dialog, which) -> {
            if (which == 0) picker.launch("image/*");
            else new AlertDialog.Builder(this).setMessage(R.string.remove_photo_question).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.remove_photo, (confirmation, button) -> {
                busy = true; render(); Backend.call("updateProfilePhoto", Backend.data("photo", null)).addOnCompleteListener(this, task -> {
                    busy = false; if (task.isSuccessful()) AvatarView.invalidatePhoto(owner); else Toast.makeText(this, R.string.photo_error, Toast.LENGTH_LONG).show(); render();
                });
            }).show();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void editName() {
        EditText field = Ui.input(this, R.string.name_hint); field.setText(name);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.edit_name).setView(field).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = field.getText().toString().trim(); if (value.isEmpty() || value.length() > 60) { field.setError(getString(R.string.invalid_name)); return; }
            view.setEnabled(false); Backend.call("registerProfile", Backend.data("name", value)).addOnCompleteListener(this, task -> {
                view.setEnabled(true); if (task.isSuccessful()) { name = value; ContactsRepository.clear(); dialog.dismiss(); render(); }
                else Toast.makeText(this, Backend.error(this, task.getException()), Toast.LENGTH_LONG).show();
            });
        })); dialog.show();
    }
}
