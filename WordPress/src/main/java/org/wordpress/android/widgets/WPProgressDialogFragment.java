package org.wordpress.android.widgets;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;

import org.wordpress.android.util.StringUtils;

/**
 * Simple progress dialog fragment.
 */
public class WPProgressDialogFragment extends DialogFragment {

    private static final String ARG_MESSAGE = "message";

    public static WPProgressDialogFragment newInstance(String message) {
        Bundle args = new Bundle();
        args.putString(ARG_MESSAGE, message);

        WPProgressDialogFragment fragment = new WPProgressDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle bundle = getArguments();

        String message = StringUtils.notNullStr(bundle.getString(ARG_MESSAGE));

        ProgressDialog dialog = new ProgressDialog(getActivity(), getTheme());
        dialog.setMessage(message);
        dialog.setIndeterminate(true);
        dialog.setCancelable(false);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        return dialog;
    }
}
