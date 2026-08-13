package de.lunara.app.ui.main

/** Fail closed when an Activity-scoped ViewModel is rebound after logout/account switching. */
internal object SessionUiIsolation {
    fun canExpose(renderedUserId: String?, stateOwnerId: String?): Boolean =
        renderedUserId != null && renderedUserId == stateOwnerId
}
