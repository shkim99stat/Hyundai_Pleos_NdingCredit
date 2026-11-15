package com.example.pleos_endingcredit

/** Figma screen IDs for navigation. savedName keeps Activity state readable across updates. */
internal enum class Page(val screenId: String, val savedName: String) {
    A0_CONSENT("A0", "CONSENT"),
    A1_PAYOUT_SETUP("A1", "PAYOUT_SETUP"),
    A2_HOME("A2", "HOME"),
    A3_START("A3", "START"),
    A4_RECORDING("A4", "RECORDING"),
    A5_DRIVING_PREVIEW("A5", "DRIVING_PREVIEW"),
    LEGACY_RESULT_CHOICE("result-choice", "CHOICE"),
    B1_CREDIT_VIDEO("B1", "CREDIT_VIDEO"),
    B2_PREPARING("B2", "PREPARING"),
    B3_CREDIT("B3", "CREDIT"),
    C1_RESULT("C1", "RESULT"),
    F1_CATALOG("F1", "ARCHIVE"),
    F4_CATALOG_DETAIL("F4", "CATALOG_DETAIL"),
    F3_YEAR_REVIEW("F3", "YEAR_REVIEW"),
    ;

    companion object {
        fun fromSavedName(value: String?): Page? = entries.firstOrNull { it.savedName == value }
    }
}

/** Panels without their own Figma number use the parent screen ID and a descriptive suffix. */
internal enum class Sheet(val screenId: String, val savedName: String) {
    CANCEL_TRIP("A4.cancel", "CANCEL_TRIP"),
    END_TRIP("A4-3", "END_TRIP"),
    COSTS("C1.costs", "COSTS"),
    D1_1_COSTS("D1-1", "CATALOG_COSTS"),
    CARD_PREVIEW("C1.card-preview", "CARD_PREVIEW"),
    SHARE_PREVIEW("trip-share", "SHARE_PREVIEW"),
    RECORDED_STOPS("recorded-stops", "RECORDED_STOPS"),
    A2_CREW("A2.crew", "TRAVEL_CREW"),
    A3_TRIP_START("A3.popup", "TRIP_START"),
    C1_EDIT_RESULT_PLACES("C1-2", "EDIT_RESULT_PLACES"),
    DELETE_CATALOG_TRIP("F4.delete", "DELETE_CATALOG_TRIP"),
    ;

    companion object {
        fun fromSavedName(value: String?): Sheet? = entries.firstOrNull { it.savedName == value }
    }
}
