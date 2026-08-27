//// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    // Declared here for consistency with the other three. `app` applied hilt.android without
    // a root declaration, which resolved but was inconsistent -- see AUDIT_ADDENDUM.md A.4.
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
