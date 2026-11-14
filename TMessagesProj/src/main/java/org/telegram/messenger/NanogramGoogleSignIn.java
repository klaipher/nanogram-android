package org.telegram.messenger;

import android.app.Activity;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;

/**
 * "Sign in with Google" for e-mail login verification through Credential Manager. play-services-auth 22 removed the
 * GoogleSignIn API upstream still calls; the login screens only need the account e-mail and the ID token.
 */
public final class NanogramGoogleSignIn {

    public static final class Account {
        public final String email;
        public final String idToken;

        Account(String email, String idToken) {
            this.email = email;
            this.idToken = idToken;
        }

        public String getEmail() {
            return email;
        }

        public String getIdToken() {
            return idToken;
        }
    }

    public interface Callback {
        void onSignedIn(@NonNull Account account);
    }

    private NanogramGoogleSignIn() {
    }

    public static void signIn(Activity activity, String serverClientId, Callback callback) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        GetSignInWithGoogleOption option = new GetSignInWithGoogleOption.Builder(serverClientId).build();
        GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(option).build();
        CredentialManager.create(activity).getCredentialAsync(activity, request, new CancellationSignal(), activity.getMainExecutor(),
                new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                    @Override
                    public void onResult(GetCredentialResponse response) {
                        Credential credential = response.getCredential();
                        if (credential instanceof CustomCredential && GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(credential.getType())) {
                            try {
                                GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(credential.getData());
                                callback.onSignedIn(new Account(google.getId(), google.getIdToken()));
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        } else {
                            FileLog.e("NanogramGoogleSignIn: unexpected credential type " + credential.getType());
                        }
                    }

                    @Override
                    public void onError(@NonNull GetCredentialException e) {
                        FileLog.e(e);
                    }
                });
    }
}
