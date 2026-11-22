package br.com.falai;

import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;
import androidx.credentials.exceptions.NoCredentialException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.GoogleAuthProvider;

final class GoogleLogin {
    static final class NoGoogleAccountException extends Exception { }
    static Task<AuthResult> signIn(AppCompatActivity activity, CancellationSignal cancellation) {
        TaskCompletionSource<String> source = new TaskCompletionSource<>();
        GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                .setServerClientId(activity.getString(R.string.default_web_client_id))
                .setFilterByAuthorizedAccounts(false).setAutoSelectEnabled(false).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        requestCredential(activity, cancellation, request, source, true);
        return source.getTask().onSuccessTask(token -> Backend.auth().signInWithCredential(GoogleAuthProvider.getCredential(token, null)));
    }
    private static void requestCredential(AppCompatActivity activity, CancellationSignal cancellation,
            GetCredentialRequest request, TaskCompletionSource<String> source, boolean allowFallback) {
        CredentialManager.create(activity).getCredentialAsync(activity, request, cancellation, ContextCompat.getMainExecutor(activity),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override public void onResult(@NonNull GetCredentialResponse response) {
                        try {
                            Credential credential = response.getCredential();
                            if (!(credential instanceof CustomCredential) || !GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) throw new IllegalArgumentException("credential-type");
                            source.trySetResult(GoogleIdTokenCredential.createFrom(credential.getData()).getIdToken());
                        } catch (Exception error) { source.trySetException(error); }
                    }
                    @Override public void onError(@NonNull GetCredentialException error) {
                        if (error instanceof NoCredentialException && allowFallback && !cancellation.isCanceled()) {
                            GetCredentialRequest fallback = new GetCredentialRequest.Builder().addCredentialOption(
                                    new GetSignInWithGoogleOption.Builder(activity.getString(R.string.default_web_client_id)).build()).build();
                            requestCredential(activity, cancellation, fallback, source, false);
                        } else if (error instanceof NoCredentialException) source.trySetException(new NoGoogleAccountException());
                        else source.trySetException(error);
                    }
                });
    }
    static void clear(AppCompatActivity activity) {
        CredentialManager.create(activity).clearCredentialStateAsync(new ClearCredentialStateRequest(), null,
                ContextCompat.getMainExecutor(activity), new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { }
                    @Override public void onError(@NonNull ClearCredentialException error) { }
                });
    }
    private GoogleLogin() { }
}
