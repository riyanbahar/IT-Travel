package com.config;

import com.apps.storefinder.R;
import com.libraries.utilities.MGUtilities;
import com.models.Consent;


public class UIConfig {

	public static int SLIDER_PLACEHOLDER = R.mipmap.bg_image_placeholder;

    public final static int IMAGE_PLACEHOLDER_PROFILE_THUMB = R.mipmap.bg_image_thumb_placeholder;

    public final static int IMAGE_PLACEHOLDER = R.mipmap.bg_image_placeholder;

    public static int THEME_BLACK_COLOR = R.color.theme_main_color;

    public static int BORDER_WIDTH = R.dimen.border_store_list;

    public final static Consent[] CONSENT_SCREENS = {
            new Consent(
                    "AGE_16",
                    R.string.age_consent_title,
                    R.string.age_consent_category,
                    R.string.age_consent_what,
                    R.string.age_consent_why_needed,
                    R.string.age_consent_more_information,
                    "https://gdpr-info.eu/art-8-gdpr/"),

            new Consent(
                    "BASIC_APP",
                    R.string.basic_app_consent_title,
                    R.string.basic_app_consent_category,
                    R.string.basic_app_consent_what,
                    R.string.basic_app_consent_why_needed,
                    R.string.basic_app_consent_more_information,
                    "http://example.com/gdpr"),

            new Consent(
                    "ADS",
                    R.string.ads_consent_title,
                    R.string.ads_consent_category,
                    R.string.ads_consent_what,
                    R.string.ads_consent_why_needed,
                    R.string.ads_consent_more_information,
                    "https://firebase.google.com/support/privacy")
    };

    public final static String[] GDPR_COUNTRY = {
            "Austria",
            "Belgium",
            "Bulgaria",
            "Croatia",
            "Republic of Cyprus",
            "Czech Republic",
            "Denmark",
            "Estonia",
            "Finland",
            "France",
            "Germany",
            "Greece",
            "Hungary",
            "Ireland",
            "Italy",
            "Latvia",
            "Lithuania",
            "Luxembourg",
            "Malta",
            "Netherlands",
            "Poland",
            "Portugal",
            "Romania",
            "Slovakia",
            "Slovenia",
            "Spain",
            "Sweden",
            "United Kingdom"
    };
}
