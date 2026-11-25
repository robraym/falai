package br.com.falai;

import android.content.Context;
import android.view.Gravity;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import com.google.android.gms.tasks.Tasks;

final class ProfileController {
    private final FalaiActivity activity;
    private LinearLayout root;
    private String owner, name = "", email = "", about = "";
    private boolean busy;
    private final ActivityResultLauncher<String> picker;
    ProfileController(FalaiActivity activity) {
        this.activity = activity;
        picker = activity.registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
        if (uri == null || busy || !owner.equals(Backend.uid())) return;
        busy = true; render();
        ProfilePhotos.prepare(activity.getApplicationContext(), uri).onSuccessTask(photo -> {
            if (!owner.equals(Backend.uid())) return Tasks.forException(new IllegalStateException("account-changed"));
            return Backend.call("updateProfilePhoto", Backend.data("photo", photo));
        }).addOnCompleteListener(activity, task -> {
            busy = false;
            if (task.isSuccessful()) AvatarView.invalidatePhoto(owner);
            else Toast.makeText(activity, R.string.photo_error, Toast.LENGTH_LONG).show();
            render();
        });
    });
    }
    void show(LinearLayout container) {
        root = container;
        if (!Backend.ready() || Backend.uid().isEmpty()) return;
        if (Backend.uid().equals(owner)) { render(); return; }
        owner = Backend.uid(); about = "";
        email = Backend.auth().getCurrentUser().getEmail(); name = Backend.auth().getCurrentUser().getDisplayName();
        if (email == null) email = ""; if (name == null) name = "";
        render();
        String requested = owner;
        Backend.db().collection("users").document(owner).get().addOnSuccessListener(activity, doc -> {
            if (!requested.equals(owner) || !requested.equals(Backend.uid())) return;
            if (doc.getString("name") != null) name = doc.getString("name"); render();
        });
        Backend.db().collection("profileAbout").document(owner).get().addOnSuccessListener(activity, doc -> {
            if (!requested.equals(owner) || !requested.equals(Backend.uid())) return;
            about = doc.getString("text") == null ? "" : doc.getString("text"); render();
        });
    }
    private void render() {
        if (activity.isFinishing() || root == null || !owner.equals(Backend.uid())) return;
        root.removeAllViews();
        ScrollView scroll = new ScrollView(activity); LinearLayout content = Ui.column(activity); content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(0, 0, 0, Ui.dp(activity, 12));
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout identity = SettingsPage.panel(activity); identity.setGravity(Gravity.CENTER_HORIZONTAL); content.addView(identity);
        AvatarView avatar = new AvatarView(activity); avatar.load(owner, name); avatar.setContentDescription(activity.getString(R.string.profile_photo));
        avatar.setOnClickListener(v -> photoActions()); avatar.setFocusable(true); avatar.setEnabled(!busy); avatar.setAlpha(busy ? .6f : 1f);
        FrameLayout photo = new FrameLayout(activity);
        photo.addView(avatar, new FrameLayout.LayoutParams(Ui.dp(activity, 112), Ui.dp(activity, 112), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        ImageButton editPhoto = new ImageButton(activity); editPhoto.setImageResource(R.drawable.ic_edit);
        editPhoto.setContentDescription(activity.getString(R.string.choose_photo)); editPhoto.setEnabled(!busy); editPhoto.setAlpha(busy ? .6f : 1f);
        editPhoto.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity)));
        editPhoto.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 16), Ui.dp(activity, 16));
        editPhoto.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Appearance.ripple(activity)), null,
                Ui.shape(activity, R.color.card, 24)));
        editPhoto.setOnClickListener(v -> photoActions());
        photo.addView(editPhoto, new FrameLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48), Gravity.BOTTOM | Gravity.END));
        identity.addView(photo, new LinearLayout.LayoutParams(Ui.dp(activity, 120), Ui.dp(activity, 120)));
        if (busy) {
            TextView saving = Ui.text(activity, activity.getString(R.string.photo_saving), 13, false); saving.setPadding(0, Ui.dp(activity, 12), 0, 0); identity.addView(saving);
        }
        TextView displayName = Ui.text(activity, name, 24, true); displayName.setMaxLines(2); displayName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        displayName.setGravity(Gravity.CENTER); displayName.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 24), Ui.dp(activity, 8), Ui.dp(activity, 8));
        android.graphics.drawable.Drawable editIcon = activity.getDrawable(R.drawable.ic_edit).mutate(); editIcon.setTint(Appearance.primary(activity)); editIcon.setBounds(0, 0, Ui.dp(activity, 18), Ui.dp(activity, 18));
        displayName.setCompoundDrawables(null, null, editIcon, null); displayName.setCompoundDrawablePadding(Ui.dp(activity, 8));
        displayName.setContentDescription(activity.getString(R.string.edit_name) + ": " + name); displayName.setOnClickListener(v -> editName()); displayName.setFocusable(true); identity.addView(displayName, new LinearLayout.LayoutParams(-2, -2));
        TextView mail = Ui.text(activity, email, 15, false); mail.setGravity(Gravity.CENTER); mail.setSingleLine(true); mail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        identity.addView(mail, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout aboutRow = Ui.row(activity); aboutRow.setGravity(Gravity.TOP); aboutRow.setBaselineAligned(false);
        aboutRow.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 8), 0, Ui.dp(activity, 12));
        LinearLayout text = Ui.column(activity);
        TextView aboutTitle = Ui.text(activity, activity.getString(R.string.profile_about), 13, false); aboutTitle.setPadding(0, 0, 0, Ui.dp(activity, 6)); text.addView(aboutTitle);
        TextView phrase = Ui.text(activity, about.isEmpty() ? activity.getString(R.string.about_empty) : about, 16, false);
        phrase.setTextColor(activity.getColor(about.isEmpty() ? R.color.text_secondary : R.color.text_primary)); text.addView(phrase);
        aboutRow.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView pencil = new ImageView(activity); pencil.setImageResource(R.drawable.ic_edit); pencil.setImageTintList(android.content.res.ColorStateList.valueOf(Appearance.primary(activity)));
        pencil.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10), Ui.dp(activity, 10), Ui.dp(activity, 14)); pencil.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        aboutRow.addView(pencil, new LinearLayout.LayoutParams(Ui.dp(activity, 48), Ui.dp(activity, 48)));
        aboutRow.setOnClickListener(v -> editAbout()); aboutRow.setFocusable(true); aboutRow.setContentDescription(activity.getString(R.string.edit_about) + ": " + (about.isEmpty() ? activity.getString(R.string.about_empty) : about));
        aboutRow.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Appearance.ripple(activity)), null, new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE)));
        LinearLayout aboutCard = SettingsPage.panel(activity); aboutCard.addView(aboutRow, new LinearLayout.LayoutParams(-1, -2)); content.addView(aboutCard);
    }
    private void editAbout() {
        EditText field = Ui.input(activity, R.string.about_hint); field.setSingleLine(false); field.setMaxLines(3); field.setText(about);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(139)});
        LinearLayout form = Ui.column(activity); form.setPadding(Ui.dp(activity, 20), 0, Ui.dp(activity, 20), 0); form.addView(field);
        TextView count = Ui.text(activity, activity.getString(R.string.about_count, about.length()), 12, false); count.setGravity(Gravity.END); form.addView(count);
        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int length, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int length) { count.setText(activity.getString(R.string.about_count, s.length())); }
            public void afterTextChanged(android.text.Editable value) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.edit_about).setView(form).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = field.getText().toString().trim().replaceAll("\\s+", " "); view.setEnabled(false);
            Backend.call("updateProfileAbout", Backend.data("text", value)).addOnCompleteListener(activity, task -> {
                view.setEnabled(true); if (task.isSuccessful()) { about = (String) task.getResult().get("text"); dialog.dismiss(); render(); }
                else Toast.makeText(activity, Backend.error(activity, task.getException()), Toast.LENGTH_LONG).show();
            });
        })); dialog.show();
    }
    private void photoActions() {
        if (busy) return;
        new AlertDialog.Builder(activity).setTitle(R.string.profile_photo).setItems(new String[]{activity.getString(R.string.choose_photo), activity.getString(R.string.remove_photo)}, (dialog, which) -> {
            if (which == 0) picker.launch("image/*");
            else new AlertDialog.Builder(activity).setMessage(R.string.remove_photo_question).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.remove_photo, (confirmation, button) -> {
                busy = true; render(); Backend.call("updateProfilePhoto", Backend.data("photo", null)).addOnCompleteListener(activity, task -> {
                    busy = false; if (task.isSuccessful()) AvatarView.invalidatePhoto(owner); else Toast.makeText(activity, R.string.photo_error, Toast.LENGTH_LONG).show(); render();
                });
            }).show();
        }).setNegativeButton(R.string.cancel, null).show();
    }
    private void editName() {
        EditText field = Ui.input(activity, R.string.name_hint); field.setText(name);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(R.string.edit_name).setView(field).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String value = field.getText().toString().trim(); if (value.isEmpty() || value.length() > 60) { field.setError(activity.getString(R.string.invalid_name)); return; }
            view.setEnabled(false); Backend.call("registerProfile", Backend.data("name", value)).addOnCompleteListener(activity, task -> {
                view.setEnabled(true); if (task.isSuccessful()) { name = value; ContactsRepository.clear(); dialog.dismiss(); render(); }
                else Toast.makeText(activity, Backend.error(activity, task.getException()), Toast.LENGTH_LONG).show();
            });
        })); dialog.show();
    }
}
