package org.wordpress.android.util;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.EditText;
import android.widget.TextView;

import org.wordpress.android.widgets.TypefaceCache;

import org.wordpress.android.R;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Design guidelines for Calypso-styled Site Settings (and likely other screens)
 */

public class WPPrefUtils {

    /**
     * Length of a {@link String} (representing a language code) when there is no region included.
     * For example: "en" contains no region, "en_US" contains a region (US)
     *
     * Used to parse a language code {@link String} when creating a {@link Locale}.
     */
    private static final int NO_REGION_LANG_CODE_LEN = 2;

    /**
     * Index of a language code {@link String} where the region code begins. The language code
     * format is cc_rr, where cc is the country code (e.g. en, es, az) and rr is the region code
     * (e.g. us, au, gb).
     */
    private static final int REGION_SUBSTRING_INDEX = 3;

    /**
     * Gets a preference and sets the {@link android.preference.Preference.OnPreferenceChangeListener}.
     */
    public static Preference getPrefAndSetClickListener(PreferenceFragment prefFrag,
                                                         int id,
                                                         Preference.OnPreferenceClickListener listener) {
        Preference pref = prefFrag.findPreference(prefFrag.getString(id));
        if (pref != null) pref.setOnPreferenceClickListener(listener);
        return pref;
    }

    /**
     * Gets a preference and sets the {@link android.preference.Preference.OnPreferenceChangeListener}.
     */
    public static Preference getPrefAndSetChangeListener(PreferenceFragment prefFrag,
                                                         int id,
                                                         Preference.OnPreferenceChangeListener listener) {
        Preference pref = prefFrag.findPreference(prefFrag.getString(id));
        if (pref != null) pref.setOnPreferenceChangeListener(listener);
        return pref;
    }

    /**
     * Removes a {@link Preference} from the {@link PreferenceCategory} with the given key.
     */
    public static void removePreference(PreferenceFragment prefFrag, int parentKey, int prefKey) {
        String parentName = prefFrag.getString(parentKey);
        String prefName = prefFrag.getString(prefKey);
        PreferenceGroup parent = (PreferenceGroup) prefFrag.findPreference(parentName);
        Preference child = prefFrag.findPreference(prefName);

        if (parent != null && child != null) {
            parent.removePreference(child);
        }
    }

    /**
     * Font      : Open Sans
     * Style     : Normal
     * Variation : Normal
     */
    public static Typeface getNormalTypeface(Context context) {
        return TypefaceCache.getTypeface(context,
                TypefaceCache.FAMILY_OPEN_SANS, Typeface.NORMAL, TypefaceCache.VARIATION_NORMAL);
    }

    /**
     * Font      : Open Sans
     * Style     : Bold
     * Variation : Light
     */
    public static Typeface getSemiboldTypeface(Context context) {
        return TypefaceCache.getTypeface(context,
                TypefaceCache.FAMILY_OPEN_SANS, Typeface.BOLD, TypefaceCache.VARIATION_LIGHT);
    }

    /**
     * Styles a {@link TextView} to display a large title against a dark background.
     */
    public static void layoutAsLightTitle(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_extra_large);
        setTextViewAttributes(view, size, R.color.white, getSemiboldTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display a large title against a light background.
     */
    public static void layoutAsDarkTitle(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_extra_large);
        setTextViewAttributes(view, size, R.color.grey_dark, getSemiboldTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display medium sized text as a header with sub-elements.
     */
    public static void layoutAsSubhead(TextView view) {
        int color = view.isEnabled() ? R.color.grey_dark : R.color.grey_lighten_10;
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_large);
        setTextViewAttributes(view, size, color, getNormalTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display smaller text.
     */
    public static void layoutAsBody1(TextView view) {
        int color = view.isEnabled() ? R.color.grey_darken_10 : R.color.grey_lighten_10;
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_medium);
        setTextViewAttributes(view, size, color, getNormalTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display smaller text with a dark grey color.
     */
    public static void layoutAsBody2(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_medium);
        setTextViewAttributes(view, size, R.color.grey_darken_10, getSemiboldTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display very small helper text.
     */
    public static void layoutAsCaption(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_small);
        setTextViewAttributes(view, size, R.color.grey_darken_10, getNormalTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display text in a button.
     */
    public static void layoutAsFlatButton(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_medium);
        setTextViewAttributes(view, size, R.color.blue_medium, getSemiboldTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display text in a button.
     */
    public static void layoutAsRaisedButton(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_medium);
        setTextViewAttributes(view, size, R.color.white, getSemiboldTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display text in an editable text field.
     */
    public static void layoutAsInput(EditText view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_large);
        setTextViewAttributes(view, size, R.color.grey_dark, getNormalTypeface(view.getContext()));
        view.setHintTextColor(view.getResources().getColor(R.color.grey_lighten_10));
        view.setTextColor(view.getResources().getColor(R.color.grey_dark));
    }

    /**
     * Styles a {@link TextView} to display selected numbers in a {@link android.widget.NumberPicker}.
     */
    public static void layoutAsNumberPickerSelected(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_triple_extra_large);
        setTextViewAttributes(view, size, R.color.blue_medium, getSemiboldTypeface(view.getContext()));
    }

    /**
     * Styles a {@link TextView} to display non-selected numbers in a {@link android.widget.NumberPicker}.
     */
    public static void layoutAsNumberPickerPeek(TextView view) {
        int size = view.getResources().getDimensionPixelSize(R.dimen.text_sz_large);
        setTextViewAttributes(view, size, R.color.grey_dark, getNormalTypeface(view.getContext()));
    }

    public static void setTextViewAttributes(TextView textView, int size, int colorRes, Typeface typeface) {
        textView.setTypeface(typeface);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        textView.setTextColor(textView.getResources().getColor(colorRes));
    }

    /**
     * Gets a locale for the given language code.
     */
    public static Locale languageLocale(String languageCode) {
        if (TextUtils.isEmpty(languageCode)) return Locale.getDefault();

        if (languageCode.length() > NO_REGION_LANG_CODE_LEN) {
            return new Locale(languageCode.substring(0, NO_REGION_LANG_CODE_LEN),
                    languageCode.substring(REGION_SUBSTRING_INDEX));
        }

        return new Locale(languageCode);
    }

    /**
     * Creates a map from language codes to WordPress language IDs.
     */
    public static Map<String, String> generateLanguageMap(Activity activity) {
        String[] languageIds = activity.getResources().getStringArray(R.array.lang_ids);
        String[] languageCodes = activity.getResources().getStringArray(R.array.language_codes);

        Map<String, String> languageMap = new HashMap<>();
        for (int i = 0; i < languageIds.length && i < languageCodes.length; ++i) {
            languageMap.put(languageCodes[i], languageIds[i]);
        }

        return languageMap;
    }
}
